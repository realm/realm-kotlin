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

package sample.input

import io.realm.kotlin.ext.backlinks
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import io.realm.kotlin.types.RealmTypeAdapter
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import io.realm.kotlin.types.annotations.PersistedName
import io.realm.kotlin.types.annotations.TypeAdapter
import org.mongodb.kbson.BsonDateTime
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import java.util.*

class Sample : RealmObject {

    @PrimaryKey
    var id: Long = Random().nextLong()

    @Ignore
    val ignoredDelegate by lazy { "" }

    @Ignore
    var ignoredString: String = ""

    @Transient
    var transientString: String = ""

    // Primitive types
    @Index
    var stringField: String? = "Realm"
    var byteField: Byte? = 0xA
    var charField: Char? = 'a'
    var shortField: Short? = 17

    @Index
    var intField: Int? = 42
    var longField: Long? = 256
    var booleanField: Boolean? = true
    var floatField: Float? = 3.14f
    var doubleField: Double? = 1.19840122
    var decimal128Field: Decimal128? = Decimal128("1.8446744073709551618E-6157")
    var timestampField: RealmInstant? = RealmInstant.from(0, 0)
    var objectIdField: ObjectId? = ObjectId.create()
    var bsonObjectIdField: BsonObjectId? = BsonObjectId()
    var uuidField: RealmUUID? = RealmUUID.random()
    var byteArrayField: ByteArray? = null
    var mutableRealmInt: MutableRealmInt? = MutableRealmInt.create(42)
    var child: Child? = null

    var nullableRealmAny: RealmAny? = RealmAny.create(42)

    // List types
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
    var decimal128ListField: RealmList<Decimal128> = realmListOf()
    var objectListField: RealmList<Sample> = realmListOf()
    var embeddedRealmObjectListField: RealmList<EmbeddedChild> = realmListOf()

    // Nullable list types - RealmList<RealmObject?> is not supported
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
    var nullableDecimal128ListField: RealmList<Decimal128?> = realmListOf()
    var nullableRealmAnyListField: RealmList<RealmAny?> = realmListOf()

    // Set types
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
    var decimal128SetField: RealmSet<Decimal128> = realmSetOf()
    var objectSetField: RealmSet<Sample> = realmSetOf()

    // Nullable set types - RealmSet<RealmObject?> is not supported nor are embedded objects
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
    var nullableDecimal128SetField: RealmSet<Decimal128?> = realmSetOf()
    var nullableRealmAnySetField: RealmSet<RealmAny?> = realmSetOf()

    // Dictionary types - RealmDictionary<RealmObject> is not supported as it must be nullable
    // Embedded objects are supported and must also be nullable
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
    var objectIdDictionaryField: RealmDictionary<ObjectId> = realmDictionaryOf()
    var bsonObjectIdDictionaryField: RealmDictionary<BsonObjectId> = realmDictionaryOf()
    var uuidDictionaryField: RealmDictionary<RealmUUID> = realmDictionaryOf()
    var binaryDictionaryField: RealmDictionary<ByteArray> = realmDictionaryOf()
    var decimal128DictionaryField: RealmDictionary<Decimal128> = realmDictionaryOf()

    // Nullable dictionary types - includes objects and embedded objects
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
    var nullableObjectIdDictionaryField: RealmDictionary<ObjectId?> = realmDictionaryOf()
    var nullableBsonObjectIdDictionaryField: RealmDictionary<BsonObjectId?> = realmDictionaryOf()
    var nullableUUIDDictionaryField: RealmDictionary<RealmUUID?> = realmDictionaryOf()
    var nullableBinaryDictionaryField: RealmDictionary<ByteArray?> = realmDictionaryOf()
    var nullableDecimal128DictionaryField: RealmDictionary<Decimal128?> = realmDictionaryOf()
    var nullableRealmAnyDictionaryField: RealmDictionary<RealmAny?> = realmDictionaryOf()
    var nullableObjectDictionaryField: RealmDictionary<Sample?> = realmDictionaryOf()
    var nullableEmbeddedObjectDictionaryField: RealmDictionary<EmbeddedChild?> = realmDictionaryOf()

    val linkingObjectsByList by backlinks(Sample::objectListField)
    val linkingObjectsBySet by backlinks(Sample::objectSetField)
    val linkingObjectsByDictionary by backlinks(Sample::nullableObjectDictionaryField)

    // @PersistedName annotations
    // Using positional argument
    @PersistedName("persistedNameStringField")
    var publicNameStringField: String? = ""
    // Using named argument
    @PersistedName(name = "persistedNameChildField")
    var publicNameChildField: Child? = null
    @PersistedName("persistedNameLinkingObjectsField")
    val publicNameLinkingObjectsField by backlinks(Sample::objectSetField)

    @TypeAdapter(adapter = RealmInstantBsonDateTimeAdapterSingleton::class)
    var singletonAdaptedRealmInstant: BsonDateTime = BsonDateTime()

    @TypeAdapter(adapter = RealmInstantBsonDateTimeAdapterInstanced::class)
    var instancedAdaptedRealmInstant: BsonDateTime = BsonDateTime()

    fun dumpSchema(): String = "${Sample.`io_realm_kotlin_schema`()}"
}

object RealmInstantBsonDateTimeAdapterSingleton: RealmTypeAdapter<RealmInstant, BsonDateTime> {
    override fun fromRealm(realmValue: RealmInstant): BsonDateTime = BsonDateTime(100)

    override fun toRealm(value: BsonDateTime): RealmInstant = RealmInstant.now()
}

class RealmInstantBsonDateTimeAdapterInstanced: RealmTypeAdapter<RealmInstant, BsonDateTime> {
    override fun fromRealm(realmValue: RealmInstant): BsonDateTime = BsonDateTime(100)

    override fun toRealm(value: BsonDateTime): RealmInstant = RealmInstant.now()
}

class Child : RealmObject {
    var name: String? = "Child-default"
    val linkingObjectsByObject by backlinks(Sample::child)

    @PersistedName(name = "persistedNameParent")
    val publicNameParent by backlinks(Sample::publicNameChildField)
}

class EmbeddedParent : RealmObject {
    var child: EmbeddedChild? = null
}

class EmbeddedChild : EmbeddedRealmObject {
    var name: String? = "Embedded-child"
}
