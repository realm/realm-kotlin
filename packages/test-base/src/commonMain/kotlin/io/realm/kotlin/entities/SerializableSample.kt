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
    RealmListSerializer::class,
    RealmSetSerializer::class,
    RealmAnySerializer::class,
    RealmInstantSerializer::class,
    MutableRealmIntSerializer::class,
    RealmUUIDSerializer::class,
    RealmObjectIdSerializer::class
)

package io.realm.kotlin.entities

import io.realm.kotlin.ext.backlinks
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.serializers.MutableRealmIntSerializer
import io.realm.kotlin.serializers.RealmAnySerializer
import io.realm.kotlin.serializers.RealmInstantSerializer
import io.realm.kotlin.serializers.RealmListSerializer
import io.realm.kotlin.serializers.RealmObjectIdSerializer
import io.realm.kotlin.serializers.RealmSetSerializer
import io.realm.kotlin.serializers.RealmUUIDSerializer
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128

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
    var timestampField: RealmInstant = RealmInstant.from(100, 1000)
    var objectIdField: ObjectId = ObjectId.from("507f1f77bcf86cd799439011")
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
    var nullableObjectIdField: ObjectId? = null
    var nullableBsonObjectIdField: ObjectId? = null
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
    var timestampListField: RealmList<RealmInstant> = realmListOf()
    var objectIdListField: RealmList<ObjectId> = realmListOf()
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
    var nullableTimestampListField: RealmList<RealmInstant?> = realmListOf()
    var nullableObjectIdListField: RealmList<ObjectId?> = realmListOf()
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
    var timestampSetField: RealmSet<RealmInstant> = realmSetOf()
    var objectIdSetField: RealmSet<ObjectId> = realmSetOf()
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
    var nullableTimestampSetField: RealmSet<RealmInstant?> = realmSetOf()
    var nullableObjectIdSetField: RealmSet<ObjectId?> = realmSetOf()
    var nullableBsonObjectIdSetField: RealmSet<BsonObjectId?> = realmSetOf()
    var nullableUUIDSetField: RealmSet<RealmUUID?> = realmSetOf()
    var nullableBinarySetField: RealmSet<ByteArray?> = realmSetOf()
    var nullableRealmAnySetField: RealmSet<RealmAny?> = realmSetOf()

    val objectBacklinks by backlinks(SerializableSample::nullableObject)
    val listBacklinks by backlinks(SerializableSample::objectListField)
    val setBacklinks by backlinks(SerializableSample::objectSetField)

    // For verification that references inside class is also using our modified accessors and are
    // not optimized to use the backing field directly.
    fun stringFieldGetter(): String {
        return stringField
    }

    fun stringFieldSetter(s: String) {
        stringField = s
    }

    companion object {
        // Empty object required by SampleTests
    }
}

@Serializable
class SerializableEmbeddedObject : EmbeddedRealmObject {
    var name: String = ""
}
