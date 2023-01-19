package io.realm.kotlin.types

import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.internal.RealmAnyImpl
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.types.RealmAny.Companion.create
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
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
 * that Realm will not persist any change to the actual `RealmAny` value. If a `RealmAny` holds a
 * [RealmObject], it just holds the reference to it, not a copy of the object. Because `RealmAny`
 * instances are immutable, a new instance is needed to update a `RealmAny` attribute.
 * ```
 *      anObject.realmAnyField = RealmAny.create(42.0)
 *      anObject.realmAnyField = RealmAny.create("Hello")
 *      anObject.realmAnyField = RealmAny.create(MyRealmObject())
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
 * [DynamicRealmObject]s and [DynamicMutableRealmObject]s can be used inside `RealmAny` with
 * the corresponding [create] function for `DynamicRealmObject`s and with [asRealmObject] using
 * either `DynamicRealmObject` or `DynamicMutableRealmObject` as the generic parameter.
 *
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
 * `RealmAny` properties can be aggregated. [RealmQuery.max] and [RealmQuery.min] produce results
 * based the sorting criteria mentioned above and thus the output type will be a `RealmAny` instance
 * containing the corresponding polymorphic value. [RealmQuery.sum] computes the sum of all
 * numerical values, ignoring other data types, and returns a [Decimal128] result - `SUM`s cannot be
 * typed-coerced, that is, queries like this are not allowed:
 * ```
 *      realm.query<Warehouse>()
 *          .sum<Float>("nullableStorage") // type CANNOT be coerced to Float
 * ```
 */
public interface RealmAny {

    /**
     * Supported Realm data types that can be stored in a `RealmAny` instance.
     */
    public enum class Type {
        INT, BOOL, STRING, BINARY, TIMESTAMP, FLOAT, DOUBLE, DECIMAL128, OBJECT_ID, UUID, OBJECT
    }

    /**
     * Returns the [Type] of the `RealmAny` instance.
     */
    // TODO use RealmStorageType? How do we avoid problems with RealmStorageType.REALM_ANY?
    public val type: Type

    /**
     * Returns the value from this `RealmAny` as a [Short]. `RealmAny` instances created using
     * [Short], [Int], [Byte], [Char] or [Long] values can be converted to any of these types
     * safely, although overflow might occur, for example, if the value to be output as a `Short`
     * is greater than [Short.MAX_VALUE].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Short`.
     * @throws [ArithmeticException] if the stored value cannot be coerced to another numeric type.
     */
    public fun asShort(): Short

    /**
     * Returns the value from this `RealmAny` as an [Int]. `RealmAny` instances created using
     * [Short], [Int], [Byte], [Char] or [Long] values can be converted to any of these types
     * safely, although overflow might occur, for example, if the value to be output as a `Short`
     * is greater than [Int.MAX_VALUE].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Int`.
     * @throws [ArithmeticException] if the stored value cannot be coerced to another numeric type.
     */
    public fun asInt(): Int

    /**
     * Returns the value from this `RealmAny` as a [Byte]. `RealmAny` instances created using
     * [Short], [Int], [Byte], [Char] or [Long] values can be converted to any of these types
     * safely, although overflow might occur, for example, if the value to be output as a `Short`
     * is greater than [Byte.MAX_VALUE].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Byte`.
     * @throws [ArithmeticException] if the stored value cannot be coerced to another numeric type.
     */
    public fun asByte(): Byte

    /**
     * Returns the value from this `RealmAny` as a [Char]. `RealmAny` instances created using
     * [Short], [Int], [Byte], [Char] or [Long] values can be converted to any of these types
     * safely, although overflow might occur, for example, if the value to be output as a `Short`
     * is greater than [Char.MAX_VALUE].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Char`.
     * @throws [ArithmeticException] if the stored value cannot be coerced to another numeric type.
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
     * Returns the value from this `RealmAny` as a [Decimal128].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `Decimal128`.
     */
    public fun asDecimal128(): Decimal128

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
     * Returns the value from this RealmAny as a [BaseRealmObject] of type [T].
     * @throws [IllegalStateException] if the stored value cannot be safely converted to `T`.
     */
    public fun <T : BaseRealmObject> asRealmObject(clazz: KClass<T>): T

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
            RealmAnyImpl(Type.INT, Long::class, value)

        /**
         * Creates an unmanaged `RealmAny` instance from an [Int] value.
         */
        public fun create(value: Int): RealmAny =
            RealmAnyImpl(Type.INT, Long::class, value)

        /**
         * Creates an unmanaged `RealmAny` instance from a [Byte] value.
         */
        public fun create(value: Byte): RealmAny =
            RealmAnyImpl(Type.INT, Long::class, value)

        /**
         * Creates an unmanaged `RealmAny` instance from a [Char] value.
         */
        public fun create(value: Char): RealmAny =
            RealmAnyImpl(Type.INT, Long::class, value)

        /**
         * Creates an unmanaged `RealmAny` instance from a [Long] value.
         */
        public fun create(value: Long): RealmAny =
            RealmAnyImpl(Type.INT, Long::class, value)

        /**
         * Creates an unmanaged `RealmAny` instance from a [Boolean] value.
         */
        public fun create(value: Boolean): RealmAny =
            RealmAnyImpl(Type.BOOL, Boolean::class, value)

        /**
         * Creates an unmanaged `RealmAny` instance from a [String] value.
         */
        public fun create(value: String): RealmAny =
            RealmAnyImpl(Type.STRING, String::class, value)

        /**
         * Creates an unmanaged `RealmAny` instance from a [Float] value.
         */
        public fun create(value: Float): RealmAny =
            RealmAnyImpl(Type.FLOAT, Float::class, value)

        /**
         * Creates an unmanaged `RealmAny` instance from a [Double] value.
         */
        public fun create(value: Double): RealmAny =
            RealmAnyImpl(Type.DOUBLE, Double::class, value)

        /**
         * Creates an unmanaged `RealmAny` instance from a [Decimal128] value.
         */
        public fun create(value: Decimal128): RealmAny =
            RealmAnyImpl(Type.DECIMAL128, Decimal128::class, value)

        /**
         * Creates an unmanaged `RealmAny` instance from a [BsonObjectId] value.
         */
        public fun create(value: BsonObjectId): RealmAny =
            RealmAnyImpl(Type.OBJECT_ID, BsonObjectId::class, value)

        /**
         * Creates an unmanaged `RealmAny` instance from a [ByteArray] value.
         */
        public fun create(value: ByteArray): RealmAny =
            RealmAnyImpl(Type.BINARY, ByteArray::class, value)

        /**
         * Creates an unmanaged `RealmAny` instance from a [RealmInstant] value.
         */
        public fun create(value: RealmInstant): RealmAny =
            RealmAnyImpl(Type.TIMESTAMP, RealmInstant::class, value)

        /**
         * Creates an unmanaged `RealmAny` instance from a [RealmUUID] value.
         */
        public fun create(value: RealmUUID): RealmAny =
            RealmAnyImpl(Type.UUID, RealmUUID::class, value)

        /**
         * Creates an unmanaged `RealmAny` instance from a [RealmObject] value and its
         * corresponding [KClass].
         */
        public fun <T : RealmObject> create(value: T, clazz: KClass<out T>): RealmAny =
            RealmAnyImpl(Type.OBJECT, clazz, value)

        /**
         * Creates an unmanaged `RealmAny` instance from a [RealmObject] value.
         */
        public inline fun <reified T : RealmObject> create(realmObject: T): RealmAny =
            create(realmObject, T::class)

        /**
         * Creates an unmanaged `RealmAny` instance from a [DynamicRealmObject] value.
         */
        public fun create(realmObject: DynamicRealmObject): RealmAny =
            RealmAnyImpl(Type.OBJECT, DynamicRealmObject::class, realmObject)
    }
}
