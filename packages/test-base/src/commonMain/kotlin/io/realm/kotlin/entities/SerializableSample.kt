/*
 * Copyright 2023 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:UseSerializers(
    RealmListKSerializer::class,
    RealmSetKSerializer::class,
    RealmDictionaryKSerializer::class,
    RealmAnyKSerializer::class,
    RealmInstantKSerializer::class,
    MutableRealmIntKSerializer::class,
    RealmUUIDKSerializer::class
)

package io.realm.kotlin.entities

import io.realm.kotlin.ext.backlinks
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.serializers.MutableRealmIntKSerializer
import io.realm.kotlin.serializers.RealmAnyKSerializer
import io.realm.kotlin.serializers.RealmDictionaryKSerializer
import io.realm.kotlin.serializers.RealmInstantKSerializer
import io.realm.kotlin.serializers.RealmListKSerializer
import io.realm.kotlin.serializers.RealmSetKSerializer
import io.realm.kotlin.serializers.RealmUUIDKSerializer
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1

@Suppress("MagicNumber")
@Serializable
class SerializableSample : RealmObject {
    var stringField: String = "Realm"
    var byteField: Byte = 0xA
    var charField: Char = 'a'
    var shortField: Short = 17
    var intField: Int = 42
    var longField: Long = 256
    var booleanField: Boolean = true
    var floatField: Float = 3.14f
    var doubleField: Double = 1.19840122
    var decimal128Field: Decimal128 = Decimal128("1.8446744073709551618E-6157")
    // We will loose nano second precision when we round trip these, so framework only works for
    // timestamps with 0-nanosecond fraction.
    var timestampField: RealmInstant = RealmInstant.from(100, 1000000)
    var bsonObjectIdField: BsonObjectId = BsonObjectId("507f1f77bcf86cd799439011")
    var uuidField: RealmUUID = RealmUUID.from("46423f1b-ce3e-4a7e-812f-004cf9c42d76")
    var binaryField: ByteArray = byteArrayOf(42)
    var mutableRealmIntField: MutableRealmInt = MutableRealmInt.create(42)

    var nullableStringField: String? = null
    var nullableByteField: Byte? = null
    var nullableCharField: Char? = null
    var nullableShortField: Short? = null
    var nullableIntField: Int? = null
    var nullableLongField: Long? = null
    var nullableBooleanField: Boolean? = null
    var nullableFloatField: Float? = null
    var nullableDoubleField: Double? = null
    var nullableDecimal128Field: Decimal128? = null
    var nullableTimestampField: RealmInstant? = null
    var nullableBsonObjectIdField: BsonObjectId? = null
    var nullableUUIDField: RealmUUID? = null
    var nullableBinaryField: ByteArray? = null
    var nullableMutableRealmIntField: MutableRealmInt? = null
    var nullableObject: SerializableSample? = null
    var realmEmbeddedObject: SerializableEmbeddedObject? = null
    var nullableRealmAnyField: RealmAny? = null

    var stringListField: RealmList<String> = realmListOf()
    var byteListField: RealmList<Byte> = realmListOf()
    var charListField: RealmList<Char> = realmListOf()
    var shortListField: RealmList<Short> = realmListOf()
    var intListField: RealmList<Int> = realmListOf()
    var longListField: RealmList<Long> = realmListOf()
    var booleanListField: RealmList<Boolean> = realmListOf()
    var floatListField: RealmList<Float> = realmListOf()
    var doubleListField: RealmList<Double> = realmListOf()
    var decimal128ListField: RealmList<Decimal128> = realmListOf()
    var timestampListField: RealmList<RealmInstant> = realmListOf()
    var bsonObjectIdListField: RealmList<BsonObjectId> = realmListOf()
    var uuidListField: RealmList<RealmUUID> = realmListOf()
    var binaryListField: RealmList<ByteArray> = realmListOf()
    var objectListField: RealmList<SerializableSample> = realmListOf()

    var nullableStringListField: RealmList<String?> = realmListOf()
    var nullableByteListField: RealmList<Byte?> = realmListOf()
    var nullableCharListField: RealmList<Char?> = realmListOf()
    var nullableShortListField: RealmList<Short?> = realmListOf()
    var nullableIntListField: RealmList<Int?> = realmListOf()
    var nullableLongListField: RealmList<Long?> = realmListOf()
    var nullableBooleanListField: RealmList<Boolean?> = realmListOf()
    var nullableFloatListField: RealmList<Float?> = realmListOf()
    var nullableDoubleListField: RealmList<Double?> = realmListOf()
    var nullableDecimal128ListField: RealmList<Decimal128?> = realmListOf()
    var nullableTimestampListField: RealmList<RealmInstant?> = realmListOf()
    var nullableBsonObjectIdListField: RealmList<BsonObjectId?> = realmListOf()
    var nullableUUIDListField: RealmList<RealmUUID?> = realmListOf()
    var nullableBinaryListField: RealmList<ByteArray?> = realmListOf()
    var nullableRealmAnyListField: RealmList<RealmAny?> = realmListOf()

    var stringSetField: RealmSet<String> = realmSetOf()
    var byteSetField: RealmSet<Byte> = realmSetOf()
    var charSetField: RealmSet<Char> = realmSetOf()
    var shortSetField: RealmSet<Short> = realmSetOf()
    var intSetField: RealmSet<Int> = realmSetOf()
    var longSetField: RealmSet<Long> = realmSetOf()
    var booleanSetField: RealmSet<Boolean> = realmSetOf()
    var floatSetField: RealmSet<Float> = realmSetOf()
    var doubleSetField: RealmSet<Double> = realmSetOf()
    var decimal128SetField: RealmSet<Decimal128> = realmSetOf()
    var timestampSetField: RealmSet<RealmInstant> = realmSetOf()
    var bsonObjectIdSetField: RealmSet<BsonObjectId> = realmSetOf()
    var uuidSetField: RealmSet<RealmUUID> = realmSetOf()
    var binarySetField: RealmSet<ByteArray> = realmSetOf()
    var objectSetField: RealmSet<SerializableSample> = realmSetOf()

    var nullableStringSetField: RealmSet<String?> = realmSetOf()
    var nullableByteSetField: RealmSet<Byte?> = realmSetOf()
    var nullableCharSetField: RealmSet<Char?> = realmSetOf()
    var nullableShortSetField: RealmSet<Short?> = realmSetOf()
    var nullableIntSetField: RealmSet<Int?> = realmSetOf()
    var nullableLongSetField: RealmSet<Long?> = realmSetOf()
    var nullableBooleanSetField: RealmSet<Boolean?> = realmSetOf()
    var nullableFloatSetField: RealmSet<Float?> = realmSetOf()
    var nullableDoubleSetField: RealmSet<Double?> = realmSetOf()
    var nullableDecimal128SetField: RealmSet<Decimal128?> = realmSetOf()
    var nullableTimestampSetField: RealmSet<RealmInstant?> = realmSetOf()
    var nullableBsonObjectIdSetField: RealmSet<BsonObjectId?> = realmSetOf()
    var nullableUUIDSetField: RealmSet<RealmUUID?> = realmSetOf()
    var nullableBinarySetField: RealmSet<ByteArray?> = realmSetOf()
    var nullableRealmAnySetField: RealmSet<RealmAny?> = realmSetOf()

    var stringDictionaryField: RealmDictionary<String> = realmDictionaryOf()
    var byteDictionaryField: RealmDictionary<Byte> = realmDictionaryOf()
    var charDictionaryField: RealmDictionary<Char> = realmDictionaryOf()
    var shortDictionaryField: RealmDictionary<Short> = realmDictionaryOf()
    var intDictionaryField: RealmDictionary<Int> = realmDictionaryOf()
    var longDictionaryField: RealmDictionary<Long> = realmDictionaryOf()
    var booleanDictionaryField: RealmDictionary<Boolean> = realmDictionaryOf()
    var floatDictionaryField: RealmDictionary<Float> = realmDictionaryOf()
    var doubleDictionaryField: RealmDictionary<Double> = realmDictionaryOf()
    var timestampDictionaryField: RealmDictionary<RealmInstant> = realmDictionaryOf()
    var bsonObjectIdDictionaryField: RealmDictionary<BsonObjectId> = realmDictionaryOf()
    var uuidDictionaryField: RealmDictionary<RealmUUID> = realmDictionaryOf()
    var binaryDictionaryField: RealmDictionary<ByteArray> = realmDictionaryOf()
    var decimal128DictionaryField: RealmDictionary<Decimal128> = realmDictionaryOf()

    var nullableStringDictionaryField: RealmDictionary<String?> = realmDictionaryOf()
    var nullableByteDictionaryField: RealmDictionary<Byte?> = realmDictionaryOf()
    var nullableCharDictionaryField: RealmDictionary<Char?> = realmDictionaryOf()
    var nullableShortDictionaryField: RealmDictionary<Short?> = realmDictionaryOf()
    var nullableIntDictionaryField: RealmDictionary<Int?> = realmDictionaryOf()
    var nullableLongDictionaryField: RealmDictionary<Long?> = realmDictionaryOf()
    var nullableBooleanDictionaryField: RealmDictionary<Boolean?> = realmDictionaryOf()
    var nullableFloatDictionaryField: RealmDictionary<Float?> = realmDictionaryOf()
    var nullableDoubleDictionaryField: RealmDictionary<Double?> = realmDictionaryOf()
    var nullableTimestampDictionaryField: RealmDictionary<RealmInstant?> = realmDictionaryOf()
    var nullableBsonObjectIdDictionaryField: RealmDictionary<BsonObjectId?> = realmDictionaryOf()
    var nullableUUIDDictionaryField: RealmDictionary<RealmUUID?> = realmDictionaryOf()
    var nullableBinaryDictionaryField: RealmDictionary<ByteArray?> = realmDictionaryOf()
    var nullableDecimal128DictionaryField: RealmDictionary<Decimal128?> = realmDictionaryOf()
    var nullableRealmAnyDictionaryField: RealmDictionary<RealmAny?> = realmDictionaryOf()
    var nullableObjectDictionaryField: RealmDictionary<SerializableSample?> = realmDictionaryOf()

    val objectBacklinks by backlinks(SerializableSample::nullableObject)
    val listBacklinks by backlinks(SerializableSample::objectListField)
    val setBacklinks by backlinks(SerializableSample::objectSetField)

    companion object {

        @Suppress("UNCHECKED_CAST")
        val listNonNullableProperties = mapOf(
            String::class to SerializableSample::stringListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Byte::class to SerializableSample::byteListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Char::class to SerializableSample::charListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Short::class to SerializableSample::shortListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Int::class to SerializableSample::intListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Long::class to SerializableSample::longListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Boolean::class to SerializableSample::booleanListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Float::class to SerializableSample::floatListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Double::class to SerializableSample::doubleListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Decimal128::class to SerializableSample::decimal128ListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            RealmInstant::class to SerializableSample::timestampListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            BsonObjectId::class to SerializableSample::bsonObjectIdListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            RealmUUID::class to SerializableSample::uuidListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            ByteArray::class to SerializableSample::binaryListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            RealmObject::class to SerializableSample::objectListField as KMutableProperty1<SerializableSample, MutableCollection<Any>>
        )

        @Suppress("UNCHECKED_CAST")
        val listNullableProperties: Map<KClass<out Any>, KMutableProperty1<SerializableSample, MutableCollection<Any?>>> = mapOf(
            String::class to SerializableSample::nullableStringListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Byte::class to SerializableSample::nullableByteListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Char::class to SerializableSample::nullableCharListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Short::class to SerializableSample::nullableShortListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Int::class to SerializableSample::nullableIntListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Long::class to SerializableSample::nullableLongListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Boolean::class to SerializableSample::nullableBooleanListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Float::class to SerializableSample::nullableFloatListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Double::class to SerializableSample::nullableDoubleListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Decimal128::class to SerializableSample::nullableDecimal128ListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            RealmInstant::class to SerializableSample::nullableTimestampListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            BsonObjectId::class to SerializableSample::nullableBsonObjectIdListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            RealmUUID::class to SerializableSample::nullableUUIDListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            ByteArray::class to SerializableSample::nullableBinaryListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            RealmAny::class to SerializableSample::nullableRealmAnyListField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>
        )

        @Suppress("UNCHECKED_CAST")
        val setNonNullableProperties = mapOf(
            String::class to SerializableSample::stringSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Byte::class to SerializableSample::byteSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Char::class to SerializableSample::charSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Short::class to SerializableSample::shortSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Int::class to SerializableSample::intSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Long::class to SerializableSample::longSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Boolean::class to SerializableSample::booleanSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Float::class to SerializableSample::floatSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Double::class to SerializableSample::doubleSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            Decimal128::class to SerializableSample::decimal128SetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            RealmInstant::class to SerializableSample::timestampSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            BsonObjectId::class to SerializableSample::bsonObjectIdSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            RealmUUID::class to SerializableSample::uuidSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            ByteArray::class to SerializableSample::binarySetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>,
            RealmObject::class to SerializableSample::objectSetField as KMutableProperty1<SerializableSample, MutableCollection<Any>>
        )

        @Suppress("UNCHECKED_CAST")
        val setNullableProperties = mapOf(
            String::class to SerializableSample::nullableStringSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Byte::class to SerializableSample::nullableByteSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Char::class to SerializableSample::nullableCharSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Short::class to SerializableSample::nullableShortSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Int::class to SerializableSample::nullableIntSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Long::class to SerializableSample::nullableLongSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Boolean::class to SerializableSample::nullableBooleanSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Float::class to SerializableSample::nullableFloatSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Double::class to SerializableSample::nullableDoubleSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            Decimal128::class to SerializableSample::nullableDecimal128SetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            RealmInstant::class to SerializableSample::nullableTimestampSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            BsonObjectId::class to SerializableSample::nullableBsonObjectIdSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            RealmUUID::class to SerializableSample::nullableUUIDSetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            ByteArray::class to SerializableSample::nullableBinarySetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>,
            RealmAny::class to SerializableSample::nullableRealmAnySetField as KMutableProperty1<SerializableSample, MutableCollection<Any?>>
        )

        @Suppress("UNCHECKED_CAST")
        val dictNonNullableProperties = mapOf(
            String::class to SerializableSample::stringDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any>>,
            Byte::class to SerializableSample::byteDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any>>,
            Char::class to SerializableSample::charDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any>>,
            Short::class to SerializableSample::shortDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any>>,
            Int::class to SerializableSample::intDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any>>,
            Long::class to SerializableSample::longDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any>>,
            Boolean::class to SerializableSample::booleanDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any>>,
            Float::class to SerializableSample::floatDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any>>,
            Double::class to SerializableSample::doubleDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any>>,
            RealmInstant::class to SerializableSample::timestampDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any>>,
            BsonObjectId::class to SerializableSample::bsonObjectIdDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any>>,
            RealmUUID::class to SerializableSample::uuidDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any>>,
            ByteArray::class to SerializableSample::binaryDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any>>,
            Decimal128::class to SerializableSample::decimal128DictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any>>,
        )

        @Suppress("UNCHECKED_CAST")
        val dictNullableProperties = mapOf(
            String::class to SerializableSample::nullableStringDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any?>>,
            Byte::class to SerializableSample::nullableByteDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any?>>,
            Char::class to SerializableSample::nullableCharDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any?>>,
            Short::class to SerializableSample::nullableShortDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any?>>,
            Int::class to SerializableSample::nullableIntDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any?>>,
            Long::class to SerializableSample::nullableLongDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any?>>,
            Boolean::class to SerializableSample::nullableBooleanDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any?>>,
            Float::class to SerializableSample::nullableFloatDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any?>>,
            Double::class to SerializableSample::nullableDoubleDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any?>>,
            RealmInstant::class to SerializableSample::nullableTimestampDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any?>>,
            BsonObjectId::class to SerializableSample::nullableBsonObjectIdDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any?>>,
            RealmUUID::class to SerializableSample::nullableUUIDDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any?>>,
            ByteArray::class to SerializableSample::nullableBinaryDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any?>>,
            Decimal128::class to SerializableSample::nullableDecimal128DictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any?>>,
            RealmObject::class to SerializableSample::nullableObjectDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any?>>,
            RealmAny::class to SerializableSample::nullableRealmAnyDictionaryField as KMutableProperty1<SerializableSample, RealmDictionary<Any?>>
        )

        val properties = mapOf(
            String::class to SerializableSample::stringField,
            Byte::class to SerializableSample::byteField,
            Char::class to SerializableSample::charField,
            Short::class to SerializableSample::shortField,
            Int::class to SerializableSample::intField,
            Long::class to SerializableSample::longField,
            Boolean::class to SerializableSample::booleanField,
            Float::class to SerializableSample::floatField,
            Double::class to SerializableSample::doubleField,
            RealmInstant::class to SerializableSample::timestampField,
            MutableRealmInt::class to SerializableSample::mutableRealmIntField,
            BsonObjectId::class to SerializableSample::bsonObjectIdField,
            RealmUUID::class to SerializableSample::uuidField,
            ByteArray::class to SerializableSample::binaryField,
            Decimal128::class to SerializableSample::decimal128Field,
        )

        val nullableProperties = mapOf(
            String::class to SerializableSample::nullableStringField,
            Byte::class to SerializableSample::nullableByteField,
            Char::class to SerializableSample::nullableCharField,
            Short::class to SerializableSample::nullableShortField,
            Int::class to SerializableSample::nullableIntField,
            Long::class to SerializableSample::nullableLongField,
            Boolean::class to SerializableSample::nullableBooleanField,
            Float::class to SerializableSample::nullableFloatField,
            Double::class to SerializableSample::nullableDoubleField,
            RealmInstant::class to SerializableSample::nullableTimestampField,
            MutableRealmInt::class to SerializableSample::nullableMutableRealmIntField,
            BsonObjectId::class to SerializableSample::nullableBsonObjectIdField,
            RealmUUID::class to SerializableSample::nullableUUIDField,
            ByteArray::class to SerializableSample::nullableBinaryField,
            Decimal128::class to SerializableSample::nullableDecimal128Field,
            RealmObject::class to SerializableSample::nullableObject,
        )
    }
}

@Serializable
class SerializableEmbeddedObject : EmbeddedRealmObject {
    var name: String = "hello world"

    // Supplying custom companion object to work around that multiple K2 FIR extension clashes if
    // they both generate a Companion.
    // https://youtrack.jetbrains.com/issue/KT-62194/K2-Two-compiler-plugins-interference-in-generated-companion-object
    companion object
}
