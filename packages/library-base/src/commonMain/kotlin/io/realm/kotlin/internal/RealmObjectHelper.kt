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
@file:Suppress("NOTHING_TO_INLINE")

package io.realm.kotlin.internal

import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.VersionId
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.isValid
import io.realm.kotlin.ext.toRealmDictionary
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.ext.toRealmSet
import io.realm.kotlin.internal.dynamic.DynamicUnmanagedRealmObject
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.interop.CollectionType
import io.realm.kotlin.internal.interop.MemAllocator
import io.realm.kotlin.internal.interop.ObjectKey
import io.realm.kotlin.internal.interop.PropertyKey
import io.realm.kotlin.internal.interop.PropertyType
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmInterop.realm_get_value
import io.realm.kotlin.internal.interop.RealmListPointer
import io.realm.kotlin.internal.interop.RealmMapPointer
import io.realm.kotlin.internal.interop.RealmObjectInterop
import io.realm.kotlin.internal.interop.RealmSetPointer
import io.realm.kotlin.internal.interop.RealmValue
import io.realm.kotlin.internal.interop.Timestamp
import io.realm.kotlin.internal.interop.getterScope
import io.realm.kotlin.internal.interop.inputScope
import io.realm.kotlin.internal.platform.identityHashCode
import io.realm.kotlin.internal.platform.realmObjectCompanionOrThrow
import io.realm.kotlin.internal.schema.ClassMetadata
import io.realm.kotlin.internal.schema.PropertyMetadata
import io.realm.kotlin.internal.schema.RealmStorageTypeImpl
import io.realm.kotlin.internal.schema.realmStorageType
import io.realm.kotlin.internal.util.Validation.sdkError
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.schema.RealmClassKind
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import io.realm.kotlin.types.TypedRealmObject
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1

/**
 * This object holds helper methods for the compiler plugin generated methods, providing the
 * convenience of writing manually code instead of adding it through the compiler plugin.
 *
 * Inlining would anyway yield the same result as generating it.
 */
@Suppress("LargeClass", "UNCHECKED_CAST")
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
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
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
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ) {
        obj.checkValid()
        val objRef =
            realmObjectToRealmReferenceWithImport(value, obj.mediator, obj.owner, updatePolicy, cache)
        inputScope { setValueTransportByKey(obj, key, realmObjectTransport(objRef)) }
    }

    // Return type should be R? but causes compilation errors for native
    @Suppress("unused")
    internal inline fun <reified R : BaseRealmObject, U> getObject(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String,
    ): Any? {
        obj.checkValid()
        val key: PropertyKey = obj.propertyInfoOrThrow(propertyName).key
        return getterScope {
            val transport = realm_get_value(obj.objectPointer, key)
            when {
                transport.isNull() -> null
                else -> realm_get_value(obj.objectPointer, key)
                    .getLink()
                    .toRealmObject(R::class, obj.mediator, obj.owner)
            }
        }
    }

    @Suppress("unused") // Called from generated code
    internal inline fun setEmbeddedRealmObject(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String,
        value: BaseRealmObject?,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
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
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
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

    @Suppress("ComplexMethod", "LongMethod")
    internal inline fun setValueByKey(
        obj: RealmObjectReference<out BaseRealmObject>,
        key: PropertyKey,
        value: Any?,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ) {
        // TODO optimize: avoid this by creating the scope in the accessor via the compiler plugin
        //  See comment in AccessorModifierIrGeneration.modifyAccessor about this.
        inputScope {
            when (value) {
                null -> setValueTransportByKey(obj, key, nullTransport())
                is String -> setValueTransportByKey(obj, key, stringTransport(value))
                is ByteArray -> setValueTransportByKey(obj, key, byteArrayTransport(value))
                is Long -> setValueTransportByKey(obj, key, longTransport(value))
                is Boolean -> setValueTransportByKey(obj, key, booleanTransport(value))
                is Timestamp -> setValueTransportByKey(obj, key, timestampTransport(value))
                is Float -> setValueTransportByKey(obj, key, floatTransport(value))
                is Double -> setValueTransportByKey(obj, key, doubleTransport(value))
                is Decimal128 -> setValueTransportByKey(obj, key, decimal128Transport(value))
                is BsonObjectId -> setValueTransportByKey(
                    obj,
                    key,
                    objectIdTransport(value.toByteArray())
                )
                is RealmUUID -> setValueTransportByKey(obj, key, uuidTransport(value.bytes))
                is RealmObjectInterop -> setValueTransportByKey(
                    obj,
                    key,
                    realmObjectTransport(value)
                )
                is MutableRealmInt -> setValueTransportByKey(obj, key, longTransport(value.get()))
                is RealmAny -> {
                    realmAnyHandler(
                        value = value,
                        primitiveValueAsRealmValueHandler = { realmValue ->
                            setValueTransportByKey(
                                obj,
                                key,
                                realmValue
                            )
                        },
                        referenceAsRealmAnyHandler = { realmValue ->
                            setObjectByKey(obj, key, realmValue.asRealmObject(), updatePolicy, cache)
                        },
                        listAsRealmAnyHandler = { realmValue ->
                            val nativePointer = RealmInterop.realm_set_list(obj.objectPointer, key)
                            RealmInterop.realm_list_clear(nativePointer)
                            val operator =
                                realmAnyListOperator(obj.mediator, obj.owner, nativePointer, false, false)
                            operator.insertAll(0, value.asList(), updatePolicy, cache)
                        },
                        dictionaryAsRealmAnyHandler = { realmValue ->
                            val nativePointer = RealmInterop.realm_set_dictionary(obj.objectPointer, key)
                            RealmInterop.realm_dictionary_clear(nativePointer)
                            val operator =
                                realmAnyMapOperator(obj.mediator, obj.owner, nativePointer, false, false)
                            operator.putAll(value.asDictionary(), updatePolicy, cache)
                        }
                    )
                }
                else -> throw IllegalArgumentException("Unsupported value for transport: $value")
            }
        }
    }

    // TODO optimize: avoid this many get functions by creating the scope in the accessor via the
    //  compiler plugin. See comment in AccessorModifierIrGeneration.modifyAccessor about this.

    internal inline fun getString(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): String? = getterScope { getRealmValue(obj, propertyName)?.let { realmValueToString(it) } }

    internal inline fun getLong(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): Long? = getterScope { getRealmValue(obj, propertyName)?.let { realmValueToLong(it) } }

    internal inline fun getBoolean(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): Boolean? = getterScope { getRealmValue(obj, propertyName)?.let { realmValueToBoolean(it) } }

    internal inline fun getFloat(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): Float? = getterScope { getRealmValue(obj, propertyName)?.let { realmValueToFloat(it) } }

    internal inline fun getDouble(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): Double? = getterScope { getRealmValue(obj, propertyName)?.let { realmValueToDouble(it) } }

    internal inline fun getDecimal128(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): Decimal128? = getterScope { getRealmValue(obj, propertyName)?.let { realmValueToDecimal128(it) } }

    internal inline fun getInstant(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): RealmInstant? =
        getterScope { getRealmValue(obj, propertyName)?.let { realmValueToRealmInstant(it) } }

    internal inline fun getObjectId(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): BsonObjectId? = getterScope { getRealmValue(obj, propertyName)?.let { realmValueToObjectId(it) } }

    internal inline fun getUUID(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): RealmUUID? = getterScope { getRealmValue(obj, propertyName)?.let { realmValueToRealmUUID(it) } }

    internal inline fun getByteArray(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): ByteArray? = getterScope { getRealmValue(obj, propertyName)?.let { realmValueToByteArray(it) } }

    internal inline fun getRealmAny(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): RealmAny? = getterScope {
        val key = obj.propertyInfoOrThrow(propertyName).key
        getRealmValueFromKey(obj, key)
            ?.let {
                realmValueToRealmAny(
                    it, obj, obj.mediator, obj.owner,
                    false,
                    false,
                    { RealmInterop.realm_get_list(obj.objectPointer, key) }
                ) { RealmInterop.realm_get_dictionary(obj.objectPointer, key) }
            }
    }

    internal inline fun MemAllocator.getRealmValue(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String,
    ): RealmValue? = getRealmValueFromKey(obj, obj.propertyInfoOrThrow(propertyName).key)

    internal inline fun MemAllocator.getRealmValueFromKey(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyKey: PropertyKey
    ): RealmValue? {
        val realmValue = realm_get_value(
            obj.objectPointer,
            propertyKey
        )
        return when (realmValue.isNull()) {
            true -> null
            false -> realmValue
        }
    }

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
        val converter = converter<Long>(Long::class)
        val propertyKey = obj.propertyInfoOrThrow(propertyName).key

        // In order to be able to use Kotlin's nullability handling baked into the accessor we need
        // to ask Core for the current value and return null if the value itself is null, returning
        // an instance of the wrapper otherwise - not optimal but feels quite idiomatic.
        return getterScope {
            val transport = realm_get_value(obj.objectPointer, propertyKey)
            when (transport.isNull()) {
                true -> null
                else -> ManagedMutableRealmInt(obj, propertyKey, converter)
            }
        }
    }

    // Return type should be RealmList<R?> but causes compilation errors for native
    @Suppress("unused") // Called from generated code
    internal inline fun <reified R : Any> getList(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): ManagedRealmList<R> {
        val elementType: KClass<R> = R::class
        val realmObjectCompanion = elementType.realmObjectCompanionOrNull()
        val operatorType = if (realmObjectCompanion == null) {
            if (elementType == RealmAny::class) {
                CollectionOperatorType.REALM_ANY
            } else {
                CollectionOperatorType.PRIMITIVE
            }
        } else if (realmObjectCompanion.io_realm_kotlin_classKind == RealmClassKind.EMBEDDED) {
            CollectionOperatorType.EMBEDDED_OBJECT
        } else {
            CollectionOperatorType.REALM_OBJECT
        }
        val propertyMetadata = obj.propertyInfoOrThrow(propertyName)
        return getListByKey(obj, propertyMetadata, elementType, operatorType)
    }

    @Suppress("unused") // Called from generated code
    internal fun <R : TypedRealmObject> getBacklinks(
        obj: RealmObjectReference<out BaseRealmObject>,
        sourceClassKey: ClassKey,
        sourcePropertyKey: PropertyKey,
        sourceClass: KClass<R>
    ): RealmResultsImpl<R> {
        val objects = RealmInterop.realm_get_backlinks(obj.objectPointer, sourceClassKey, sourcePropertyKey)
        return RealmResultsImpl(obj.owner, objects, sourceClassKey, sourceClass, obj.mediator)
    }

    @Suppress("LongParameterList")
    internal fun <R> getListByKey(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyMetadata: PropertyMetadata,
        elementType: KClass<R & Any>,
        operatorType: CollectionOperatorType,
        issueDynamicObject: Boolean = false,
        issueDynamicMutableObject: Boolean = false
    ): ManagedRealmList<R> {
        val listPtr = RealmInterop.realm_get_list(obj.objectPointer, propertyMetadata.key)
        val operator = createListOperator<R>(
            listPtr,
            elementType,
            propertyMetadata,
            obj.mediator,
            obj.owner,
            operatorType,
            issueDynamicObject,
            issueDynamicMutableObject
        )
        return ManagedRealmList(obj, listPtr, operator)
    }

    @Suppress("LongParameterList")
    private fun <R> createListOperator(
        listPtr: RealmListPointer,
        clazz: KClass<R & Any>,
        propertyMetadata: PropertyMetadata,
        mediator: Mediator,
        realm: RealmReference,
        operatorType: CollectionOperatorType,
        issueDynamicObject: Boolean,
        issueDynamicMutableObject: Boolean
    ): ListOperator<R> {
        return when (operatorType) {
            CollectionOperatorType.PRIMITIVE -> PrimitiveListOperator(
                mediator,
                realm,
                converter<R>(clazz) as CompositeConverter<R, *>,
                listPtr
            )
            CollectionOperatorType.REALM_ANY -> RealmAnyListOperator(
                mediator,
                realm,
                listPtr,
                issueDynamicObject = issueDynamicObject,
                issueDynamicMutableObject = issueDynamicMutableObject
            ) as ListOperator<R>
            CollectionOperatorType.REALM_OBJECT -> {
                val classKey: ClassKey = realm.schemaMetadata.getOrThrow(propertyMetadata.linkTarget).classKey
                RealmObjectListOperator(
                    mediator,
                    realm,
                    listPtr,
                    clazz as KClass<RealmObject>,
                    classKey,
                ) as ListOperator<R>
            }
            CollectionOperatorType.EMBEDDED_OBJECT -> {
                val classKey: ClassKey = realm.schemaMetadata.getOrThrow(propertyMetadata.linkTarget).classKey
                EmbeddedRealmObjectListOperator(
                    mediator,
                    realm,
                    listPtr,
                    clazz as KClass<EmbeddedRealmObject>,
                    classKey,
                ) as ListOperator<R>
            }
        }
    }

    internal inline fun <reified R : Any> getSet(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): ManagedRealmSet<R?> {
        val elementType = R::class
        val realmObjectCompanion = elementType.realmObjectCompanionOrNull()
        val operatorType = if (realmObjectCompanion == null) {
            if (elementType == RealmAny::class) {
                CollectionOperatorType.REALM_ANY
            } else {
                CollectionOperatorType.PRIMITIVE
            }
        } else {
            CollectionOperatorType.REALM_OBJECT
        }
        val propertyMetadata = obj.propertyInfoOrThrow(propertyName)
        return getSetByKey(obj, propertyMetadata, elementType, operatorType)
    }

    @Suppress("LongParameterList")
    internal fun <R> getSetByKey(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyMetadata: PropertyMetadata,
        elementType: KClass<R & Any>,
        operatorType: CollectionOperatorType,
        issueDynamicObject: Boolean = false,
        issueDynamicMutableObject: Boolean = false
    ): ManagedRealmSet<R> {
        val setPtr = RealmInterop.realm_get_set(obj.objectPointer, propertyMetadata.key)
        val operator = createSetOperator<R>(
            setPtr,
            elementType,
            propertyMetadata,
            obj.mediator,
            obj.owner,
            operatorType,
            issueDynamicObject,
            issueDynamicMutableObject,
        )
        return ManagedRealmSet(obj, setPtr, operator)
    }

    @Suppress("LongParameterList")
    private fun <R> createSetOperator(
        setPtr: RealmSetPointer,
        clazz: KClass<R & Any>,
        propertyMetadata: PropertyMetadata,
        mediator: Mediator,
        realm: RealmReference,
        operatorType: CollectionOperatorType,
        issueDynamicObject: Boolean,
        issueDynamicMutableObject: Boolean
    ): SetOperator<R> {
        return when (operatorType) {
            CollectionOperatorType.PRIMITIVE -> PrimitiveSetOperator(
                mediator,
                realm,
                converter(clazz),
                setPtr
            )
            CollectionOperatorType.REALM_ANY -> RealmAnySetOperator(
                mediator,
                realm,
                setPtr,
                issueDynamicObject,
                issueDynamicMutableObject
            ) as SetOperator<R>
            CollectionOperatorType.REALM_OBJECT -> {
                val classKey: ClassKey = realm.schemaMetadata.getOrThrow(propertyMetadata.linkTarget).classKey
                RealmObjectSetOperator(
                    mediator,
                    realm,
                    setPtr,
                    clazz as KClass<RealmObject>,
                    classKey
                ) as SetOperator<R>
            }
            else ->
                throw IllegalArgumentException("Unsupported collection type: ${operatorType.name}")
        }
    }

    internal inline fun <reified R : Any> getDictionary(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String
    ): ManagedRealmDictionary<R?> {
        val elementType = R::class
        val realmObjectCompanion = elementType.realmObjectCompanionOrNull()
        val propertyMetadata = obj.propertyInfoOrThrow(propertyName)
        val operatorType = if (realmObjectCompanion == null) {
            if (elementType == RealmAny::class) {
                CollectionOperatorType.REALM_ANY
            } else {
                CollectionOperatorType.PRIMITIVE
            }
        } else if (!obj.owner.schemaMetadata[propertyMetadata.linkTarget]!!.isEmbeddedRealmObject) {
            CollectionOperatorType.REALM_OBJECT
        } else {
            CollectionOperatorType.EMBEDDED_OBJECT
        }
        return getDictionaryByKey(obj, propertyMetadata, elementType, operatorType)
    }

    @Suppress("LongParameterList")
    internal fun <R> getDictionaryByKey(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyMetadata: PropertyMetadata,
        elementType: KClass<R & Any>,
        operatorType: CollectionOperatorType,
        issueDynamicObject: Boolean = false,
        issueDynamicMutableObject: Boolean = false
    ): ManagedRealmDictionary<R> {
        val dictionaryPtr =
            RealmInterop.realm_get_dictionary(obj.objectPointer, propertyMetadata.key)
        val operator = createDictionaryOperator<R>(
            dictionaryPtr,
            elementType,
            propertyMetadata,
            obj.mediator,
            obj.owner,
            operatorType,
            issueDynamicObject,
            issueDynamicMutableObject,
        )
        return ManagedRealmDictionary(obj, dictionaryPtr, operator)
    }

    @Suppress("LongParameterList", "UnusedPrivateMember")
    private fun <R> createDictionaryOperator(
        dictionaryPtr: RealmMapPointer,
        clazz: KClass<R & Any>,
        propertyMetadata: PropertyMetadata,
        mediator: Mediator,
        realm: RealmReference,
        operatorType: CollectionOperatorType,
        issueDynamicObject: Boolean = false, // TODO handle when adding support for dynamic realms
        issueDynamicMutableObject: Boolean = false // TODO handle when adding support for dynamic realms
    ): MapOperator<String, R> {
        return when (operatorType) {
            CollectionOperatorType.PRIMITIVE -> PrimitiveMapOperator(
                mediator,
                realm,
                converter(clazz),
                converter(String::class),
                dictionaryPtr
            )
            CollectionOperatorType.REALM_ANY -> RealmAnyMapOperator(
                mediator,
                realm,
                converter(String::class),
                dictionaryPtr,
                issueDynamicObject, issueDynamicMutableObject
            ) as MapOperator<String, R>
            CollectionOperatorType.REALM_OBJECT -> {
                val classKey = realm.schemaMetadata.getOrThrow(propertyMetadata.linkTarget).classKey
                RealmObjectMapOperator(
                    mediator,
                    realm,
                    converter(String::class),
                    dictionaryPtr,
                    clazz as KClass<RealmObject>,
                    classKey
                ) as MapOperator<String, R>
            }
            CollectionOperatorType.EMBEDDED_OBJECT -> {
                val classKey = realm.schemaMetadata.getOrThrow(propertyMetadata.linkTarget).classKey
                EmbeddedRealmObjectMapOperator(
                    mediator,
                    realm,
                    converter(String::class),
                    dictionaryPtr,
                    clazz as KClass<EmbeddedRealmObject>,
                    classKey
                ) as MapOperator<String, R>
            }
        }
    }

    internal fun setValueTransportByKey(
        obj: RealmObjectReference<out BaseRealmObject>,
        key: PropertyKey,
        transport: RealmValue,
    ) {
        // TODO Consider making a RealmValue cinterop type and move the various to_realm_value
        //  implementations in the various platform RealmInterops here to eliminate
        //  RealmObjectInterop and make cinterop operate on primitive values and native pointers
        //  only. This relates to the overall concern of having a generic path for getter/setter
        //  instead of generating a typed path for each type.
        RealmInterop.realm_set_value(obj.objectPointer, key, transport, false)
    }

    @Suppress("unused") // Called from generated code
    internal inline fun <reified T : Any> setList(
        obj: RealmObjectReference<out BaseRealmObject>,
        col: String,
        list: RealmList<T>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
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
        set: RealmSet<T>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
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

    internal inline fun <reified T : Any> setDictionary(
        obj: RealmObjectReference<out BaseRealmObject>,
        col: String,
        dictionary: RealmDictionary<T>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ) {
        val existingDictionary = getDictionary<T>(obj, col)
        if (dictionary !is ManagedRealmDictionary<T> || !RealmInterop.realm_equals(
                existingDictionary.nativePointer,
                dictionary.nativePointer
            )
        ) {
            existingDictionary.also {
                it.clear()
                it.operator.putAll(dictionary, updatePolicy, cache)
            }
        }
    }

    internal fun assign(
        target: BaseRealmObject,
        source: BaseRealmObject,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ) {
        if (target is DynamicRealmObject) {
            assignDynamic(target as DynamicMutableRealmObject, source, updatePolicy, cache)
        } else {
            assignTyped(target, source, updatePolicy, cache)
        }
    }

    @Suppress("NestedBlockDepth", "LongMethod", "ComplexMethod")
    internal fun assignTyped(
        target: BaseRealmObject,
        source: BaseRealmObject,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ) {
        val metadata: ClassMetadata = target.realmObjectReference!!.metadata
        // TODO OPTIMIZE We could set all properties at once with one C-API call
        metadata.properties.filter {
            // Primary keys are set at construction time
            // Computed properties have no assignment
            !it.isComputed && !it.isPrimaryKey
        }.forEach { property ->
            // For synced Realms in ADDITIVE mode, Object Store will return the full on-disk
            // schema, including fields not defined in the user schema. This makes it problematic
            // to iterate through the Realm schema and assume that all properties will have kotlin
            // properties associated with them. To avoid throwing errors we double check that
            val accessor: KProperty1<BaseRealmObject, Any?> = property.accessor
                ?: if (property.isUserDefined()) {
                    sdkError("Typed object should always have an accessor: ${metadata.className}.${property.name}")
                } else {
                    return@forEach // Property is only visible on disk, ignore.
                }
            accessor as KMutableProperty1<BaseRealmObject, Any?>
            when (property.collectionType) {
                CollectionType.RLM_COLLECTION_TYPE_NONE -> when (property.type) {
                    PropertyType.RLM_PROPERTY_TYPE_OBJECT -> {
                        val isTargetEmbedded =
                            target.realmObjectReference!!.owner.schemaMetadata.getOrThrow(property.linkTarget).isEmbeddedRealmObject
                        if (isTargetEmbedded) {
                            val value = accessor.get(source) as EmbeddedRealmObject?
                            setEmbeddedRealmObjectByKey(
                                target.realmObjectReference!!,
                                property.key,
                                value,
                                updatePolicy,
                                cache
                            )
                        } else {
                            val value = accessor.get(source) as RealmObject?
                            setObjectByKey(
                                target.realmObjectReference!!,
                                property.key,
                                value,
                                updatePolicy,
                                cache
                            )
                        }
                    }
                    PropertyType.RLM_PROPERTY_TYPE_MIXED -> {
                        val value = accessor.get(source)
                        setValueByKey(
                            target.realmObjectReference!!,
                            property.key,
                            value,
                            updatePolicy,
                            cache
                        )
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
                    (accessor.get(target) as ManagedRealmSet<Any?>)
                        .run {
                            clear()
                            val elements = accessor.get(source) as RealmSet<*>
                            operator.addAll(elements, updatePolicy, cache)
                        }
                }
                CollectionType.RLM_COLLECTION_TYPE_DICTIONARY -> {
                    // We cannot use setDictionary as that requires the type, so we need to retrieve
                    // the existing dictionary, wipe it and insert new elements
                    @Suppress("UNCHECKED_CAST")
                    (accessor.get(target) as ManagedRealmDictionary<Any?>)
                        .run {
                            clear()
                            val elements = accessor.get(source) as RealmDictionary<*>
                            operator.putAll(elements, updatePolicy, cache)
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
        cache: UnmanagedToManagedObjectCache
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
                companion.io_realm_kotlin_fields as Map<String, KMutableProperty1<BaseRealmObject, Any?>>
            members.map { it.key to it.value.get(source) }
        }
        properties.map {
            dynamicSetValue(
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
        nullable: Boolean,
        issueDynamicMutableObject: Boolean = false
    ): R? {
        obj.checkValid()
        val propertyInfo = checkPropertyType(
            obj,
            propertyName,
            CollectionType.RLM_COLLECTION_TYPE_NONE,
            clazz,
            nullable
        )
        return getterScope {
            val transport = realm_get_value(obj.objectPointer, propertyInfo.key)

            // Consider moving this dynamic conversion to Converters.kt
            val value = when (clazz) {
                DynamicRealmObject::class,
                DynamicMutableRealmObject::class -> realmValueToRealmObject(
                    transport,
                    clazz as KClass<out BaseRealmObject>,
                    obj.mediator,
                    obj.owner
                )
                RealmAny::class -> realmValueToRealmAny(
                    realmValue = transport,
                    parent = obj,
                    mediator = obj.mediator,
                    owner = obj.owner,
                    issueDynamicObject = true,
                    issueDynamicMutableObject = issueDynamicMutableObject,
                    getListFunction = { RealmInterop.realm_get_list(obj.objectPointer, propertyInfo.key) },
                ) { RealmInterop.realm_get_dictionary(obj.objectPointer, propertyInfo.key) }

                else -> with(primitiveTypeConverters.getValue(clazz)) {
                    realmValueToPublic(transport)
                }
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
        nullable: Boolean,
        issueDynamicMutableObject: Boolean = false
    ): RealmList<R?> {
        obj.checkValid()
        val propertyMetadata = checkPropertyType(
            obj,
            propertyName,
            CollectionType.RLM_COLLECTION_TYPE_LIST,
            clazz,
            nullable
        )
        val operatorType = when {
            propertyMetadata.type == PropertyType.RLM_PROPERTY_TYPE_MIXED ->
                CollectionOperatorType.REALM_ANY
            propertyMetadata.type != PropertyType.RLM_PROPERTY_TYPE_OBJECT ->
                CollectionOperatorType.PRIMITIVE
            !obj.owner.schemaMetadata[propertyMetadata.linkTarget]!!.isEmbeddedRealmObject ->
                CollectionOperatorType.REALM_OBJECT
            else -> CollectionOperatorType.EMBEDDED_OBJECT
        }
        @Suppress("UNCHECKED_CAST")
        return getListByKey(
            obj,
            propertyMetadata,
            clazz,
            operatorType,
            true,
            issueDynamicMutableObject
        ) as RealmList<R?>
    }

    internal fun <R : Any> dynamicGetSet(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String,
        clazz: KClass<R>,
        nullable: Boolean,
        issueDynamicMutableObject: Boolean = false
    ): RealmSet<R?> {
        obj.checkValid()
        val propertyMetadata = checkPropertyType(
            obj,
            propertyName,
            CollectionType.RLM_COLLECTION_TYPE_SET,
            clazz,
            nullable
        )
        val operatorType = when {
            propertyMetadata.type == PropertyType.RLM_PROPERTY_TYPE_MIXED ->
                CollectionOperatorType.REALM_ANY
            propertyMetadata.type != PropertyType.RLM_PROPERTY_TYPE_OBJECT ->
                CollectionOperatorType.PRIMITIVE
            !obj.owner.schemaMetadata[propertyMetadata.linkTarget]!!.isEmbeddedRealmObject ->
                CollectionOperatorType.REALM_OBJECT
            else -> throw IllegalStateException("RealmSets do not support Embedded Objects.")
        }
        @Suppress("UNCHECKED_CAST")
        return getSetByKey(
            obj,
            propertyMetadata,
            clazz,
            operatorType,
            true,
            issueDynamicMutableObject
        ) as RealmSet<R?>
    }

    internal fun <R : Any> dynamicGetDictionary(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String,
        clazz: KClass<R>,
        nullable: Boolean,
        issueDynamicMutableObject: Boolean = false
    ): RealmDictionary<R?> {
        obj.checkValid()
        val propertyMetadata = checkPropertyType(
            obj,
            propertyName,
            CollectionType.RLM_COLLECTION_TYPE_DICTIONARY,
            clazz,
            nullable
        )
        val operatorType = when {
            propertyMetadata.type == PropertyType.RLM_PROPERTY_TYPE_MIXED ->
                CollectionOperatorType.REALM_ANY
            propertyMetadata.type != PropertyType.RLM_PROPERTY_TYPE_OBJECT ->
                CollectionOperatorType.PRIMITIVE
            !obj.owner.schemaMetadata[propertyMetadata.linkTarget]!!.isEmbeddedRealmObject ->
                CollectionOperatorType.REALM_OBJECT
            else -> CollectionOperatorType.EMBEDDED_OBJECT
        }
        @Suppress("UNCHECKED_CAST")
        return getDictionaryByKey(
            obj,
            propertyMetadata,
            clazz,
            operatorType,
            true,
            issueDynamicMutableObject
        ) as RealmDictionary<R?>
    }

    @Suppress("LongMethod", "ComplexMethod", "NestedBlockDepth")
    internal fun <R> dynamicSetValue(
        obj: RealmObjectReference<out BaseRealmObject>,
        propertyName: String,
        value: R,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ) {
        obj.checkValid()

        val propertyMetadata = checkPropertyType(obj, propertyName, value)
        val clazz = RealmStorageTypeImpl.fromCorePropertyType(propertyMetadata.type)
            .kClass
            .let { clazz ->
                when (clazz) {
                    BaseRealmObject::class -> DynamicMutableRealmObject::class
                    RealmAny::class -> RealmAny::class
                    else -> value?.let { it::class } ?: clazz
                }
            }
        when (propertyMetadata.collectionType) {
            CollectionType.RLM_COLLECTION_TYPE_NONE -> {
                val key = propertyMetadata.key
                when (propertyMetadata.type) {
                    PropertyType.RLM_PROPERTY_TYPE_OBJECT -> {
                        if (obj.owner.schemaMetadata[propertyMetadata.linkTarget]!!.isEmbeddedRealmObject) {
                            setEmbeddedRealmObjectByKey(
                                obj,
                                key,
                                value as BaseRealmObject?,
                                updatePolicy,
                                cache
                            )
                        } else {
                            setObjectByKey(
                                obj,
                                key,
                                value as BaseRealmObject?,
                                updatePolicy,
                                cache
                            )
                        }
                    }
                    PropertyType.RLM_PROPERTY_TYPE_MIXED -> {
                        val realmAnyValue = value as RealmAny?
                        when (realmAnyValue?.type) {
                            RealmAny.Type.OBJECT -> {
                                val objValue = value?.let {
                                    val objectClass = ((it as RealmAnyImpl<*>).clazz) as KClass<out BaseRealmObject>
                                    if (objectClass == DynamicRealmObject::class || objectClass == DynamicMutableRealmObject::class) {
                                        value.asRealmObject<DynamicRealmObject>()
                                    } else {
                                        throw IllegalArgumentException("Dynamic RealmAny fields only support DynamicRealmObjects or DynamicMutableRealmObjects.")
                                    }
                                }
                                val managedObj = realmObjectWithImport(
                                    objValue,
                                    obj.mediator,
                                    obj.owner,
                                    updatePolicy,
                                    cache
                                )!!
                                setObjectByKey(
                                    obj,
                                    key,
                                    managedObj,
                                    updatePolicy,
                                    cache
                                )
                            }
                            else -> inputScope {
                                if (value == null) {
                                    setValueTransportByKey(obj, key, nullTransport())
                                } else {
                                    realmAnyHandler(
                                        value = value,
                                        primitiveValueAsRealmValueHandler = { realmValue -> setValueTransportByKey(obj, key, realmValue) },
                                        referenceAsRealmAnyHandler = { realmValue ->
                                            setObjectByKey(obj, key, realmValue.asRealmObject(), updatePolicy, cache)
                                        },
                                        listAsRealmAnyHandler = { realmValue ->
                                            val nativePointer = RealmInterop.realm_set_list(obj.objectPointer, key)
                                            val operator =
                                                realmAnyListOperator(obj.mediator, obj.owner, nativePointer, true)
                                            operator.insertAll(0, value.asList(), updatePolicy, cache)
                                        },
                                        dictionaryAsRealmAnyHandler = { realmValue ->
                                            val nativePointer = RealmInterop.realm_set_dictionary(obj.objectPointer, key)
                                            val operator =
                                                realmAnyMapOperator(obj.mediator, obj.owner, nativePointer, true)
                                            operator.putAll(value.asDictionary(), updatePolicy, cache)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        val converter = primitiveTypeConverters.getValue(clazz)
                            .let { converter -> converter as RealmValueConverter<Any> }
                        inputScope {
                            with(converter) {
                                val realmValue = publicToRealmValue(value)
                                setValueTransportByKey(obj, key, realmValue)
                            }
                        }
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
            CollectionType.RLM_COLLECTION_TYPE_DICTIONARY -> {
                // Similar to sets and lists, we would require the type to call setDictionary
                @Suppress("UNCHECKED_CAST")
                dynamicGetDictionary(obj, propertyName, clazz, propertyMetadata.isNullable)
                    .let { it as ManagedRealmDictionary<Any?> }
                    .run {
                        clear()
                        operator.putAll(value as RealmDictionary<*>, updatePolicy, cache)
                    }
            }
            else -> throw IllegalStateException("Unknown type: ${propertyMetadata.collectionType}")
        }
    }

    @Suppress("unused") // Called from generated code
    // Inlining this functions somehow break the IntelliJ debugger, unclear why?
    internal fun realmToString(obj: BaseRealmObject): String {
        // This code assumes no race conditions
        val schemaName = obj::class.realmObjectCompanionOrNull()?.io_realm_kotlin_className
        val fqName = obj::class.qualifiedName
        return obj.realmObjectReference?.let {
            if (obj.isValid()) {
                val id: RealmObjectIdentifier = obj.getIdentifier()
                val objKey = id.objectKey.key
                val version = id.versionId.version
                "$fqName{state=VALID, schemaName=$schemaName, objKey=$objKey, version=$version, realm=${it.owner.owner.configuration.name}}"
            } else {
                val state = if (it.owner.isClosed()) {
                    "CLOSED"
                } else {
                    "INVALID"
                }
                "$fqName{state=$state, schemaName=$schemaName, realm=${it.owner.owner.configuration.name}, hashCode=${obj.hashCode()}}"
            }
        } ?: "$fqName{state=UNMANAGED, schemaName=$schemaName, hashCode=${obj.hashCode()}}"
    }

    @Suppress("unused", "ReturnCount") // Called from generated code
    // Inlining this functions somehow break the IntelliJ debugger, unclear why?
    internal fun realmEquals(obj: BaseRealmObject, other: Any?): Boolean {
        if (obj === other) return true
        if (other == null || obj::class != other::class) return false

        other as BaseRealmObject

        if (other.isManaged()) {
            if (obj.isValid() != other.isValid()) return false
            return obj.getIdentifierOrNull() == other.getIdentifierOrNull()
        } else {
            // If one of the objects are unmanaged, they are only equal if identical, which
            // should have been caught at the top of this function.
            return false
        }
    }

    @Suppress("unused", "MagicNumber") // Called from generated code
    // Inlining this functions somehow break the IntelliJ debugger, unclear why?
    internal fun realmHashCode(obj: BaseRealmObject): Int {
        // This code assumes no race conditions
        return obj.realmObjectReference?.let {
            val isValid: Boolean = obj.isValid()
            val identifier: RealmObjectIdentifier = if (it.isClosed()) {
                RealmObjectIdentifier(ClassKey(-1), ObjectKey(-1), VersionId(0), "")
            } else {
                obj.getIdentifier()
            }
            val realmPath: String = it.owner.owner.configuration.path
            var hashCode = isValid.hashCode()
            hashCode = 31 * hashCode + identifier.hashCode()
            hashCode = 31 * hashCode + realmPath.hashCode()
            hashCode
        } ?: identityHashCode(obj)
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
                is RealmDictionary<*> -> CollectionType.RLM_COLLECTION_TYPE_DICTIONARY
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
                                    (realmStorageType != RealmStorageType.OBJECT && value::class.realmStorageType() != kClass)
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
            CollectionType.RLM_COLLECTION_TYPE_DICTIONARY -> "RealmDictionary<$elementTypeString>"
            else -> TODO("Unsupported collection type: $collectionType")
        }
    }

    @Suppress("LongParameterList", "NestedBlockDepth", "LongMethod", "ComplexMethod", "LoopWithTooManyJumpStatements")
    internal fun assignValuesOnUnmanagedObject(
        target: BaseRealmObject,
        source: BaseRealmObject,
        mediator: Mediator,
        currentDepth: UInt,
        maxDepth: UInt,
        closeAfterCopy: Boolean,
        cache: ManagedToUnmanagedObjectCache
    ) {
        val metadata: ClassMetadata = source.realmObjectReference!!.metadata
        for (property: PropertyMetadata in metadata.properties) {
            // For synced Realms in ADDITIVE mode, Object Store will return the full on-disk
            // schema, including fields not defined in the user schema. This makes it problematic
            // to iterate through the Realm schema and assume that all properties will have kotlin
            // properties associated with them. To avoid throwing errors we double check that
            val accessor: KProperty1<BaseRealmObject, Any?> = property.accessor
                ?: if (property.isUserDefined()) {
                    sdkError("Typed object should always have an accessor: ${metadata.className}.${property.name}")
                } else {
                    continue // Property is only visible on disk, ignore.
                }
            if (property.isComputed) {
                continue
            }
            accessor as KMutableProperty1<BaseRealmObject, Any?>
            when (property.collectionType) {
                CollectionType.RLM_COLLECTION_TYPE_NONE -> when (property.type) {
                    PropertyType.RLM_PROPERTY_TYPE_OBJECT -> {
                        if (currentDepth == maxDepth) {
                            accessor.set(target, null)
                        } else {
                            val realmObject: BaseRealmObject? = accessor.get(source) as BaseRealmObject?
                            if (realmObject != null) {
                                accessor.set(
                                    target,
                                    createDetachedCopy(
                                        mediator,
                                        realmObject,
                                        currentDepth + 1u,
                                        maxDepth,
                                        closeAfterCopy,
                                        cache
                                    )
                                )
                            }
                        }
                    }
                    PropertyType.RLM_PROPERTY_TYPE_INT -> {
                        // MutableRealmInt is a special case, since Core treats it as Int
                        // in the schema. So we need to test for our wrapper class here
                        val value = accessor.get(source)
                        if (value is MutableRealmInt) {
                            accessor.set(target, MutableRealmInt.create(value.get()))
                        } else {
                            accessor.set(target, value)
                        }
                    }
                    PropertyType.RLM_PROPERTY_TYPE_MIXED -> {
                        val value = accessor.get(source) as RealmAny?
                        if (value?.type == RealmAny.Type.OBJECT) {
                            if (currentDepth == maxDepth) {
                                accessor.set(target, null)
                            } else {
                                val detachedObject = createDetachedCopy(
                                    mediator,
                                    value.asRealmObject(),
                                    currentDepth + 1u,
                                    maxDepth,
                                    closeAfterCopy,
                                    cache
                                ) as RealmObject
                                accessor.set(target, RealmAny.create(detachedObject))
                            }
                        } else {
                            accessor.set(target, value)
                        }
                    }
                    else -> {
                        val value = accessor.get(source)
                        accessor.set(target, value)
                    }
                }
                CollectionType.RLM_COLLECTION_TYPE_LIST -> {
                    val elements: List<Any?> = accessor.get(source) as List<Any?>
                    when (property.type) {
                        PropertyType.RLM_PROPERTY_TYPE_INT,
                        PropertyType.RLM_PROPERTY_TYPE_BOOL,
                        PropertyType.RLM_PROPERTY_TYPE_STRING,
                        PropertyType.RLM_PROPERTY_TYPE_BINARY,
                        PropertyType.RLM_PROPERTY_TYPE_FLOAT,
                        PropertyType.RLM_PROPERTY_TYPE_DOUBLE,
                        PropertyType.RLM_PROPERTY_TYPE_DECIMAL128,
                        PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP,
                        PropertyType.RLM_PROPERTY_TYPE_OBJECT_ID,
                        PropertyType.RLM_PROPERTY_TYPE_UUID -> {
                            accessor.set(target, elements.toRealmList())
                        }
                        PropertyType.RLM_PROPERTY_TYPE_MIXED -> {
                            val detachedRealmAnyList = (elements as List<RealmAny?>).map { value ->
                                if (value?.type == RealmAny.Type.OBJECT) {
                                    if (currentDepth < maxDepth) {
                                        val detachedObject = createDetachedCopy(
                                            mediator,
                                            value.asRealmObject(),
                                            currentDepth + 1u,
                                            maxDepth,
                                            closeAfterCopy,
                                            cache
                                        ) as RealmObject
                                        RealmAny.create(detachedObject)
                                    } else {
                                        null
                                    }
                                } else {
                                    value
                                }
                            }
                            accessor.set(target, detachedRealmAnyList.toRealmList())
                        }
                        PropertyType.RLM_PROPERTY_TYPE_OBJECT -> {
                            val list = UnmanagedRealmList<BaseRealmObject>()
                            if (currentDepth < maxDepth) {
                                (elements as List<BaseRealmObject>).forEach { listObject: BaseRealmObject ->
                                    list.add(
                                        createDetachedCopy(
                                            mediator,
                                            listObject,
                                            currentDepth + 1u,
                                            maxDepth,
                                            closeAfterCopy,
                                            cache
                                        )
                                    )
                                }
                            }
                            accessor.set(target, list)
                        }
                        else -> {
                            throw IllegalStateException("Unknown type: ${property.type}")
                        }
                    }
                }
                CollectionType.RLM_COLLECTION_TYPE_SET -> {
                    val elements: Set<Any?> = accessor.get(source) as Set<Any?>
                    when (property.type) {
                        PropertyType.RLM_PROPERTY_TYPE_INT,
                        PropertyType.RLM_PROPERTY_TYPE_BOOL,
                        PropertyType.RLM_PROPERTY_TYPE_STRING,
                        PropertyType.RLM_PROPERTY_TYPE_BINARY,
                        PropertyType.RLM_PROPERTY_TYPE_FLOAT,
                        PropertyType.RLM_PROPERTY_TYPE_DOUBLE,
                        PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP,
                        PropertyType.RLM_PROPERTY_TYPE_OBJECT_ID,
                        PropertyType.RLM_PROPERTY_TYPE_DECIMAL128,
                        PropertyType.RLM_PROPERTY_TYPE_UUID -> {
                            accessor.set(target, elements.toRealmSet())
                        }
                        PropertyType.RLM_PROPERTY_TYPE_MIXED -> {
                            val detachedRealmAnySet = (elements as Set<RealmAny?>).map { value ->
                                if (value?.type == RealmAny.Type.OBJECT) {
                                    if (currentDepth < maxDepth) {
                                        val detachedObject = createDetachedCopy(
                                            mediator,
                                            value.asRealmObject(),
                                            currentDepth + 1u,
                                            maxDepth,
                                            closeAfterCopy,
                                            cache
                                        ) as RealmObject
                                        RealmAny.create(detachedObject)
                                    } else {
                                        null
                                    }
                                } else {
                                    value
                                }
                            }
                            accessor.set(target, detachedRealmAnySet.toRealmSet())
                        }
                        PropertyType.RLM_PROPERTY_TYPE_OBJECT -> {
                            val set = UnmanagedRealmSet<BaseRealmObject>()
                            if (currentDepth < maxDepth) {
                                (elements as Set<BaseRealmObject>).forEach { realmObject: BaseRealmObject ->
                                    set.add(
                                        createDetachedCopy(
                                            mediator,
                                            realmObject,
                                            currentDepth + 1u,
                                            maxDepth,
                                            closeAfterCopy,
                                            cache
                                        )
                                    )
                                }
                            }
                            accessor.set(target, set)
                        }
                        else -> {
                            throw IllegalStateException("Unknown type: ${property.type}")
                        }
                    }
                }
                CollectionType.RLM_COLLECTION_TYPE_DICTIONARY -> {
                    val elements: RealmDictionary<Any?> = accessor.get(source) as RealmDictionary<Any?>
                    when (property.type) {
                        PropertyType.RLM_PROPERTY_TYPE_INT,
                        PropertyType.RLM_PROPERTY_TYPE_BOOL,
                        PropertyType.RLM_PROPERTY_TYPE_STRING,
                        PropertyType.RLM_PROPERTY_TYPE_BINARY,
                        PropertyType.RLM_PROPERTY_TYPE_FLOAT,
                        PropertyType.RLM_PROPERTY_TYPE_DOUBLE,
                        PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP,
                        PropertyType.RLM_PROPERTY_TYPE_OBJECT_ID,
                        PropertyType.RLM_PROPERTY_TYPE_DECIMAL128,
                        PropertyType.RLM_PROPERTY_TYPE_UUID -> {
                            accessor.set(target, elements.toRealmDictionary())
                        }
                        PropertyType.RLM_PROPERTY_TYPE_MIXED -> {
                            val detachedRealmAnyDictionary = (elements as RealmDictionary<RealmAny?>).map { entry ->
                                if (entry.value?.type == RealmAny.Type.OBJECT) {
                                    if (currentDepth < maxDepth) {
                                        entry.value?.let { realmAny ->
                                            createDetachedCopy(
                                                mediator,
                                                realmAny.asRealmObject(),
                                                currentDepth + 1u,
                                                maxDepth,
                                                closeAfterCopy,
                                                cache
                                            ) as RealmObject
                                        }?.let {
                                            Pair(entry.key, RealmAny.create(it))
                                        } ?: Pair(entry.key, null)
                                    } else {
                                        Pair(entry.key, null)
                                    }
                                } else {
                                    Pair(entry.key, entry.value)
                                }
                            }
                            accessor.set(target, detachedRealmAnyDictionary.toRealmDictionary())
                        }
                        PropertyType.RLM_PROPERTY_TYPE_OBJECT -> {
                            val dictionary = UnmanagedRealmDictionary<BaseRealmObject>()
                            if (currentDepth < maxDepth) {
                                (elements as RealmDictionary<BaseRealmObject>).forEach { entry ->
                                    dictionary[entry.key] = createDetachedCopy(
                                        mediator,
                                        entry.value,
                                        currentDepth + 1u,
                                        maxDepth,
                                        closeAfterCopy,
                                        cache
                                    )
                                }
                            }
                            accessor.set(target, dictionary)
                        }
                        else -> {
                            throw IllegalStateException("Unknown type: ${property.type}")
                        }
                    }
                }
                else -> {
                    throw IllegalStateException("Unknown collection type: ${property.collectionType}")
                }
            }
        }
    }

    fun dynamicGetBacklinks(
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
