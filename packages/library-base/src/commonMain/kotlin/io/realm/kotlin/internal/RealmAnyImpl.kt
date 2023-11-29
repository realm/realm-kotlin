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
            return other.internalValue.contentEquals(this.internalValue as ByteArray)
        } else if (internalValue is RealmObject) {
            if (other.clazz != this.clazz) return false
            return other.internalValue == this.internalValue
        }
        return internalValue == other.internalValue
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + clazz.hashCode()
        result = 31 * result + internalValue.hashCode()
        return result
    }

    override fun toString(): String = "RealmAny{type=$type, value=${getValue(type)}}"
}
