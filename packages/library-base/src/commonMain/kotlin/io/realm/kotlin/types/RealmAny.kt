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

package io.realm.kotlin.types

import io.realm.kotlin.ext.asBsonObjectId
import io.realm.kotlin.internal.RealmAnyByteArrayOperator
import io.realm.kotlin.internal.RealmAnyImpl
import io.realm.kotlin.internal.RealmAnyObjectOperator
import io.realm.kotlin.internal.RealmAnyPrimitiveOperator
import org.mongodb.kbson.BsonObjectId
import kotlin.reflect.KClass

/**
 * TODO
 */
public interface RealmAny {

    public enum class Type {
        INT, BOOLEAN, STRING, BYTE_ARRAY, REALM_INSTANT, FLOAT, DOUBLE, OBJECT_ID, REALM_UUID, REALM_OBJECT
    }

    // TODO use RealmStorageType? How do we avoid problems with RealmStorageType.REALM_ANY?
    public val type: Type

    public fun asShort(): Short
    public fun asInt(): Int
    public fun asByte(): Byte
    public fun asChar(): Char
    public fun asLong(): Long
    public fun asBoolean(): Boolean
    public fun asString(): String
    public fun asFloat(): Float
    public fun asDouble(): Double
    public fun asRealmObjectId(): ObjectId
    public fun asObjectId(): BsonObjectId
    public fun asByteArray(): ByteArray
    public fun asRealmInstant(): RealmInstant
    public fun asRealmUUID(): RealmUUID
    public fun <T : BaseRealmObject> asRealmObject(clazz: KClass<T>): T

    /**
     * Two [RealmAny] instances are equals if and only if their contents are the equal.
     */
    override fun equals(other: Any?): Boolean

    public companion object {
        public fun create(value: Short): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.INT, value.toLong()))

        public fun create(value: Int): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.INT, value.toLong()))

        public fun create(value: Byte): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.INT, value.toLong()))

        public fun create(value: Char): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.INT, value.code.toLong()))

        public fun create(value: Long): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.INT, value))

        public fun create(value: Boolean): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.BOOLEAN, value))

        public fun create(value: String): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.STRING, value))

        public fun create(value: Float): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.FLOAT, value))

        public fun create(value: Double): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.DOUBLE, value))

        public fun create(value: ObjectId): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.OBJECT_ID, value.asBsonObjectId()))

        public fun create(value: BsonObjectId): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.OBJECT_ID, value))

        public fun create(value: ByteArray): RealmAny =
            RealmAnyImpl(RealmAnyByteArrayOperator(Type.BYTE_ARRAY, value))

        public fun create(value: RealmInstant): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.REALM_INSTANT, value))

        public fun create(value: RealmUUID): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.REALM_UUID, value))

        public fun <T : BaseRealmObject> create(value: T): RealmAny =
            RealmAnyImpl(RealmAnyObjectOperator(Type.REALM_OBJECT, value))

//        public operator fun invoke(value: Short): RealmAny =
//            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.INT, value.toLong()))
//
//        public operator fun invoke(value: Int): RealmAny =
//            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.INT, value.toLong()))
//
//        public operator fun invoke(value: Byte): RealmAny =
//            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.INT, value.toLong()))
//
//        public operator fun invoke(value: Char): RealmAny =
//            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.INT, value.code.toLong()))
//
//        public operator fun invoke(value: Long): RealmAny =
//            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.INT, value))
//
//        public operator fun invoke(value: Boolean): RealmAny =
//            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.BOOLEAN, value))
//
//        public operator fun invoke(value: String): RealmAny =
//            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.STRING, value))
//
//        public operator fun invoke(value: Float): RealmAny =
//            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.FLOAT, value))
//
//        public operator fun invoke(value: Double): RealmAny =
//            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.DOUBLE, value))
//
//        public operator fun invoke(value: ObjectId): RealmAny =
//            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.OBJECT_ID, value.asBsonObjectId()))
//
//        public operator fun invoke(value: BsonObjectId): RealmAny =
//            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.OBJECT_ID, value))
//
//        public operator fun invoke(value: ByteArray): RealmAny =
//            RealmAnyImpl(RealmAnyByteArrayOperator(Type.BYTE_ARRAY, value))
//
//        public operator fun invoke(value: RealmInstant): RealmAny =
//            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.REALM_INSTANT, value))
//
//        public operator fun invoke(value: RealmUUID): RealmAny =
//            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.REALM_UUID, value))
//
//        public operator fun <T : BaseRealmObject> invoke(value: T): RealmAny =
//            RealmAnyImpl(RealmAnyObjectOperator(Type.REALM_OBJECT, value))
    }
}

/**
 * TODO
 */
public inline fun <reified T : BaseRealmObject> RealmAny.asRealmObject(): T =
    asRealmObject(T::class)
