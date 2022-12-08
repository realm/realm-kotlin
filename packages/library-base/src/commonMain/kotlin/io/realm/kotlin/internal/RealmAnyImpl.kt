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

import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import kotlin.reflect.KClass
import kotlin.reflect.cast

internal class RealmAnyImpl constructor(
    internal val operator: RealmAnyOperator<*>
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

    @Deprecated(
        "Use the BSON ObjectId variant instead",
        replaceWith = ReplaceWith("RealmAny.asObjectId")
    )
    override fun asRealmObjectId(): ObjectId =
        ObjectId.from((operator.getValue(RealmAny.Type.OBJECT_ID) as BsonObjectId).toByteArray())

    override fun asObjectId(): BsonObjectId =
        operator.getValue(RealmAny.Type.OBJECT_ID) as BsonObjectId

    override fun asByteArray(): ByteArray = operator.getValue(RealmAny.Type.BYTE_ARRAY) as ByteArray

    override fun asRealmInstant(): RealmInstant =
        operator.getValue(RealmAny.Type.REALM_INSTANT) as RealmInstant

    override fun asRealmUUID(): RealmUUID = operator.getValue(RealmAny.Type.REALM_UUID) as RealmUUID

    override fun <T : RealmObject> asRealmObject(clazz: KClass<T>): T =
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

    override fun toString(): String =
        "RealmAny{type=${operator.type}, value=${operator.getValue(type)}}"
}

internal class RealmAnyOperator<T : Any> constructor(
    val type: RealmAny.Type,
    val clazz: KClass<T>,
    val value: Any
) {

    fun getValue(type: RealmAny.Type): Any {
        if (this.type != type) {
            throw IllegalStateException("RealmAny type mismatch, wanted a '${type.name}' but the instance is a '${this.type.name}'.")
        }
        return value
    }

    @Suppress("ComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is RealmAnyOperator<*>) return false

        if (clazz == ByteArray::class) {
            if (other.value !is ByteArray) return false
            if (!other.value.contentEquals(this.value as ByteArray)) return false
        } else if (value is ObjectId || value is BsonObjectId) {
            if (other.clazz != ObjectId::class && other.clazz != BsonObjectId::class) return false
            if (other.value != this.value) return false
        } else if (value is RealmObject) {
            if (other.clazz != this.clazz) return false
            if (other.value !== this.value) return false
        } else {
            if (other.clazz != this.clazz) return false
            if (other.value != this.value) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + clazz.hashCode()
        result = 31 * result + value.hashCode()
        return result
    }
}
