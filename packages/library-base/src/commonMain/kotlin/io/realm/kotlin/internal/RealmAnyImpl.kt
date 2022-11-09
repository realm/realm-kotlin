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
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * TODO
 */
internal class RealmAnyImpl(
    internal val operator: RealmAnyOperator
) : RealmAny {

    override val type: RealmAny.Type = operator.type

    override fun asShort(): Short = (operator.getValue(RealmAny.Type.INT) as Long).toShort()
    override fun asInt(): Int = (operator.getValue(RealmAny.Type.INT) as Long).toInt()
    override fun asByte(): Byte = (operator.getValue(RealmAny.Type.INT) as Long).toByte()
    override fun asChar(): Char = (operator.getValue(RealmAny.Type.INT) as Long).toInt().toChar()
    override fun asLong(): Long = operator.getValue(RealmAny.Type.INT) as Long
    override fun asBoolean(): Boolean = operator.getValue(RealmAny.Type.BOOLEAN) as Boolean
    override fun asString(): String = operator.getValue(RealmAny.Type.STRING) as String
    override fun asFloat(): Float = operator.getValue(RealmAny.Type.FLOAT) as Float
    override fun asDouble(): Double = operator.getValue(RealmAny.Type.DOUBLE) as Double

    override fun asRealmObjectId(): ObjectId =
        ObjectId.from((operator.getValue(RealmAny.Type.OBJECT_ID) as BsonObjectId).toByteArray())

    override fun asObjectId(): BsonObjectId =
        operator.getValue(RealmAny.Type.OBJECT_ID) as BsonObjectId

    override fun asByteArray(): ByteArray = operator.getValue(RealmAny.Type.BYTE_ARRAY) as ByteArray

    override fun asRealmInstant(): RealmInstant =
        operator.getValue(RealmAny.Type.REALM_INSTANT) as RealmInstant

    override fun asRealmUUID(): RealmUUID = operator.getValue(RealmAny.Type.REALM_UUID) as RealmUUID

    override fun <T : BaseRealmObject> asRealmObject(clazz: KClass<T>): T =
        operator.getValue(RealmAny.Type.REALM_OBJECT).let { clazz.cast(it) }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is RealmAnyImpl) return false
        if (other.type != this.type) return false
        return operator == other.operator
    }

    override fun hashCode(): Int {
        var result = operator.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }
}

internal interface RealmAnyOperator {
    val type: RealmAny.Type
    fun getValue(type: RealmAny.Type): Any
}

internal class RealmAnyPrimitiveOperator(
    override val type: RealmAny.Type,
    private val value: Any
) : RealmAnyOperator {

    override fun getValue(type: RealmAny.Type): Any {
        if (this.type != type) {
            throw IllegalStateException("RealmAny type mismatch, wanted a '${type.name}' but the instance is a '${this.type.name}'.")
        }
        return value
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is RealmAnyPrimitiveOperator) return false
        if (other.value != this.value) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}

internal class RealmAnyByteArrayOperator(
    override val type: RealmAny.Type,
    private val value: Any
) : RealmAnyOperator {

    override fun getValue(type: RealmAny.Type): Any {
        if (this.type != type) {
            throw IllegalStateException("RealmAny type mismatch, wanted a '${type.name}' but the instance is a '${this.type.name}'.")
        }
        return value
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is RealmAnyByteArrayOperator) return false
        if (other.value !is ByteArray) return false
        if (!other.value.contentEquals(this.value as ByteArray)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}

internal class RealmAnyObjectOperator(
    override val type: RealmAny.Type,
    private val value: Any,
) : RealmAnyOperator {

    override fun getValue(type: RealmAny.Type): Any {
        if (this.type != type) {
            throw IllegalStateException("RealmAny type mismatch, wanted a '${type.name}' but the instance is a '${this.type.name}'.")
        }
        return value
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is RealmAnyObjectOperator) return false
        if (other.value !== this.value) return false
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}
