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
import io.realm.kotlin.types.EmbeddedRealmObject
import org.mongodb.kbson.BsonObjectId
import kotlin.reflect.KClass

/**
 * `RealmAny` is used to represent a polymorphic Realm value.
 *
 * At any particular moment an instance of this class stores a definite value of a definite type.
 * If, for instance, that is a `Double` value, you may call [asDouble] to extract that value. You
 * may call [type] to discover what type of value is currently stored. Calling `asDouble` on an
 * instance that does not store a `Double` value would raise an [IllegalStateException].
 *
 * `RealmAny` behaves like a value type on all the supported types except on Realm objects. It means
 * that Realm will not persist any change to the `RealmAny` value except when the type is
 * `REALM_OBJECT`. When a `RealmAny` holds a [RealmObject], it just holds the reference to it, not a
 * copy of the object. So modifications to the Realm object are reflected in the RealmAny value,
 * including if the object is deleted. Because `RealmAny` instances are immutable, a new instance is
 * needed to update a `RealmAny` attribute.
 * ```
 *      anObject.realmAnyField = RealmAny.create(42.0)
 *      anObject.realmAnyField = RealmAny.create("Hello")
 * ```
 * It is crucial to understand that the act of extracting a value of a particular type requires
 * definite knowledge about the stored type. Calling a getter method for any particular type that
 * is not the same type as the stored value, results in an exception being thrown.
 *
 * Our recommendation to handle the RealmAny polymorphism is to write a conditional expression with
 * `when` around the `RealmAny` type and its inner value class.
 * ```
 *      val realmAny = anObject.realmAnyField
 *      when (realmAny.type) {
 *          INT -> doSomething(realmAny.asInt()) // or as any other primitive derived from 'Number'
 *          BOOLEAN -> doSomething(realmAny.asBoolean())
 *          STRING -> doSomething(realmAny.asString())
 *          BYTE_ARRAY -> doSomething(realmAny.asByteArray())
 *          REALM_INSTANT -> doSomething(realmAny.asRealmInstant())
 *          FLOAT -> doSomething(realmAny.asFloat())
 *          DOUBLE -> doSomething(realmAny.asDouble())
 *          OBJECT_ID -> doSomething(realmAny.asObjectId())
 *          REALM_UUID -> doSomething(realmAny.asRealmUUID())
 *          REALM_OBJECT -> doSomething(realmAny.asRealmObject<MyRealmObject>())
 *      }
 * ```
 * [Short], [Int], [Byte], [Char] and [Long] values are converted internally to `int64_t` values.
 * One has to be aware of this when comparing `RealmAny` values generated from different numeral
 * types, for example:
 * ```
 *      RealmAny.create(42.toShort()) == RealmAny.create(42.toByte()) // true
 * ```
 * `RealmAny` cannot store `null` values, although `RealmAny` properties **must** be declared
 * nullable:
 * ```
 *      class Warehouse {
 *          var nonNullableStorage: RealmAny = RealmAny.create("invalid") // This is NOT allowed
 *          var nullableStorage: RealmAny? = RealmAny.create("valid") // Property MUST be nullable
 *          var defaultNullStorage: RealmAny? = null // Property MUST be nullable
 *      }
 *
 *      warehouse.nullableStorage = RealmAny.create(22)
 *      warehouse.nullableStorage = null // Assign null directly to the property
 * ```
 * `RealmAny` cannot store [EmbeddedRealmObject]s.
 *
 * TODO missing tests for this section
 * `RealmAny` values can be sorted. The sorting order used between different `RealmAny` types,
 * from lowest to highest, is:
 * - Boolean
 * - Byte/Short/Integer/Long/Float/Double/Decimal128
 * - byte[]/String
 * - Date
 * - ObjectId
 * - UUID
 * - RealmObject
 *
 * TODO missing comments about query aggregators
 */
public interface RealmAny {

    /**
     * Supported Realm data types that can be stored in a `RealmAny` instance.
     */
    public enum class Type {
        INT, BOOLEAN, STRING, BYTE_ARRAY, REALM_INSTANT, FLOAT, DOUBLE, OBJECT_ID, REALM_UUID, REALM_OBJECT
    }

    /**
     * Returns the [Type] of the `RealmAny` instance.
     */
    // TODO use RealmStorageType? How do we avoid problems with RealmStorageType.REALM_ANY?
    public val type: Type

    /**
     * Returns the value stored by this `RealmAny` as a [Short]. `RealmAny` instances created using
     * [Short], [Int], [Byte], [Char] or [Long] values can be converted to any of these types
     * safely.
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Short`.
     */
    public fun asShort(): Short

    /**
     * Returns the value from this `RealmAny` as an [Int]. `RealmAny` instances created using
     * [Short], [Int], [Byte], [Char] or [Long] values can be converted to any of these types
     * safely.
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Int`.
     */
    public fun asInt(): Int

    /**
     * Returns the value from this `RealmAny` as a [Byte]. `RealmAny` instances created using
     * [Short], [Int], [Byte], [Char] or [Long] values can be converted to any of these types
     * safely.
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Byte`.
     */
    public fun asByte(): Byte

    /**
     * Returns the value from this `RealmAny` as a [Char]. `RealmAny` instances created using
     * [Short], [Int], [Byte], [Char] or [Long] values can be converted to any of these types
     * safely.
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Char`.
     */
    public fun asChar(): Char

    /**
     * Returns the value from this `RealmAny` as a [Long]. `RealmAny` instances created using
     * [Short], [Int], [Byte], [Char] or [Long] values can be converted to any of these types
     * safely.
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Long`.
     */
    public fun asLong(): Long

    /**
     * Returns the value from this `RealmAny` as a [Boolean].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Boolean`.
     */
    public fun asBoolean(): Boolean

    /**
     * Returns the value from this `RealmAny` as a [String].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `String`.
     */
    public fun asString(): String

    /**
     * Returns the value from this `RealmAny` as a [Float].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Float`.
     */
    public fun asFloat(): Float

    /**
     * Returns the value from this `RealmAny` as a [Double].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Double`.
     */
    public fun asDouble(): Double

    /**
     * Returns the value from this `RealmAny` as an [ObjectId].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `ObjectId`.
     */
    @Deprecated("Use the BSON ObjectId variant instead", ReplaceWith("RealmAny.asObjectId"))
    public fun asRealmObjectId(): ObjectId

    /**
     * Returns the value from this `RealmAny` as a [BsonObjectId].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to
     * `BsonObjectId`.
     */
    public fun asObjectId(): BsonObjectId

    /**
     * Returns the value from this `RealmAny` as a [ByteArray].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to
     * `ByteArray`.
     */
    public fun asByteArray(): ByteArray

    /**
     * Returns the value from this `RealmAny` as a [RealmInstant].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to
     * `RealmInstant`.
     */
    public fun asRealmInstant(): RealmInstant

    /**
     * Returns the value from this `RealmAny` as a [RealmUUID].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to
     * `RealmUUID`.
     */
    public fun asRealmUUID(): RealmUUID

    /**
     * Returns the value from this RealmAny as a [RealmObject] of type [T].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `T`.
     */
    public fun <T : RealmObject> asRealmObject(clazz: KClass<T>): T

    /**
     * Two [RealmAny] instances are equal if and only if their types and contents are the equal.
     */
    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int

    public companion object {
        /**
         * Creates an unmanaged `RealmAny` instance from a [Short] value.
         */
        public fun create(value: Short): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.INT, value.toLong()))

        /**
         * Creates an unmanaged `RealmAny` instance from an [Int] value.
         */
        public fun create(value: Int): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.INT, value.toLong()))

        /**
         * Creates an unmanaged `RealmAny` instance from a [Byte] value.
         */
        public fun create(value: Byte): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.INT, value.toLong()))

        /**
         * Creates an unmanaged `RealmAny` instance from a [Char] value.
         */
        public fun create(value: Char): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.INT, value.code.toLong()))

        /**
         * Creates an unmanaged `RealmAny` instance from a [Long] value.
         */
        public fun create(value: Long): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.INT, value))

        /**
         * Creates an unmanaged `RealmAny` instance from a [Boolean] value.
         */
        public fun create(value: Boolean): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.BOOLEAN, value))

        /**
         * Creates an unmanaged `RealmAny` instance from a [String] value.
         */
        public fun create(value: String): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.STRING, value))

        /**
         * Creates an unmanaged `RealmAny` instance from a [Float] value.
         */
        public fun create(value: Float): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.FLOAT, value))

        /**
         * Creates an unmanaged `RealmAny` instance from a [Double] value.
         */
        public fun create(value: Double): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.DOUBLE, value))

        /**
         * Creates an unmanaged `RealmAny` instance from an [ObjectId] value.
         */
        @Deprecated("Use the BSON ObjectId variant instead", ReplaceWith("RealmAny.create"))
        public fun create(value: ObjectId): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.OBJECT_ID, value.asBsonObjectId()))

        /**
         * Creates an unmanaged `RealmAny` instance from a [BsonObjectId] value.
         */
        public fun create(value: BsonObjectId): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.OBJECT_ID, value))

        /**
         * Creates an unmanaged `RealmAny` instance from a [ByteArray] value.
         */
        public fun create(value: ByteArray): RealmAny =
            RealmAnyImpl(RealmAnyByteArrayOperator(Type.BYTE_ARRAY, value))

        /**
         * Creates an unmanaged `RealmAny` instance from a [RealmInstant] value.
         */
        public fun create(value: RealmInstant): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.REALM_INSTANT, value))

        /**
         * Creates an unmanaged `RealmAny` instance from a [RealmUUID] value.
         */
        public fun create(value: RealmUUID): RealmAny =
            RealmAnyImpl(RealmAnyPrimitiveOperator(Type.REALM_UUID, value))

        /**
         * Creates an unmanaged `RealmAny` instance from a [RealmObject] value.
         */
        public fun <T : RealmObject> create(value: T): RealmAny =
            RealmAnyImpl(RealmAnyObjectOperator(Type.REALM_OBJECT, value))
    }
}

/**
 * Creates an unmanaged `RealmAny` instance from a [RealmObject] value.
 *
 * Reified convenience wrapper for the [RealmAny.create] for [RealmObject]s.
 */
public inline fun <reified T : RealmObject> RealmAny.asRealmObject(): T =
    asRealmObject(T::class)
