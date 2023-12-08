package io.realm.kotlin.entities.adapters

import io.realm.kotlin.ext.asBsonObjectId
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmTypeAdapter
import io.realm.kotlin.types.RealmUUID
import io.realm.kotlin.types.annotations.TypeAdapter
import org.mongodb.kbson.BsonDecimal128
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128


@Suppress("MagicNumber")
class AllTypes : RealmObject {

    @TypeAdapter(StringAdapter::class)
    var stringField: String = "Realm"

    @TypeAdapter(BooleanAdapter::class)
    var booleanField: Boolean = true

    @TypeAdapter(FloatAdapter::class)
    var floatField: Float = 3.14f

    @TypeAdapter(DoubleAdapter::class)
    var doubleField: Double = 1.19840122

    @TypeAdapter(Decimal128Adapter::class)
    var decimal128Field: Decimal128 = BsonDecimal128("1.8446744073709551618E-6157")

    @TypeAdapter(TimestampAdapter::class)
    var timestampField: RealmInstant = RealmInstant.from(100, 1000)

    @TypeAdapter(ObjectIdAdapter::class)
    var objectIdField: ObjectId = ObjectId.from("507f1f77bcf86cd799439011")

    @TypeAdapter(BsonObjectIdAdapter::class)
    var bsonObjectIdField: BsonObjectId = BsonObjectId("507f1f77bcf86cd799439011")

    @TypeAdapter(UuidAdapter::class)
    var uuidField: RealmUUID = RealmUUID.from("46423f1b-ce3e-4a7e-812f-004cf9c42d76")

    @TypeAdapter(BinaryAdapter::class)
    var binaryField: ByteArray = byteArrayOf(42)

    @TypeAdapter(NullableStringAdapter::class)
    var nullableStringField: String? = null

    @TypeAdapter(NullableBooleanAdapter::class)
    var nullableBooleanField: Boolean? = null

    @TypeAdapter(NullableFloatAdapter::class)
    var nullableFloatField: Float? = null

    @TypeAdapter(NullableDoubleAdapter::class)
    var nullableDoubleField: Double? = null

    @TypeAdapter(NullableDecimal128Adapter::class)
    var nullableDecimal128Field: Decimal128? = null

    @TypeAdapter(NullableTimestampAdapter::class)
    var nullableTimestampField: RealmInstant? = null

    @TypeAdapter(NullableObjectIdAdapter::class)
    var nullableObjectIdField: ObjectId? = null

    @TypeAdapter(NullableBsonObjectIdAdapter::class)
    var nullableBsonObjectIdField: BsonObjectId? = null

    @TypeAdapter(NullableUuidAdapter::class)
    var nullableUuidField: RealmUUID? = null

    @TypeAdapter(NullableBinaryAdapter::class)
    var nullableBinaryField: ByteArray? = null

    @TypeAdapter(RealmAnyAdapter::class)
    var nullableRealmAnyField: RealmAny? = null

//    var nullableObject: Sample? = null
//
//    var stringListField: RealmList<String> = realmListOf()
//    var byteListField: RealmList<Byte> = realmListOf()
//    var charListField: RealmList<Char> = realmListOf()
//    var shortListField: RealmList<Short> = realmListOf()
//    var intListField: RealmList<Int> = realmListOf()
//    var longListField: RealmList<Long> = realmListOf()
//    var booleanListField: RealmList<Boolean> = realmListOf()
//    var floatListField: RealmList<Float> = realmListOf()
//    var doubleListField: RealmList<Double> = realmListOf()
//    var timestampListField: RealmList<RealmInstant> = realmListOf()
//    var objectIdListField: RealmList<ObjectId> = realmListOf()
//    var bsonObjectIdListField: RealmList<BsonObjectId> = realmListOf()
//    var uuidListField: RealmList<RealmUUID> = realmListOf()
//    var binaryListField: RealmList<ByteArray> = realmListOf()
//    var decimal128ListField: RealmList<Decimal128> = realmListOf()
//    var objectListField: RealmList<Sample> = realmListOf()
//
//    var nullableStringListField: RealmList<String?> = realmListOf()
//    var nullableByteListField: RealmList<Byte?> = realmListOf()
//    var nullableCharListField: RealmList<Char?> = realmListOf()
//    var nullableShortListField: RealmList<Short?> = realmListOf()
//    var nullableIntListField: RealmList<Int?> = realmListOf()
//    var nullableLongListField: RealmList<Long?> = realmListOf()
//    var nullableBooleanListField: RealmList<Boolean?> = realmListOf()
//    var nullableFloatListField: RealmList<Float?> = realmListOf()
//    var nullableDoubleListField: RealmList<Double?> = realmListOf()
//    var nullableTimestampListField: RealmList<RealmInstant?> = realmListOf()
//    var nullableObjectIdListField: RealmList<ObjectId?> = realmListOf()
//    var nullableBsonObjectIdListField: RealmList<BsonObjectId?> = realmListOf()
//    var nullableUUIDListField: RealmList<RealmUUID?> = realmListOf()
//    var nullableBinaryListField: RealmList<ByteArray?> = realmListOf()
//    var nullableDecimal128ListField: RealmList<Decimal128?> = realmListOf()
//    var nullableRealmAnyListField: RealmList<RealmAny?> = realmListOf()
//
//    var stringSetField: RealmSet<String> = realmSetOf()
//    var byteSetField: RealmSet<Byte> = realmSetOf()
//    var charSetField: RealmSet<Char> = realmSetOf()
//    var shortSetField: RealmSet<Short> = realmSetOf()
//    var intSetField: RealmSet<Int> = realmSetOf()
//    var longSetField: RealmSet<Long> = realmSetOf()
//    var booleanSetField: RealmSet<Boolean> = realmSetOf()
//    var floatSetField: RealmSet<Float> = realmSetOf()
//    var doubleSetField: RealmSet<Double> = realmSetOf()
//    var timestampSetField: RealmSet<RealmInstant> = realmSetOf()
//    var objectIdSetField: RealmSet<ObjectId> = realmSetOf()
//    var bsonObjectIdSetField: RealmSet<BsonObjectId> = realmSetOf()
//    var uuidSetField: RealmSet<RealmUUID> = realmSetOf()
//    var binarySetField: RealmSet<ByteArray> = realmSetOf()
//    var decimal128SetField: RealmSet<Decimal128> = realmSetOf()
//    var objectSetField: RealmSet<Sample> = realmSetOf()
//
//    var nullableStringSetField: RealmSet<String?> = realmSetOf()
//    var nullableByteSetField: RealmSet<Byte?> = realmSetOf()
//    var nullableCharSetField: RealmSet<Char?> = realmSetOf()
//    var nullableShortSetField: RealmSet<Short?> = realmSetOf()
//    var nullableIntSetField: RealmSet<Int?> = realmSetOf()
//    var nullableLongSetField: RealmSet<Long?> = realmSetOf()
//    var nullableBooleanSetField: RealmSet<Boolean?> = realmSetOf()
//    var nullableFloatSetField: RealmSet<Float?> = realmSetOf()
//    var nullableDoubleSetField: RealmSet<Double?> = realmSetOf()
//    var nullableTimestampSetField: RealmSet<RealmInstant?> = realmSetOf()
//    var nullableObjectIdSetField: RealmSet<ObjectId?> = realmSetOf()
//    var nullableBsonObjectIdSetField: RealmSet<BsonObjectId?> = realmSetOf()
//    var nullableUUIDSetField: RealmSet<RealmUUID?> = realmSetOf()
//    var nullableBinarySetField: RealmSet<ByteArray?> = realmSetOf()
//    var nullableDecimal128SetField: RealmSet<Decimal128?> = realmSetOf()
//    var nullableRealmAnySetField: RealmSet<RealmAny?> = realmSetOf()
//
//    var stringDictionaryField: RealmDictionary<String> = realmDictionaryOf()
//    var byteDictionaryField: RealmDictionary<Byte> = realmDictionaryOf()
//    var charDictionaryField: RealmDictionary<Char> = realmDictionaryOf()
//    var shortDictionaryField: RealmDictionary<Short> = realmDictionaryOf()
//    var intDictionaryField: RealmDictionary<Int> = realmDictionaryOf()
//    var longDictionaryField: RealmDictionary<Long> = realmDictionaryOf()
//    var booleanDictionaryField: RealmDictionary<Boolean> = realmDictionaryOf()
//    var floatDictionaryField: RealmDictionary<Float> = realmDictionaryOf()
//    var doubleDictionaryField: RealmDictionary<Double> = realmDictionaryOf()
//    var timestampDictionaryField: RealmDictionary<RealmInstant> = realmDictionaryOf()
//    var objectIdDictionaryField: RealmDictionary<ObjectId> = realmDictionaryOf()
//    var bsonObjectIdDictionaryField: RealmDictionary<BsonObjectId> = realmDictionaryOf()
//    var uuidDictionaryField: RealmDictionary<RealmUUID> = realmDictionaryOf()
//    var binaryDictionaryField: RealmDictionary<ByteArray> = realmDictionaryOf()
//    var decimal128DictionaryField: RealmDictionary<Decimal128> = realmDictionaryOf()
//
//    var nullableStringDictionaryField: RealmDictionary<String?> = realmDictionaryOf()
//    var nullableByteDictionaryField: RealmDictionary<Byte?> = realmDictionaryOf()
//    var nullableCharDictionaryField: RealmDictionary<Char?> = realmDictionaryOf()
//    var nullableShortDictionaryField: RealmDictionary<Short?> = realmDictionaryOf()
//    var nullableIntDictionaryField: RealmDictionary<Int?> = realmDictionaryOf()
//    var nullableLongDictionaryField: RealmDictionary<Long?> = realmDictionaryOf()
//    var nullableBooleanDictionaryField: RealmDictionary<Boolean?> = realmDictionaryOf()
//    var nullableFloatDictionaryField: RealmDictionary<Float?> = realmDictionaryOf()
//    var nullableDoubleDictionaryField: RealmDictionary<Double?> = realmDictionaryOf()
//    var nullableTimestampDictionaryField: RealmDictionary<RealmInstant?> = realmDictionaryOf()
//    var nullableObjectIdDictionaryField: RealmDictionary<ObjectId?> = realmDictionaryOf()
//    var nullableBsonObjectIdDictionaryField: RealmDictionary<BsonObjectId?> = realmDictionaryOf()
//    var nullableUUIDDictionaryField: RealmDictionary<RealmUUID?> = realmDictionaryOf()
//    var nullableBinaryDictionaryField: RealmDictionary<ByteArray?> = realmDictionaryOf()
//    var nullableDecimal128DictionaryField: RealmDictionary<Decimal128?> = realmDictionaryOf()
//    var nullableRealmAnyDictionaryField: RealmDictionary<RealmAny?> = realmDictionaryOf()
//    var nullableObjectDictionaryFieldNotNull: RealmDictionary<Sample?> = realmDictionaryOf()
//    var nullableObjectDictionaryFieldNull: RealmDictionary<Sample?> = realmDictionaryOf()
//
//    val objectBacklinks by backlinks(Sample::nullableObject)
//    val listBacklinks by backlinks(Sample::objectListField)
//    val setBacklinks by backlinks(Sample::objectSetField)
//
//    @PersistedName("persistedStringField")
//    var publicStringField = "Realm"
//
//    // For verification that references inside class is also using our modified accessors and are
//    // not optimized to use the backing field directly.
//    fun stringFieldGetter(): String {
//        return stringField
//    }
//
//    fun stringFieldSetter(s: String) {
//        stringField = s
//    }

    companion object {
        // Empty object required by SampleTests
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AllTypes

        if (stringField != other.stringField) return false
        if (booleanField != other.booleanField) return false
        if (floatField != other.floatField) return false
        if (doubleField != other.doubleField) return false
        if (decimal128Field != other.decimal128Field) return false
        if (timestampField != other.timestampField) return false
        if (objectIdField != other.objectIdField) return false
        if (bsonObjectIdField != other.bsonObjectIdField) return false
        if (uuidField != other.uuidField) return false
        if (!binaryField.contentEquals(other.binaryField)) return false
        if (nullableStringField != other.nullableStringField) return false
        if (nullableBooleanField != other.nullableBooleanField) return false
        if (nullableFloatField != other.nullableFloatField) return false
        if (nullableDoubleField != other.nullableDoubleField) return false
        if (nullableDecimal128Field != other.nullableDecimal128Field) return false
        if (nullableTimestampField != other.nullableTimestampField) return false
        if (nullableObjectIdField != other.nullableObjectIdField) return false
        if (nullableBsonObjectIdField != other.nullableBsonObjectIdField) return false
        if (nullableUuidField != other.nullableUuidField) return false
        if (nullableBinaryField != null) {
            if (other.nullableBinaryField == null) return false
            if (!nullableBinaryField.contentEquals(other.nullableBinaryField)) return false
        } else if (other.nullableBinaryField != null) return false
        if (nullableRealmAnyField != other.nullableRealmAnyField) return false

        return true
    }

    override fun hashCode(): Int {
        var result = stringField.hashCode()
        result = 31 * result + booleanField.hashCode()
        result = 31 * result + floatField.hashCode()
        result = 31 * result + doubleField.hashCode()
        result = 31 * result + decimal128Field.hashCode()
        result = 31 * result + timestampField.hashCode()
        result = 31 * result + objectIdField.hashCode()
        result = 31 * result + bsonObjectIdField.hashCode()
        result = 31 * result + uuidField.hashCode()
        result = 31 * result + binaryField.contentHashCode()
        result = 31 * result + (nullableStringField?.hashCode() ?: 0)
        result = 31 * result + (nullableBooleanField?.hashCode() ?: 0)
        result = 31 * result + (nullableFloatField?.hashCode() ?: 0)
        result = 31 * result + (nullableDoubleField?.hashCode() ?: 0)
        result = 31 * result + (nullableDecimal128Field?.hashCode() ?: 0)
        result = 31 * result + (nullableTimestampField?.hashCode() ?: 0)
        result = 31 * result + (nullableObjectIdField?.hashCode() ?: 0)
        result = 31 * result + (nullableBsonObjectIdField?.hashCode() ?: 0)
        result = 31 * result + (nullableUuidField?.hashCode() ?: 0)
        result = 31 * result + (nullableBinaryField?.contentHashCode() ?: 0)
        result = 31 * result + (nullableRealmAnyField?.hashCode() ?: 0)
        return result
    }

}

// Passthrough converters
object StringAdapter : RealmTypeAdapter<String, String> {
    override fun fromRealm(realmValue: String): String = realmValue.toString()

    override fun toRealm(value: String): String = value.toString()
}

object BooleanAdapter : RealmTypeAdapter<Boolean, Boolean> {
    override fun fromRealm(realmValue: Boolean): Boolean = realmValue.not().not()

    override fun toRealm(value: Boolean): Boolean = value.not().not()
}

object FloatAdapter : RealmTypeAdapter<Float, Float> {
    override fun fromRealm(realmValue: Float): Float = realmValue.toFloat()

    override fun toRealm(value: Float): Float = value.toFloat()
}

object DoubleAdapter : RealmTypeAdapter<Double, Double> {
    override fun fromRealm(realmValue: Double): Double = realmValue.toDouble()

    override fun toRealm(value: Double): Double = value.toDouble()
}

object Decimal128Adapter : RealmTypeAdapter<Decimal128, Decimal128> {
    override fun fromRealm(realmValue: Decimal128): Decimal128 = Decimal128.fromIEEE754BIDEncoding(realmValue.high, realmValue.low)

    override fun toRealm(value: Decimal128): Decimal128 = Decimal128.fromIEEE754BIDEncoding(value.high, value.low)
}

object TimestampAdapter : RealmTypeAdapter<RealmInstant, RealmInstant> {
    override fun fromRealm(realmValue: RealmInstant): RealmInstant = RealmInstant.from(realmValue.epochSeconds, realmValue.nanosecondsOfSecond)

    override fun toRealm(value: RealmInstant): RealmInstant = RealmInstant.from(value.epochSeconds, value.nanosecondsOfSecond)
}

object ObjectIdAdapter : RealmTypeAdapter<ObjectId, ObjectId> {
    override fun fromRealm(realmValue: ObjectId): ObjectId = ObjectId.from(realmValue.asBsonObjectId().toHexString())

    override fun toRealm(value: ObjectId): ObjectId = ObjectId.from(value.asBsonObjectId().toHexString())
}

object BsonObjectIdAdapter : RealmTypeAdapter<BsonObjectId, BsonObjectId> {
    override fun fromRealm(realmValue: BsonObjectId): BsonObjectId = BsonObjectId(realmValue.toHexString())

    override fun toRealm(value: BsonObjectId): BsonObjectId = BsonObjectId(value.toHexString())
}

object UuidAdapter : RealmTypeAdapter<RealmUUID, RealmUUID> {
    override fun fromRealm(realmValue: RealmUUID): RealmUUID = RealmUUID.from(realmValue.bytes)

    override fun toRealm(value: RealmUUID): RealmUUID = RealmUUID.from(value.bytes)
}

object BinaryAdapter : RealmTypeAdapter<ByteArray, ByteArray> {
    override fun fromRealm(realmValue: ByteArray): ByteArray = realmValue.copyOf()

    override fun toRealm(value: ByteArray): ByteArray = value.copyOf()
}


object NullableStringAdapter : RealmTypeAdapter<String?, String?> {
    override fun fromRealm(realmValue: String?): String? = realmValue?.toString()

    override fun toRealm(value: String?): String? = value?.toString()
}

object NullableBooleanAdapter : RealmTypeAdapter<Boolean?, Boolean?> {
    override fun fromRealm(realmValue: Boolean?): Boolean? = realmValue?.not()?.not()

    override fun toRealm(value: Boolean?): Boolean? = value?.not()?.not()
}

object NullableFloatAdapter : RealmTypeAdapter<Float?, Float?> {
    override fun fromRealm(realmValue: Float?): Float? = realmValue?.toFloat()

    override fun toRealm(value: Float?): Float? = value?.toFloat()
}

object NullableDoubleAdapter : RealmTypeAdapter<Double?, Double?> {
    override fun fromRealm(realmValue: Double?): Double? = realmValue?.toDouble()

    override fun toRealm(value: Double?): Double? = value?.toDouble()
}

object NullableDecimal128Adapter : RealmTypeAdapter<Decimal128?, Decimal128?> {
    override fun fromRealm(realmValue: Decimal128?): Decimal128? = realmValue?.let { Decimal128.fromIEEE754BIDEncoding(realmValue.high, realmValue.low) }

    override fun toRealm(value: Decimal128?): Decimal128? = value?.let { Decimal128.fromIEEE754BIDEncoding(value.high, value.low) }
}

object NullableTimestampAdapter : RealmTypeAdapter<RealmInstant?, RealmInstant?> {
    override fun fromRealm(realmValue: RealmInstant?): RealmInstant? = realmValue?.let { RealmInstant.from(realmValue.epochSeconds, realmValue.nanosecondsOfSecond) }

    override fun toRealm(value: RealmInstant?): RealmInstant? = value?.let { RealmInstant.from(value.epochSeconds, value.nanosecondsOfSecond) }
}

object NullableObjectIdAdapter : RealmTypeAdapter<ObjectId?, ObjectId?> {
    override fun fromRealm(realmValue: ObjectId?): ObjectId? = realmValue?.let { ObjectId.from(realmValue.asBsonObjectId().toHexString()) }

    override fun toRealm(value: ObjectId?): ObjectId? = value?.let { ObjectId.from(value.asBsonObjectId().toHexString()) }
}

object NullableBsonObjectIdAdapter : RealmTypeAdapter<BsonObjectId?, BsonObjectId?> {
    override fun fromRealm(realmValue: BsonObjectId?): BsonObjectId? = realmValue?.let { BsonObjectId(realmValue.toHexString()) }

    override fun toRealm(value: BsonObjectId?): BsonObjectId? = value?.let { BsonObjectId(value.toHexString()) }
}

object NullableUuidAdapter : RealmTypeAdapter<RealmUUID?, RealmUUID?> {
    override fun fromRealm(realmValue: RealmUUID?): RealmUUID? = realmValue?.let { RealmUUID.from(realmValue.bytes) }

    override fun toRealm(value: RealmUUID?): RealmUUID? = value?.let { RealmUUID.from(value.bytes) }
}

object NullableBinaryAdapter : RealmTypeAdapter<ByteArray?, ByteArray?> {
    override fun fromRealm(realmValue: ByteArray?): ByteArray? = realmValue?.let { realmValue.copyOf() }

    override fun toRealm(value: ByteArray?): ByteArray? = value?.let { value.copyOf() }
}

object RealmAnyAdapter : RealmTypeAdapter<RealmAny?, RealmAny?> {
    override fun fromRealm(realmValue: RealmAny?): RealmAny? = realmValue?.let { realmValue.clone() }

    override fun toRealm(value: RealmAny?): RealmAny? = value?.let { value.clone() }
}

internal fun RealmAny.clone() = when(type) {
    RealmAny.Type.INT -> RealmAny.create(asInt())
    RealmAny.Type.BOOL -> RealmAny.create(asBoolean())
    RealmAny.Type.STRING -> RealmAny.create(asString())
    RealmAny.Type.BINARY -> RealmAny.create(asByteArray())
    RealmAny.Type.TIMESTAMP -> RealmAny.create(asRealmInstant())
    RealmAny.Type.FLOAT -> RealmAny.create(asFloat())
    RealmAny.Type.DOUBLE -> RealmAny.create(asDouble())
    RealmAny.Type.DECIMAL128 -> RealmAny.create(asDecimal128())
    RealmAny.Type.OBJECT_ID -> RealmAny.create(asObjectId())
    RealmAny.Type.UUID -> RealmAny.create(asRealmUUID())
    RealmAny.Type.OBJECT -> RealmAny.create<RealmObject>(asRealmObject())
}
