/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.kotlin.internal

import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.ext.asBsonObjectId
import io.realm.kotlin.internal.dynamic.DynamicUnmanagedRealmObject
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.interop.CollectionType
import io.realm.kotlin.internal.interop.PropertyKey
import io.realm.kotlin.internal.interop.PropertyType
import io.realm.kotlin.internal.interop.RealmCoreException
import io.realm.kotlin.internal.interop.RealmCoreLogicException
import io.realm.kotlin.internal.interop.RealmCorePropertyNotNullableException
import io.realm.kotlin.internal.interop.RealmCorePropertyTypeMismatchException
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmListPointer
import io.realm.kotlin.internal.interop.RealmSetPointer
import io.realm.kotlin.internal.interop.RealmValueT
import io.realm.kotlin.internal.interop.RealmValueTransport
import io.realm.kotlin.internal.interop.Timestamp
import io.realm.kotlin.internal.interop.UUIDWrapper
import io.realm.kotlin.internal.interop.scoped
import io.realm.kotlin.internal.interop.scopedTracked
import io.realm.kotlin.internal.interop.unscoped
import io.realm.kotlin.internal.platform.realmObjectCompanionOrThrow
import io.realm.kotlin.internal.schema.ClassMetadata
import io.realm.kotlin.internal.schema.PropertyMetadata
import io.realm.kotlin.internal.schema.RealmStorageTypeImpl
import io.realm.kotlin.internal.schema.realmStorageType
import io.realm.kotlin.internal.util.Validation.sdkError
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import io.realm.kotlin.types.TypedRealmObject
import org.mongodb.kbson.BsonObjectId
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1

/**
 * This object holds helper methods for the compiler plugin generated methods, providing the
 * convenience of writing manually code instead of adding it through the compiler plugin.
 *
 * Inlining would anyway yield the same result as generating it.
 */
@Suppress("LargeClass")
internal object RealmObjectHelper {

    // ---------------------------------------------------------------------
    // Objects
    // ---------------------------------------------------------------------

    @Suppress("unused") // Called from generated code
    internal inline fun setObject(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String,
        value: BaseRealmObject?,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: ObjectCache = mutableMapOf()
    ) {
        obj.checkValid()
        val key = obj.propertyInfoOrThrow(propertyName).key
        setObjectByKey(obj, key, value, updatePolicy, cache)
    }

    internal inline fun setObjectByKey(
        obj: RealmObjectReference<out BaseRealmObject>,
        key: PropertyKey,
        value: BaseRealmObject?,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: ObjectCache = mutableMapOf()
    ) {
        val objRef: RealmObjectReference<out BaseRealmObject>? = value?.let {
            val realmObjectReference = value.realmObjectReference
            // If managed ...
            if (realmObjectReference != null) {
                // and from the same version we just use object as is
                if (realmObjectReference.owner == obj.owner) {
                    value
                } else {
                    throw IllegalArgumentException(
                        """Cannot import an outdated object. Use findLatest(object) to find an
                            |up-to-date version of the object in the given context before importing
                            |it.
                        """.trimMargin()
                    )
                }
            } else {
                // otherwise we will import it
                copyToRealm(
                    obj.mediator,
                    obj.owner.asValidLiveRealmReference(),
                    value,
                    updatePolicy,
                    cache = cache
                )
            }.realmObjectReference
        }
        setValueByKey(obj, key, objRef)
    }

    // Return type should be R? but causes compilation errors for native
    @Suppress("unused")
    internal inline fun <reified R : BaseRealmObject, U> getObject(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String,
    ): Any? {
        obj.checkValid()
        val key: PropertyKey = obj.propertyInfoOrThrow(propertyName).key
        unscoped {
            return RealmInterop.realm_get_value_transport(it, obj.objectPointer, key)
                ?.getLink()
                ?.toRealmObject(R::class, obj.mediator, obj.owner)
        }
    }

    @Suppress("unused") // Called from generated code
    internal inline fun setEmbeddedRealmObject(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String,
        value: BaseRealmObject?,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: ObjectCache = mutableMapOf()
    ) {
        obj.checkValid()
        val key = obj.propertyInfoOrThrow(propertyName).key
        setEmbeddedRealmObjectByKey(obj, key, value, updatePolicy, cache)
    }

    internal inline fun setEmbeddedRealmObjectByKey(
        obj: RealmObjectReference<out BaseRealmObject>,
        key: PropertyKey,
        value: BaseRealmObject?,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: ObjectCache = mutableMapOf()
    ) {
        if (value != null) {
            val embedded = RealmInterop.realm_set_embedded(obj.objectPointer, key)
            val newObj = embedded.toRealmObject(value::class, obj.mediator, obj.owner)
            assign(newObj, value, updatePolicy, cache)
        } else {
            setValueByKey(obj, key, null)
        }
    }

    // ---------------------------------------------------------------------
    // Primitives
    // ---------------------------------------------------------------------

    internal inline fun setValue(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String,
        value: Any?
    ) {
        obj.checkValid()
        val key = obj.propertyInfoOrThrow(propertyName).key

        // TODO OPTIMIZE We are currently only doing this check for typed access so could consider
        //  moving the guard into the compiler plugin. Await the implementation of a user
        //  facing general purpose dynamic realm (not only for migration) before doing this, as
        //  this would also require the guard ... or maybe await proper core support for throwing
        //  when this is not supported.
        obj.metadata.let { classMetaData ->
            val primaryKeyPropertyKey: PropertyKey? = classMetaData.primaryKeyProperty?.key
            if (primaryKeyPropertyKey != null && key == primaryKeyPropertyKey) {
                val name = classMetaData[primaryKeyPropertyKey]!!.name
                throw IllegalArgumentException("Cannot update primary key property '${obj.className}.$name'")
            }
        }

        return setValueByKey(obj, key, value)
    }

    @Suppress("ComplexMethod")
    internal inline fun setValueByKey(
        obj: RealmObjectReference<out BaseRealmObject>,
        key: PropertyKey,
        value: Any?
    ) = scoped {
        val transport = when (value) {
            null -> create()
            is Long -> create(value)
            is Boolean -> create(value)
            is String -> create(value)
            is ByteArray -> create(value)
            is Timestamp -> create(value)
            is Float -> create(value)
            is Double -> create(value)
            is BsonObjectId -> create(value)
            is ObjectId -> create(value.asBsonObjectId())
            is UUIDWrapper -> create(value)
            is RealmObjectReference<out BaseRealmObject> -> create(
                RealmInterop.realm_object_as_link(value.objectPointer)
            )
            is MutableRealmInt -> create(value.get())
            else -> throw IllegalArgumentException("Unsupported value for transport: $value")
        }
        setValueTransportByKey(obj, key, transport)
    }

    internal inline fun getString(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): String? = unscoped { realmValueToString(getValue(obj, propertyName, it)) }

    internal inline fun getLong(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): Long? = unscoped { realmValueToLong(getValue(obj, propertyName, it)) }

    internal inline fun getBoolean(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): Boolean? = scoped { realmValueToBoolean(getValue(obj, propertyName, alloc())) }

    internal inline fun getFloat(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): Float? = unscoped { realmValueToFloat(getValue(obj, propertyName, it)) }

    internal inline fun getDouble(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): Double? = scoped { realmValueToDouble(getValue(obj, propertyName, alloc())) }

    internal inline fun getInstant(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): RealmInstant? = unscoped { realmValueToRealmInstant(getValue(obj, propertyName, it)) }

    internal inline fun getObjectId(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): BsonObjectId? = unscoped { realmValueToObjectId(getValue(obj, propertyName, it)) }

    internal inline fun getUUID(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): RealmUUID? = unscoped { realmValueToRealmUUID(getValue(obj, propertyName, it)) }

    internal inline fun getByteArray(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): ByteArray? = unscoped { realmValueToByteArray(getValue(obj, propertyName, it)) }

    internal inline fun getValue(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String,
        struct: RealmValueT
    ): RealmValueTransport? = RealmInterop.realm_get_value_transport(
        struct,
        obj.objectPointer,
        obj.propertyInfoOrThrow(propertyName).key
    )

// ---------------------------------------------------------------------
// End new implementation
// ---------------------------------------------------------------------

    const val NOT_IN_A_TRANSACTION_MSG =
        "Changing Realm data can only be done on a live object from inside a write transaction. Frozen objects can be turned into live using the 'MutableRealm.findLatest(obj)' API."

// Issues (not yet fully uncovered/filed) met when calling these or similar methods from
// generated code
// - Generic return type should be R but causes compilation errors for native
//  e: java.lang.IllegalStateException: Not found Idx for public io.realm.kotlin.internal/RealmObjectHelper|null[0]/
// - Passing KProperty1<T,R> with inlined reified type parameters to enable fetching type and
//   property names directly from T/property triggers runtime crash for primitive properties on
//   Kotlin native. Seems to be an issue with boxing/unboxing

    // Note: this data type is not using the converter/compiler plugin accessor default path
// It feels appropriate not to integrate it now as we might change the path to the C-API once
// we benchmark the current implementation against specific paths per data type.
    internal inline fun getMutableInt(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): ManagedMutableRealmInt? {
        val converter = converter<Long>(Long::class, obj.mediator, obj.owner)
        val propertyKey = obj.propertyInfoOrThrow(propertyName).key

        // In order to be able to use Kotlin's nullability handling baked into the accessor we need
        // to ask Core for the current value and return null if the value itself is null, returning
        // an instance of the wrapper otherwise - not optimal but feels quite idiomatic.
        return unscoped {
            val currentValue = RealmInterop.realm_get_value_transport(
                it,
                obj.objectPointer,
                propertyKey
            )
            when (currentValue) {
                null -> null
                else -> ManagedMutableRealmInt(obj, propertyKey, converter)
            }
        }
    }

    // Return type should be RealmList<R?> but causes compilation errors for native
    @Suppress("unused") // Called from generated code
    internal inline fun <reified R : Any> getList(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): ManagedRealmList<Any?> {
        val elementType = R::class
        val realmObjectCompanion = elementType.realmObjectCompanionOrNull()
        val operatorType = if (realmObjectCompanion == null) {
            CollectionOperatorType.PRIMITIVE
        } else if (!realmObjectCompanion.io_realm_kotlin_isEmbedded) {
            CollectionOperatorType.REALM_OBJECT
        } else {
            CollectionOperatorType.EMBEDDED_OBJECT
        }
        val key = obj.propertyInfoOrThrow(propertyName).key
        return getListByKey(obj, key, elementType, operatorType)
    }

    @Suppress("unused") // Called from generated code
    internal fun <R : TypedRealmObject> getLinkingObjects(
        obj: RealmObjectReference<out BaseRealmObject>,
        sourceClassKey: ClassKey,
        sourcePropertyKey: PropertyKey,
        sourceClass: KClass<R>
    ): RealmResultsImpl<R> {
// <<<<<<< HEAD
//        val objects =
//            RealmInterop.realm_get_backlinks(obj.objectPointer, sourceClassKey, sourcePropertyKey)
//        return RealmResultsImpl(
//            obj.owner,
//            objects,
//            sourceClassKey,
//            obj.type,
//            obj.mediator
//        ) as RealmResultsImpl<R>
// =======
        val objects = RealmInterop.realm_get_backlinks(obj.objectPointer, sourceClassKey, sourcePropertyKey)
        return RealmResultsImpl(obj.owner, objects, sourceClassKey, sourceClass, obj.mediator) as RealmResultsImpl<R>
// >>>>>>> master
    }

    // Cannot call managedRealmList directly from an inline function
    internal fun <R> getListByKey(
        obj: RealmObjectReference<out BaseRealmObject>,
        key: PropertyKey,
        elementType: KClass<*>,
        operatorType: CollectionOperatorType
    ): ManagedRealmList<R> {
        val listPtr = RealmInterop.realm_get_list(obj.objectPointer, key)
        val operator =
            createListOperator<R>(listPtr, elementType, obj.mediator, obj.owner, operatorType)
        return ManagedRealmList(listPtr, operator)
    }

    @Suppress("LongParameterList")
    private fun <R> createListOperator(
        listPtr: RealmListPointer,
        clazz: KClass<*>,
        mediator: Mediator,
        realm: RealmReference,
        operatorType: CollectionOperatorType
    ): ListOperator<R> {
        val converter: RealmValueConverter<R> =
            converter<Any>(clazz, mediator, realm) as CompositeConverter<R, *>
        return when (operatorType) {
            CollectionOperatorType.PRIMITIVE ->
                PrimitiveListOperator(mediator, realm, converter, listPtr)
            CollectionOperatorType.REALM_OBJECT ->
                RealmObjectListOperator(mediator, realm, converter, listPtr, clazz)
            CollectionOperatorType.EMBEDDED_OBJECT -> EmbeddedRealmObjectListOperator(
                mediator,
                realm,
                converter as RealmValueConverter<EmbeddedRealmObject>,
                listPtr,
                clazz
            ) as ListOperator<R>
        }
    }

    internal inline fun <reified R : Any> getSet(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): ManagedRealmSet<Any?> {
        val elementType = R::class
        val realmObjectCompanion = elementType.realmObjectCompanionOrNull()
        val operatorType = if (realmObjectCompanion == null) {
            CollectionOperatorType.PRIMITIVE
        } else {
            CollectionOperatorType.REALM_OBJECT
        }
        val key = obj.propertyInfoOrThrow(propertyName).key
        return getSetByKey(obj, key, elementType, operatorType)
    }

    // Cannot call managedRealmList directly from an inline function
    internal fun <R> getSetByKey(
        obj: RealmObjectReference<out BaseRealmObject>,
        key: PropertyKey,
        elementType: KClass<*>,
        operatorType: CollectionOperatorType
    ): ManagedRealmSet<R> {
        val setPtr = RealmInterop.realm_get_set(obj.objectPointer, key)
        val operator =
            createSetOperator<R>(setPtr, elementType, obj.mediator, obj.owner, operatorType)
        return ManagedRealmSet(setPtr, operator)
    }

    @Suppress("LongParameterList")
    private fun <R> createSetOperator(
        setPtr: RealmSetPointer,
        clazz: KClass<*>,
        mediator: Mediator,
        realm: RealmReference,
        operatorType: CollectionOperatorType
    ): SetOperator<R> {
        val converter: RealmValueConverter<R> =
            converter<Any>(clazz, mediator, realm) as CompositeConverter<R, *>
        return when (operatorType) {
            CollectionOperatorType.PRIMITIVE ->
                PrimitiveSetOperator(mediator, realm, converter, setPtr)
            CollectionOperatorType.REALM_OBJECT ->
                RealmObjectSetOperator(mediator, realm, converter, setPtr, clazz)
            else ->
                throw IllegalArgumentException("Unsupported collection type: ${operatorType.name}")
        }
    }

    internal fun setValueTransportByKey(
        obj: RealmObjectReference<out BaseRealmObject>,
        key: PropertyKey,
        transport: RealmValueTransport,
    ) {
        try {
            // TODO Consider making a RealmValue cinterop type and move the various to_realm_value
            //  implementations in the various platform RealmInterops here to eliminate
            //  RealmObjectInterop and make cinterop operate on primitive values and native pointers
            //  only. This relates to the overall concern of having a generic path for getter/setter
            //  instead of generating a typed path for each type.
            RealmInterop.realm_set_value_transport(obj.objectPointer, key, transport, false)
            // The catch block should catch specific Core exceptions and rethrow them as Kotlin exceptions.
            // Core exceptions meaning might differ depending on the context, by rethrowing we can add some context related
            // info that might help users to understand the exception.
        } catch (exception: Throwable) {
            throw CoreExceptionConverter.convertToPublicException(exception) { coreException: RealmCoreException ->
                when (coreException) {
                    is RealmCorePropertyNotNullableException ->
                        IllegalArgumentException("Required property `${obj.className}.${obj.metadata[key]!!.name}` cannot be null")
                    is RealmCorePropertyTypeMismatchException ->
                        IllegalArgumentException("Property `${obj.className}.${obj.metadata[key]!!.name}` cannot be assigned with value '${transport.value}' of wrong type")
                    is RealmCoreLogicException -> IllegalArgumentException(
                        "Property `${obj.className}.${obj.metadata[key]!!.name}` cannot be assigned with value '${transport.value}'",
                        exception
                    )
                    else -> IllegalStateException(
                        "Cannot set `${obj.className}.$${obj.metadata[key]!!.name}` to `${transport.value}`: $NOT_IN_A_TRANSACTION_MSG",
                        exception
                    )
                }
            }
        }
    }

    @Suppress("unused") // Called from generated code
    internal inline fun <reified T : Any> setList(
        obj: RealmObjectReference<out BaseRealmObject>,
        col: String,
        list: RealmList<Any?>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: ObjectCache = mutableMapOf()
    ) {
        val existingList = getList<T>(obj, col)
        if (list !is ManagedRealmList || !RealmInterop.realm_equals(
                existingList.nativePointer,
                list.nativePointer
            )
        ) {
            existingList.also {
                it.clear()
                it.operator.insertAll(it.size, list, updatePolicy, cache)
            }
        }
    }

    internal inline fun <reified T : Any> setSet(
        obj: RealmObjectReference<out BaseRealmObject>,
        col: String,
        set: RealmSet<Any?>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: ObjectCache = mutableMapOf()
    ) {
        val existingSet = getSet<T>(obj, col)
        if (set !is ManagedRealmSet || !RealmInterop.realm_equals(
                existingSet.nativePointer,
                set.nativePointer
            )
        ) {
            existingSet.also {
                it.clear()
                it.operator.addAll(set, updatePolicy, cache)
            }
        }
    }

    @Suppress("LongParameterList")
    internal fun assign(
        target: BaseRealmObject,
        source: BaseRealmObject,
        updatePolicy: UpdatePolicy,
        cache: ObjectCache
    ) {
        if (target is DynamicRealmObject) {
            assignDynamic(target as DynamicMutableRealmObject, source, updatePolicy, cache)
        } else {
            assignTyped(target, source, updatePolicy, cache)
        }
    }

    @Suppress("LongParameterList", "NestedBlockDepth", "LongMethod")
    internal fun assignTyped(
        target: BaseRealmObject,
        source: BaseRealmObject,
        updatePolicy: UpdatePolicy,
        cache: ObjectCache
    ) {
        val metadata: ClassMetadata = target.realmObjectReference!!.metadata
        // TODO OPTIMIZE We could set all properties at once with one C-API call
        metadata.properties.filter {
            // Primary keys are set at construction time
            // Computed properties have no assignment
            !it.isComputed && !it.isPrimaryKey
        }.forEach { property ->
            val accessor = property.accessor
                ?: sdkError("Typed object should always have an accessor")

            accessor as KMutableProperty1<BaseRealmObject, Any?>
            when (property.collectionType) {
                CollectionType.RLM_COLLECTION_TYPE_NONE -> when (property.type) {
                    PropertyType.RLM_PROPERTY_TYPE_OBJECT -> {
                        val isTargetEmbedded =
                            target.realmObjectReference!!.owner.schemaMetadata.getOrThrow(property.linkTarget).isEmbeddedRealmObject
                        if (isTargetEmbedded) {
                            setEmbeddedRealmObjectByKey(
                                target.realmObjectReference!!,
                                property.key,
                                accessor.get(source) as EmbeddedRealmObject?,
                                updatePolicy,
                                cache
                            )
                        } else {
                            setObjectByKey(
                                target.realmObjectReference!!,
                                property.key,
                                accessor.get(source) as RealmObject?,
                                updatePolicy,
                                cache
                            )
                        }
                    }
                    else -> {
                        val getterValue = accessor.get(source)
                        accessor.set(target, getterValue)
                    }
                }
                CollectionType.RLM_COLLECTION_TYPE_LIST -> {
                    // We cannot use setList as that requires the type, so we need to retrieve the
                    // existing list, wipe it and insert new elements
                    @Suppress("UNCHECKED_CAST")
                    (accessor.get(target) as ManagedRealmList<Any?>)
                        .run {
                            clear()
                            val elements = accessor.get(source) as RealmList<*>
                            operator.insertAll(size, elements, updatePolicy, cache)
                        }
                }
                CollectionType.RLM_COLLECTION_TYPE_SET -> {
                    // We cannot use setSet as that requires the type, so we need to retrieve the
                    // existing set, wipe it and insert new elements
                    @Suppress("UNCHECKED_CAST")
                    (accessor.get(target) as ManagedRealmSet<Any?>).run {
                        clear()
                        val elements = accessor.get(source) as RealmSet<*>
                        operator.addAll(elements, updatePolicy, cache)
                    }
                }
                else -> TODO("Collection type ${property.collectionType} is not supported")
            }
        }
    }

    @Suppress("LongParameterList")
    internal fun assignDynamic(
        target: DynamicMutableRealmObject,
        source: BaseRealmObject,
        updatePolicy: UpdatePolicy,
        cache: ObjectCache
    ) {
        val properties: List<Pair<String, Any?>> = if (source is DynamicRealmObject) {
            if (source is DynamicUnmanagedRealmObject) {
                source.properties.toList()
            } else {
                // We should never reach here. If the object is dynamic and managed we reuse the
                // managed object. Even for embedded object we should not reach here as the parent
                // would also already be managed and we would just have reused that instead of
                // reimporting it
                sdkError("Unexpected import of dynamic managed object")
            }
        } else {
            val companion = realmObjectCompanionOrThrow(source::class)

            @Suppress("UNCHECKED_CAST")
            val members =
                companion.`io_realm_kotlin_fields` as Map<String, KMutableProperty1<BaseRealmObject, Any?>>
            members.map { it.key to it.value.get(source) }
        }
        properties.map {
            RealmObjectHelper.dynamicSetValue(
                target.realmObjectReference!!,
                it.first,
                it.second,
                updatePolicy,
                cache
            )
        }
    }

    /**
     * Get values for non-collection properties by name.
     *
     * This will verify that the requested type (`clazz`) and nullability matches the property
     * properties in the schema.
     */
    internal fun <R : Any> dynamicGet(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String,
        clazz: KClass<R>,
        nullable: Boolean
    ): R? {
        obj.checkValid()
        val propertyInfo = checkPropertyType(
            obj,
            propertyName,
            CollectionType.RLM_COLLECTION_TYPE_NONE,
            clazz,
            nullable
        )
        return unscoped {
//            val realmValue = getValueByKey(obj, propertyInfo.key)

            val realmValue = RealmInterop.realm_get_value_transport(
                it,
                obj.objectPointer,
                propertyInfo.key
            )

            // Consider moving this dynamic conversion to Converters.kt
            val value = when (clazz) {
                DynamicRealmObject::class,
                DynamicMutableRealmObject::class -> realmValueToRealmObject(
                    realmValue,
                    clazz as KClass<out BaseRealmObject>,
                    obj.mediator,
                    obj.owner
                )
                else -> primitiveTypeConverters.getValue(clazz)
                    .realmValueToPublic(realmValue)
            }
            value?.let {
                @Suppress("UNCHECKED_CAST")
                if (clazz.isInstance(value)) {
                    value as R?
                } else {
                    throw ClassCastException("Retrieving value of type '${clazz.simpleName}' but was of type '${value::class.simpleName}'")
                }
            }
        }
    }

    internal fun <R : Any> dynamicGetList(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String,
        clazz: KClass<R>,
        nullable: Boolean
    ): RealmList<R?> {
        obj.checkValid()
        val propertyMetadata = checkPropertyType(
            obj,
            propertyName,
            CollectionType.RLM_COLLECTION_TYPE_LIST,
            clazz,
            nullable
        )
        val operatorType = if (propertyMetadata.type != PropertyType.RLM_PROPERTY_TYPE_OBJECT) {
            CollectionOperatorType.PRIMITIVE
        } else if (!obj.owner.schemaMetadata[propertyMetadata.linkTarget]!!.isEmbeddedRealmObject) {
            CollectionOperatorType.REALM_OBJECT
        } else {
            CollectionOperatorType.EMBEDDED_OBJECT
        }
        @Suppress("UNCHECKED_CAST")
        return getListByKey<R>(obj, propertyMetadata.key, clazz, operatorType) as RealmList<R?>
    }

    internal fun <R : Any> dynamicGetSet(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String,
        clazz: KClass<R>,
        nullable: Boolean
    ): RealmSet<R?> {
        obj.checkValid()
        val propertyMetadata = checkPropertyType(
            obj,
            propertyName,
            CollectionType.RLM_COLLECTION_TYPE_SET,
            clazz,
            nullable
        )
        val operatorType = if (propertyMetadata.type != PropertyType.RLM_PROPERTY_TYPE_OBJECT) {
            CollectionOperatorType.PRIMITIVE
        } else if (!obj.owner.schemaMetadata[propertyMetadata.linkTarget]!!.isEmbeddedRealmObject) {
            CollectionOperatorType.REALM_OBJECT
        } else {
            throw IllegalStateException("RealmSets do not support Embedded Objects.")
        }
        @Suppress("UNCHECKED_CAST")
        return getSetByKey<R>(obj, propertyMetadata.key, clazz, operatorType) as RealmSet<R?>
    }

    @Suppress("LongMethod", "ComplexMethod")
    internal fun <R> dynamicSetValue(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String,
        value: R,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: ObjectCache = mutableMapOf()
    ) {
        obj.checkValid()

        val propertyMetadata = checkPropertyType(obj, propertyName, value)
        val clazz =
            RealmStorageTypeImpl.fromCorePropertyType(propertyMetadata.type).kClass.let { it ->
                if (it == BaseRealmObject::class) DynamicMutableRealmObject::class else value?.let { it::class }
                    ?: it
            }
        when (propertyMetadata.collectionType) {
            CollectionType.RLM_COLLECTION_TYPE_NONE -> when (propertyMetadata.type) {
                PropertyType.RLM_PROPERTY_TYPE_OBJECT -> {
                    if (obj.owner.schemaMetadata[propertyMetadata.linkTarget]!!.isEmbeddedRealmObject) {
                        setEmbeddedRealmObjectByKey(
                            obj,
                            propertyMetadata.key,
                            value as BaseRealmObject?,
                            updatePolicy,
                            cache
                        )
                    } else {
                        setObjectByKey(
                            obj,
                            propertyMetadata.key,
                            value as BaseRealmObject?,
                            updatePolicy,
                            cache
                        )
                    }
                }
                else -> {
                    scoped {
                        @Suppress("UNCHECKED_CAST")
                        val realmValue = primitiveTypeConverters.getValue(clazz)
                            .let { converter -> converter as RealmValueConverter<Any> }
                            .publicToRealmValue(this, value)
                        setValueTransportByKey(obj, propertyMetadata.key, realmValue)
                    }
                }
            }
            CollectionType.RLM_COLLECTION_TYPE_LIST -> {
                // We cannot use setList as that requires the type, so we need to retrieve the
                // existing list, wipe it and insert new elements
                @Suppress("UNCHECKED_CAST")
                dynamicGetList(obj, propertyName, clazz, propertyMetadata.isNullable)
                    .let { it as ManagedRealmList<Any?> }
                    .run {
                        clear()
                        operator.insertAll(
                            size,
                            value as RealmList<*>,
                            updatePolicy,
                            cache
                        )
                    }
            }
            CollectionType.RLM_COLLECTION_TYPE_SET -> {
                // Similar to lists, we would require the type to call setSet
                @Suppress("UNCHECKED_CAST")
                dynamicGetSet(obj, propertyName, clazz, propertyMetadata.isNullable)
                    .let { it as ManagedRealmSet<Any?> }
                    .run {
                        clear()
                        operator.addAll(value as RealmSet<*>, updatePolicy, cache)
                    }
            }
            CollectionType.RLM_COLLECTION_TYPE_DICTIONARY -> TODO("Dictionaries not supported yet.")
            else -> IllegalStateException("Unknown type: ${propertyMetadata.collectionType}")
        }
    }

    private fun checkPropertyType(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String,
        collectionType: CollectionType,
        elementType: KClass<*>,
        nullable: Boolean
    ): PropertyMetadata {
        val realElementType = elementType.realmStorageType()
        return obj.metadata.getOrThrow(propertyName).also { propertyInfo ->
            val kClass = RealmStorageTypeImpl.fromCorePropertyType(propertyInfo.type).kClass
            if (collectionType != propertyInfo.collectionType ||
                realElementType != kClass ||
                nullable != propertyInfo.isNullable
            ) {
                val expected = formatType(collectionType, realElementType, nullable)
                val actual =
                    formatType(propertyInfo.collectionType, kClass, propertyInfo.isNullable)
                throw IllegalArgumentException("Trying to access property '${obj.className}.$propertyName' as type: '$expected' but actual schema type is '$actual'")
            }
        }
    }

    @Suppress("ComplexMethod")
    private fun checkPropertyType(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String,
        value: Any?
    ): PropertyMetadata {
        return obj.metadata.getOrThrow(propertyName).also { propertyInfo ->
            val collectionType = when (value) {
                is RealmList<*> -> CollectionType.RLM_COLLECTION_TYPE_LIST
                is RealmSet<*> -> CollectionType.RLM_COLLECTION_TYPE_SET
                else -> CollectionType.RLM_COLLECTION_TYPE_NONE
            }
            val realmStorageType = RealmStorageTypeImpl.fromCorePropertyType(propertyInfo.type)
            val kClass = realmStorageType.kClass
            @Suppress("ComplexCondition")
            if (collectionType != propertyInfo.collectionType ||
                // We cannot retrieve the element type info from a list, so will have to rely on lower levels to error out if the types doesn't match
                collectionType == CollectionType.RLM_COLLECTION_TYPE_NONE && (
                    (value == null && !propertyInfo.isNullable) ||
                        (
                            value != null && (
                                (
                                    realmStorageType == RealmStorageType.OBJECT && value !is BaseRealmObject
                                    ) ||
                                    (realmStorageType != RealmStorageType.OBJECT && value!!::class.realmStorageType() != kClass)
                                )
                            )
                    )
            ) {
                val actual =
                    formatType(propertyInfo.collectionType, kClass, propertyInfo.isNullable)
                val received = formatType(
                    collectionType,
                    value?.let { it::class } ?: Nothing::class,
                    value == null
                )
                throw IllegalArgumentException(
                    "Property '${obj.className}.$propertyName' of type '$actual' cannot be assigned with value '$value' of type '$received'"
                )
            }
        }
    }

    private fun formatType(
        collectionType: CollectionType,
        elementType: KClass<*>,
        nullable: Boolean
    ): String {
        val elementTypeString = elementType.toString() + if (nullable) "?" else ""
        return when (collectionType) {
            CollectionType.RLM_COLLECTION_TYPE_NONE -> elementTypeString
            CollectionType.RLM_COLLECTION_TYPE_LIST -> "RealmList<$elementTypeString>"
            CollectionType.RLM_COLLECTION_TYPE_SET -> "RealmSet<$elementTypeString>"
            else -> TODO("Unsupported collection type: $collectionType")
        }
    }

    fun dynamicGetLinkingObjects(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): RealmResults<out DynamicRealmObject> {
        obj.metadata.getOrThrow(propertyName).let { sourcePropertyMetadata ->
            if (sourcePropertyMetadata.type != PropertyType.RLM_PROPERTY_TYPE_LINKING_OBJECTS) {
                val realmStorageType =
                    RealmStorageTypeImpl.fromCorePropertyType(sourcePropertyMetadata.type)
                val kClass = realmStorageType.kClass
                val actual = formatType(
                    sourcePropertyMetadata.collectionType,
                    kClass,
                    sourcePropertyMetadata.isNullable
                )
                throw IllegalArgumentException("Trying to access property '$propertyName' as an object reference but schema type is '$actual'")
            }

            obj.owner.schemaMetadata.getOrThrow(sourcePropertyMetadata.linkTarget)
                .let { targetClassMetadata ->
                    val targetPropertyMetadata =
                        targetClassMetadata.getOrThrow(sourcePropertyMetadata.linkOriginPropertyName)

                    val objects = RealmInterop.realm_get_backlinks(
                        obj.objectPointer,
                        targetClassMetadata.classKey,
                        targetPropertyMetadata.key
                    )
                    return RealmResultsImpl(
                        obj.owner,
                        objects,
                        targetClassMetadata.classKey,
                        DynamicRealmObject::class,
                        obj.mediator
                    )
                }
        }
    }
}
