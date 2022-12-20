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

import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import kotlin.reflect.KClass
import kotlin.reflect.cast

internal class RealmAnyImpl<T : Any> constructor(
    override val type: RealmAny.Type,
    internal val clazz: KClass<T>,
    internal val value: Any
) : RealmAny {

    private var shortValue: Short? = null
    private var intValue: Int? = null
    private var byteValue: Byte? = null
    private var charValue: Char? = null
    private var longValue: Long? = null
    private var booleanValue: Boolean? = null
    private var stringValue: String? = null
    private var floatValue: Float? = null
    private var doubleValue: Double? = null
    private var objectIdValue: BsonObjectId? = null
    private var byteArrayValue: ByteArray? = null
    private var realmInstantValue: RealmInstant? = null
    private var realmUUIDValue: RealmUUID? = null
    private var realmObjectValue: BaseRealmObject? = null

    private var numericOverflow: NumericOverflow = NumericOverflow()

    init {
        when (type) {
            RealmAny.Type.INT -> {
                val storageTypeValue = when (val internalValue = getValue(RealmAny.Type.INT)) {
                    is Number -> internalValue.toLong()
                    else -> (internalValue as Char).code.toLong()
                }

                if (storageTypeValue > Int.MAX_VALUE) {
                    numericOverflow = numericOverflow.copy(
                        intOverflow = true,
                        charOverflow = true,
                        shortOverflow = true,
                        byteOverflow = true
                    )
                }
                if (storageTypeValue > Char.MAX_VALUE.code) {
                    numericOverflow = numericOverflow.copy(
                        charOverflow = true,
                        shortOverflow = true,
                        byteOverflow = true
                    )
                }
                if (storageTypeValue > Short.MAX_VALUE) {
                    numericOverflow = numericOverflow.copy(
                        shortOverflow = true,
                        byteOverflow = true
                    )
                }
                if (storageTypeValue > Byte.MAX_VALUE) {
                    numericOverflow = numericOverflow.copy(
                        byteOverflow = true
                    )
                }
                shortValue = storageTypeValue.toShort()
                intValue = storageTypeValue.toInt()
                byteValue = storageTypeValue.toByte()
                charValue = storageTypeValue.toInt().toChar()
                longValue = storageTypeValue
            }
            RealmAny.Type.BOOL -> booleanValue = getValue(RealmAny.Type.BOOL) as Boolean
            RealmAny.Type.STRING -> stringValue = getValue(RealmAny.Type.STRING) as String
            RealmAny.Type.BINARY -> byteArrayValue = getValue(RealmAny.Type.BINARY) as ByteArray
            RealmAny.Type.TIMESTAMP ->
                realmInstantValue = getValue(RealmAny.Type.TIMESTAMP) as RealmInstant
            RealmAny.Type.FLOAT -> floatValue = getValue(RealmAny.Type.FLOAT) as Float
            RealmAny.Type.DOUBLE -> doubleValue = getValue(RealmAny.Type.DOUBLE) as Double
            RealmAny.Type.OBJECT_ID ->
                objectIdValue = getValue(RealmAny.Type.OBJECT_ID) as BsonObjectId
            RealmAny.Type.UUID -> realmUUIDValue = getValue(RealmAny.Type.UUID) as RealmUUID
            RealmAny.Type.OBJECT ->
                realmObjectValue = getValue(RealmAny.Type.OBJECT) as BaseRealmObject
        }
    }

    override fun asShort(): Short {
        return checkOverflow("asShort", numericOverflow.shortOverflow).run {
            shortValue ?: throw IllegalStateException("No value for type ${type.name}")
        }
    }

    override fun asInt(): Int {
        return checkOverflow("asInt", numericOverflow.intOverflow).run {
            intValue ?: throw IllegalStateException("No value for type ${type.name}")
        }
    }

    override fun asByte(): Byte {
        return checkOverflow("asByte", numericOverflow.byteOverflow).run {
            byteValue ?: throw IllegalStateException("No value for type ${type.name}")
        }
    }

    override fun asChar(): Char {
        return checkOverflow("asChar", numericOverflow.charOverflow).run {
            charValue ?: throw IllegalStateException("No value for type ${type.name}")
        }
    }

    override fun asLong(): Long =
        longValue ?: throw IllegalStateException("No value for type ${type.name}")

    override fun asBoolean(): Boolean =
        booleanValue ?: throw IllegalStateException("No value for type ${type.name}")

    override fun asString(): String =
        stringValue ?: throw IllegalStateException("No value for type ${type.name}")

    override fun asFloat(): Float =
        floatValue ?: throw IllegalStateException("No value for type ${type.name}")

    override fun asDouble(): Double =
        doubleValue ?: throw IllegalStateException("No value for type ${type.name}")

    override fun asObjectId(): BsonObjectId =
        objectIdValue ?: throw IllegalStateException("No value for type ${type.name}")

    override fun asByteArray(): ByteArray =
        byteArrayValue ?: throw IllegalStateException("No value for type ${type.name}")

    override fun asRealmInstant(): RealmInstant =
        realmInstantValue ?: throw IllegalStateException("No value for type ${type.name}")

    override fun asRealmUUID(): RealmUUID =
        realmUUIDValue ?: throw IllegalStateException("No value for type ${type.name}")

    override fun <T : BaseRealmObject> asRealmObject(clazz: KClass<T>): T {
        val getValue = getValue(RealmAny.Type.OBJECT)
        return clazz.cast(getValue)
    }

    private fun getValue(type: RealmAny.Type): Any {
        if (this.type != type) {
            throw IllegalStateException("RealmAny type mismatch, wanted a '${type.name}' but the instance is a '${this.type.name}'.")
        }
        return value
    }

    private fun checkOverflow(coercionFunctionName: String, overflow: Boolean) {
        if (overflow) {
            throw ArithmeticException("Cannot convert value with '$coercionFunctionName' due to overflow for value $value")
        }
    }

    @Suppress("ComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other === this) return true
        if (other !is RealmAnyImpl<*>) return false
        if (other.type != this.type) return false
        if (clazz == ByteArray::class) {
            if (other.value !is ByteArray) return false
            if (!other.value.contentEquals(this.value as ByteArray)) return false
        } else if (value is ObjectId || value is BsonObjectId) {
            if (other.clazz != ObjectId::class && other.clazz != BsonObjectId::class) return false
            if (other.value != this.value) return false
        } else if (value is RealmObject) {
            if (other.clazz != this.clazz) return false
            if (other.value !== this.value) return false
        } else if (value is Number) { // Numerics are the same as long as their value is the same
            when (other.value) {
                is Char -> if (other.value.code.toLong() != value.toLong()) return false
                is Number -> if (other.value.toLong() != this.value.toLong()) return false
                else -> return false
            }
        } else if (value is Char) { // We are comparing chars
            when (other.value) {
                is Char -> if (other.value.code.toLong() != value.toLong()) return false
                is Number -> if (other.value.toLong() != this.value.toLong()) return false
                else -> return false
            }
        }
        return true
    }

    @Suppress("ComplexMethod")
    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + clazz.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + (shortValue ?: 0)
        result = 31 * result + (intValue ?: 0)
        result = 31 * result + (byteValue ?: 0)
        result = 31 * result + (charValue?.hashCode() ?: 0)
        result = 31 * result + (longValue?.hashCode() ?: 0)
        result = 31 * result + (booleanValue?.hashCode() ?: 0)
        result = 31 * result + (stringValue?.hashCode() ?: 0)
        result = 31 * result + (floatValue?.hashCode() ?: 0)
        result = 31 * result + (doubleValue?.hashCode() ?: 0)
        result = 31 * result + (objectIdValue?.hashCode() ?: 0)
        result = 31 * result + (byteArrayValue?.contentHashCode() ?: 0)
        result = 31 * result + (realmInstantValue?.hashCode() ?: 0)
        result = 31 * result + (realmUUIDValue?.hashCode() ?: 0)
        result = 31 * result + (realmObjectValue?.hashCode() ?: 0)
        result = 31 * result + numericOverflow.hashCode()
        return result
    }

    override fun toString(): String = "RealmAny{type=$type, value=${getValue(type)}}"
}

private data class NumericOverflow constructor(
    val intOverflow: Boolean = false,
    val charOverflow: Boolean = false,
    val shortOverflow: Boolean = false,
    val byteOverflow: Boolean = false
)


///*
// * Copyright 2022 Realm Inc.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package io.realm.kotlin.internal
//
//import io.realm.kotlin.types.BaseRealmObject
//import io.realm.kotlin.types.ObjectId
//import io.realm.kotlin.types.RealmAny
//import io.realm.kotlin.types.RealmInstant
//import io.realm.kotlin.types.RealmObject
//import io.realm.kotlin.types.RealmUUID
//import org.mongodb.kbson.BsonObjectId
//import kotlin.reflect.KClass
//import kotlin.reflect.cast
//
//internal class RealmAnyImpl<T : Any> constructor(
//    override val type: RealmAny.Type,
//    internal val clazz: KClass<T>,
//    internal val value: Any
//) : RealmAny {
//
//    private var shortValue: Short? = null
//    private var intValue: Int? = null
//    private var byteValue: Byte? = null
//    private var charValue: Char? = null
//    private var longValue: Long? = null
//    private var booleanValue: Boolean? = null
//    private var stringValue: String? = null
//    private var floatValue: Float? = null
//    private var doubleValue: Double? = null
//    private var realmObjectIdValue: ObjectId? = null
//    private var objectIdValue: BsonObjectId? = null
//    private var byteArrayValue: ByteArray? = null
//    private var realmInstantValue: RealmInstant? = null
//    private var realmUUIDValue: RealmUUID? = null
//    private var realmObjectValue: BaseRealmObject? = null
//
//    private var numericOverflow: NumericOverflow = NumericOverflow()
//
//    init {
//        when (type) {
//            RealmAny.Type.INT -> {
//                val storageTypeValue = when (val internalValue = getValue(RealmAny.Type.INT)) {
//                    is Number -> internalValue.toLong()
//                    else -> (internalValue as Char).code.toLong()
//                }
//
//                if (storageTypeValue > Int.MAX_VALUE) {
//                    numericOverflow = numericOverflow.copy(
//                        intOverflow = true,
//                        charOverflow = true,
//                        shortOverflow = true,
//                        byteOverflow = true
//                    )
//                }
//                if (storageTypeValue > Char.MAX_VALUE.code) {
//                    numericOverflow = numericOverflow.copy(
//                        charOverflow = true,
//                        shortOverflow = true,
//                        byteOverflow = true
//                    )
//                }
//                if (storageTypeValue > Short.MAX_VALUE) {
//                    numericOverflow = numericOverflow.copy(
//                        shortOverflow = true,
//                        byteOverflow = true
//                    )
//                }
//                if (storageTypeValue > Byte.MAX_VALUE) {
//                    numericOverflow = numericOverflow.copy(
//                        byteOverflow = true
//                    )
//                }
//                shortValue = storageTypeValue.toShort()
//                intValue = storageTypeValue.toInt()
//                byteValue = storageTypeValue.toByte()
//                charValue = storageTypeValue.toInt().toChar()
//                longValue = storageTypeValue
//            }
//            RealmAny.Type.BOOL -> booleanValue = getValue(RealmAny.Type.BOOL) as Boolean
//            RealmAny.Type.STRING -> stringValue = getValue(RealmAny.Type.STRING) as String
//            RealmAny.Type.BINARY -> byteArrayValue = getValue(RealmAny.Type.BINARY) as ByteArray
//            RealmAny.Type.TIMESTAMP ->
//                realmInstantValue = getValue(RealmAny.Type.TIMESTAMP) as RealmInstant
//            RealmAny.Type.FLOAT -> floatValue = getValue(RealmAny.Type.FLOAT) as Float
//            RealmAny.Type.DOUBLE -> doubleValue = getValue(RealmAny.Type.DOUBLE) as Double
//            RealmAny.Type.OBJECT_ID -> {
//                realmObjectIdValue =
//                    ObjectId.from((getValue(RealmAny.Type.OBJECT_ID) as BsonObjectId).toByteArray())
//                objectIdValue = getValue(RealmAny.Type.OBJECT_ID) as BsonObjectId
//            }
//            RealmAny.Type.UUID -> realmUUIDValue = getValue(RealmAny.Type.UUID) as RealmUUID
//            RealmAny.Type.OBJECT ->
//                realmObjectValue = getValue(RealmAny.Type.OBJECT) as BaseRealmObject
//        }
//    }
//
//    override fun asShort(): Short {
//        return checkOverflow("asShort", numericOverflow.shortOverflow).run {
//            shortValue ?: throw IllegalStateException("No value for type ${type.name}")
//        }
//    }
//
//    override fun asInt(): Int {
//        return checkOverflow("asInt", numericOverflow.intOverflow).run {
//            intValue ?: throw IllegalStateException("No value for type ${type.name}")
//        }
//    }
//
//    override fun asByte(): Byte {
//        return checkOverflow("asByte", numericOverflow.byteOverflow).run {
//            byteValue ?: throw IllegalStateException("No value for type ${type.name}")
//        }
//    }
//
//    override fun asChar(): Char {
//        return checkOverflow("asChar", numericOverflow.charOverflow).run {
//            charValue ?: throw IllegalStateException("No value for type ${type.name}")
//        }
//    }
//
//    override fun asLong(): Long =
//        longValue ?: throw IllegalStateException("No value for type ${type.name}")
//
//    override fun asBoolean(): Boolean =
//        booleanValue ?: throw IllegalStateException("No value for type ${type.name}")
//
//    override fun asString(): String =
//        stringValue ?: throw IllegalStateException("No value for type ${type.name}")
//
//    override fun asFloat(): Float =
//        floatValue ?: throw IllegalStateException("No value for type ${type.name}")
//
//    override fun asDouble(): Double =
//        doubleValue ?: throw IllegalStateException("No value for type ${type.name}")
//
//    @Deprecated(
//        "Use the BSON ObjectId variant instead",
//        replaceWith = ReplaceWith("RealmAny.asObjectId")
//    )
//    override fun asRealmObjectId(): ObjectId =
//        realmObjectIdValue ?: throw IllegalStateException("No value for type ${type.name}")
//
//    override fun asObjectId(): BsonObjectId =
//        objectIdValue ?: throw IllegalStateException("No value for type ${type.name}")
//
//    override fun asByteArray(): ByteArray =
//        byteArrayValue ?: throw IllegalStateException("No value for type ${type.name}")
//
//    override fun asRealmInstant(): RealmInstant =
//        realmInstantValue ?: throw IllegalStateException("No value for type ${type.name}")
//
//    override fun asRealmUUID(): RealmUUID =
//        realmUUIDValue ?: throw IllegalStateException("No value for type ${type.name}")
//
//    override fun <T : BaseRealmObject> asRealmObject(clazz: KClass<T>): T {
//        val getValue = getValue(RealmAny.Type.OBJECT)
//        return clazz.cast(getValue)
//    }
//
//    private fun getValue(type: RealmAny.Type): Any {
//        if (this.type != type) {
//            throw IllegalStateException("RealmAny type mismatch, wanted a '${type.name}' but the instance is a '${this.type.name}'.")
//        }
//        return value
//    }
//
//    private fun checkOverflow(coercionFunctionName: String, overflow: Boolean) {
//        if (overflow) {
//            throw ArithmeticException("Cannot convert value with '$coercionFunctionName' due to overflow for value $value")
//        }
//    }
//
//    @Suppress("ComplexMethod")
//    override fun equals(other: Any?): Boolean {
//        if (other == null) return false
//        if (other === this) return true
//        if (other !is RealmAnyImpl<*>) return false
//        if (other.type != this.type) return false
//        if (clazz == ByteArray::class) {
//            if (other.value !is ByteArray) return false
//            if (!other.value.contentEquals(this.value as ByteArray)) return false
//        } else if (value is ObjectId || value is BsonObjectId) {
//            if (other.clazz != ObjectId::class && other.clazz != BsonObjectId::class) return false
//            if (other.value != this.value) return false
//        } else if (value is RealmObject) {
//            if (other.clazz != this.clazz) return false
//            if (other.value !== this.value) return false
//        } else if (value is Number) { // Numerics are the same as long as their value is the same
//            when (other.value) {
//                is Char -> if (other.value.code.toLong() != value.toLong()) return false
//                is Number -> if (other.value.toLong() != this.value.toLong()) return false
//                else -> return false
//            }
//        } else if (value is Char) { // We are comparing chars
//            when (other.value) {
//                is Char -> if (other.value.code.toLong() != value.toLong()) return false
//                is Number -> if (other.value.toLong() != this.value.toLong()) return false
//                else -> return false
//            }
//        }
//        return true
//    }
//
//    @Suppress("ComplexMethod")
//    override fun hashCode(): Int {
//        var result = type.hashCode()
//        result = 31 * result + clazz.hashCode()
//        result = 31 * result + value.hashCode()
//        result = 31 * result + (shortValue ?: 0)
//        result = 31 * result + (intValue ?: 0)
//        result = 31 * result + (byteValue ?: 0)
//        result = 31 * result + (charValue?.hashCode() ?: 0)
//        result = 31 * result + (longValue?.hashCode() ?: 0)
//        result = 31 * result + (booleanValue?.hashCode() ?: 0)
//        result = 31 * result + (stringValue?.hashCode() ?: 0)
//        result = 31 * result + (floatValue?.hashCode() ?: 0)
//        result = 31 * result + (doubleValue?.hashCode() ?: 0)
//        result = 31 * result + (realmObjectIdValue?.hashCode() ?: 0)
//        result = 31 * result + (objectIdValue?.hashCode() ?: 0)
//        result = 31 * result + (byteArrayValue?.contentHashCode() ?: 0)
//        result = 31 * result + (realmInstantValue?.hashCode() ?: 0)
//        result = 31 * result + (realmUUIDValue?.hashCode() ?: 0)
//        result = 31 * result + (realmObjectValue?.hashCode() ?: 0)
//        result = 31 * result + numericOverflow.hashCode()
//        return result
//    }
//
//    override fun toString(): String = "RealmAny{type=$type, value=${getValue(type)}}"
//}
//
//private data class NumericOverflow constructor(
//    val intOverflow: Boolean = false,
//    val charOverflow: Boolean = false,
//    val shortOverflow: Boolean = false,
//    val byteOverflow: Boolean = false
//)
