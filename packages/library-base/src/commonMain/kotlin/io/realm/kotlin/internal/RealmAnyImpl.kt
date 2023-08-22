/*
 * Copyright 2022 Realm Inc.
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
import io.realm.kotlin.internal.RealmObjectHelper.setValueTransportByKey
import io.realm.kotlin.internal.interop.CollectionType
import io.realm.kotlin.internal.interop.MemTrackingAllocator
import io.realm.kotlin.internal.interop.PropertyKey
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import kotlin.reflect.KClass
import kotlin.reflect.cast

internal sealed interface RealmAnyContainer {

    val mediator: Mediator
    val realm: RealmReference
    val obj: RealmObjectReference<*>
    fun set(
        allocator: MemTrackingAllocator,
        value: RealmAny,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ) {
        with(allocator) {
            when (value.type) {
                RealmAny.Type.INT,
                RealmAny.Type.BOOL,
                RealmAny.Type.STRING,
                RealmAny.Type.BINARY,
                RealmAny.Type.TIMESTAMP,
                RealmAny.Type.FLOAT,
                RealmAny.Type.DOUBLE,
                RealmAny.Type.DECIMAL128,
                RealmAny.Type.OBJECT_ID,
                RealmAny.Type.UUID,
                RealmAny.Type.OBJECT ->
                    setPrimitive(value)
                RealmAny.Type.SET -> {
                    createCollection(CollectionType.RLM_COLLECTION_TYPE_SET)
                    (getSet() as ManagedRealmSet).operator.addAll(
                        value.asSet(),
                        updatePolicy,
                        cache
                    )
                }

                RealmAny.Type.LIST -> {
                    createCollection(CollectionType.RLM_COLLECTION_TYPE_LIST)
                    (getList() as ManagedRealmList).operator.insertAll(
                        0,
                        value.asList(),
                        updatePolicy,
                        cache
                    )
                }
                RealmAny.Type.DICTIONARY -> {
                    createCollection(CollectionType.RLM_COLLECTION_TYPE_DICTIONARY)
                    (getDictionary() as ManagedRealmDictionary).operator.putAll(
                        value.asDictionary(),
                        updatePolicy,
                        cache
                    )
                }
            }
        }
    }

    fun MemTrackingAllocator.setPrimitive(value: RealmAny)
    fun MemTrackingAllocator.createCollection(collectionType: CollectionType)
    fun getSet(): RealmSet<RealmAny?>
    fun getList(): RealmList<RealmAny?>
    fun getDictionary(): RealmDictionary<RealmAny?>
}

internal class RealmAnyProperty(
    override val obj: RealmObjectReference<*>,
    val key: PropertyKey,
    val issueDynamicObject: Boolean,
    val issueDynamicMutableObject: Boolean,
) : RealmAnyContainer {

    override val mediator = obj.mediator
    override val realm: RealmReference = obj.owner

    override fun MemTrackingAllocator.createCollection(collectionType: CollectionType) {
        RealmInterop.realm_set_value(obj.objectPointer, key, nullTransport(), false)
        when(collectionType) {
            CollectionType.RLM_COLLECTION_TYPE_NONE -> TODO()
            CollectionType.RLM_COLLECTION_TYPE_LIST -> {
                RealmInterop.realm_set_list(obj.objectPointer, key)
            }
            CollectionType.RLM_COLLECTION_TYPE_SET -> {
                RealmInterop.realm_set_set(obj.objectPointer, key)
            }
            CollectionType.RLM_COLLECTION_TYPE_DICTIONARY -> {
                RealmInterop.realm_set_dictionary(obj.objectPointer, key)
            }
            else -> TODO()
        }
    }

    override fun MemTrackingAllocator.setPrimitive(value: RealmAny) {
        val converter = if (value.type == RealmAny.Type.OBJECT) {
            when ((value as RealmAnyImpl<*>).clazz) {
                DynamicRealmObject::class ->
                    realmAnyConverter(obj.mediator, obj.owner, true)
                DynamicMutableRealmObject::class ->
                    realmAnyConverter(
                        obj.mediator,
                        obj.owner,
                        issueDynamicObject = true,
                        issueDynamicMutableObject = true
                    )
                else ->
                    realmAnyConverter(obj.mediator, obj.owner)
            }
        } else {
            realmAnyConverter(obj.mediator, obj.owner)
        }
        with(converter) {
            setValueTransportByKey(obj, key, publicToRealmValue(value))
        }
    }

    override fun getSet(): RealmSet<RealmAny?> {
        val nativePointer = RealmInterop.realm_get_set(obj.objectPointer, key)
        val operator = RealmAnySetOperator(
            mediator,
            realm,
            nativePointer,
            issueDynamicObject,
            issueDynamicMutableObject
        )
        return ManagedRealmSet(obj, nativePointer, operator)
    }

    override fun getList(): RealmList<RealmAny?> {
        val nativePointer = RealmInterop.realm_get_list(obj.objectPointer, key)
        val operator = RealmAnyListOperator(mediator, realm, nativePointer, issueDynamicObject = issueDynamicObject, issueDynamicMutableObject = issueDynamicMutableObject)
        val realmAnyList = ManagedRealmList(obj, nativePointer, operator)
        return realmAnyList
    }

    override fun getDictionary(): RealmDictionary<RealmAny?> {
        val nativePointer = RealmInterop.realm_get_dictionary(obj.objectPointer, key)
        val operator = RealmAnyMapOperator(
            mediator,
            realm,
            realmAnyConverter(mediator, realm, issueDynamicObject, issueDynamicMutableObject),
            converter(String::class, mediator, realm),
            nativePointer,
            issueDynamicObject, issueDynamicMutableObject
        )
        val realmAnyDictionary = ManagedRealmDictionary(obj, nativePointer, operator)
        return realmAnyDictionary
    }
}

internal class RealmAnyImpl<T : Any> constructor(
    override val type: RealmAny.Type,
    internal val clazz: KClass<T>,
    value: Any
) : RealmAny {

    private val internalValue: Any

    init {
        internalValue = when (type) {
            RealmAny.Type.INT -> when (value) {
                is Number -> value.toLong()
                is Char -> value.code.toLong()
                else -> throw IllegalArgumentException("Unsupported numeric type. Only Long, Short, Int, Byte and Char are valid numeric types.")
            }
            else -> value
        }
    }

    override fun asShort(): Short {
        checkOverFlow(Short::class)
        return (getValue(RealmAny.Type.INT) as Long).toShort()
    }

    override fun asInt(): Int {
        checkOverFlow(Int::class)
        return (getValue(RealmAny.Type.INT) as Long).toInt()
    }

    override fun asByte(): Byte {
        checkOverFlow(Byte::class)
        return (getValue(RealmAny.Type.INT) as Long).toByte()
    }

    override fun asChar(): Char {
        checkOverFlow(Char::class)
        return (getValue(RealmAny.Type.INT) as Long).toInt().toChar()
    }

    override fun asLong(): Long = getValue(RealmAny.Type.INT) as Long

    override fun asBoolean(): Boolean = getValue(RealmAny.Type.BOOL) as Boolean

    override fun asString(): String = getValue(RealmAny.Type.STRING) as String

    override fun asFloat(): Float = getValue(RealmAny.Type.FLOAT) as Float

    override fun asDouble(): Double = getValue(RealmAny.Type.DOUBLE) as Double

    override fun asDecimal128(): Decimal128 = getValue(RealmAny.Type.DECIMAL128) as Decimal128

    override fun asObjectId(): BsonObjectId = getValue(RealmAny.Type.OBJECT_ID) as BsonObjectId

    override fun asByteArray(): ByteArray = getValue(RealmAny.Type.BINARY) as ByteArray

    override fun asRealmInstant(): RealmInstant = getValue(RealmAny.Type.TIMESTAMP) as RealmInstant

    override fun asRealmUUID(): RealmUUID = getValue(RealmAny.Type.UUID) as RealmUUID

    override fun <T : BaseRealmObject> asRealmObject(clazz: KClass<T>): T {
        val getValue = getValue(RealmAny.Type.OBJECT)
        return clazz.cast(getValue)
    }

    override fun asSet(): RealmSet<RealmAny?> = getValue(RealmAny.Type.SET) as RealmSet<RealmAny?>

    override fun asList(): RealmList<RealmAny?> =
        getValue(RealmAny.Type.LIST) as RealmList<RealmAny?>

    override fun asDictionary(): RealmDictionary<RealmAny?> =
        getValue(RealmAny.Type.DICTIONARY) as RealmDictionary<RealmAny?>

    private fun getValue(type: RealmAny.Type): Any {
        if (this.type != type) {
            throw IllegalStateException("RealmAny type mismatch, wanted a '${type.name}' but the instance is a '${this.type.name}'.")
        }
        return internalValue
    }

    private fun checkOverFlow(numeric: KClass<*>) {
        val storageTypeValue = when (val internalValue = getValue(RealmAny.Type.INT)) {
            is Number -> internalValue.toLong()
            else -> (internalValue as Char).code.toLong()
        }

        when (numeric) {
            Short::class -> if (storageTypeValue > Short.MAX_VALUE) {
                throw ArithmeticException("Cannot convert value with 'asShort' due to overflow for value $storageTypeValue")
            }
            Int::class -> if (storageTypeValue > Int.MAX_VALUE) {
                throw ArithmeticException("Cannot convert value with 'asInt' due to overflow for value $storageTypeValue")
            }
            Byte::class -> if (storageTypeValue > Byte.MAX_VALUE) {
                throw ArithmeticException("Cannot convert value with 'asByte' due to overflow for value $storageTypeValue")
            }
            Char::class -> if (storageTypeValue > Char.MAX_VALUE.code.toLong()) {
                throw ArithmeticException("Cannot convert value with 'asChar' due to overflow for value $storageTypeValue")
            }
        }
    }

    @Suppress("ComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other === this) return true
        if (other !is RealmAnyImpl<*>) return false
        if (other.type != this.type) return false
        if (clazz == ByteArray::class) {
            if (other.internalValue !is ByteArray) return false
            if (!other.internalValue.contentEquals(this.internalValue as ByteArray)) return false
        } else if (internalValue is BsonObjectId) {
            if (other.clazz != BsonObjectId::class) return false
            if (other.internalValue != this.internalValue) return false
        } else if (internalValue is RealmObject) {
            if (other.clazz != this.clazz) return false
            if (other.internalValue !== this.internalValue) return false
        } else if (internalValue is Number) { // Numerics are the same as long as their value is the same
            when (other.internalValue) {
                is Char -> if (other.internalValue.code.toLong() != internalValue.toLong()) return false
                is Number -> if (other.internalValue.toLong() != this.internalValue.toLong()) return false
                else -> return false
            }
        } else if (internalValue is Char) { // We are comparing chars
            when (other.internalValue) {
                is Char -> if (other.internalValue.code.toLong() != internalValue.toLong()) return false
                is Number -> if (other.internalValue.toLong() != this.internalValue.toLong()) return false
                else -> return false
            }
        }
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + clazz.hashCode()
        result = 31 * result + internalValue.hashCode()
        return result
    }

    override fun toString(): String = "RealmAny{type=$type, value=${getValue(type)}}"
}
