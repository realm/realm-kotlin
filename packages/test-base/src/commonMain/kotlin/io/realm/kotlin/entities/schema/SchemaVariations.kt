/*
 * Copyright 2021 Realm Inc.
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

package io.realm.kotlin.entities.schema

import io.realm.kotlin.entities.Sample
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import io.realm.kotlin.types.annotations.FullText
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128

/**
 * Class used for testing of the schema API; thus, doesn't exhaust modeling features but provides
 * sufficient model features to cover all code paths of the schema API.
 */
@Suppress("MagicNumber")
class SchemaVariations : RealmObject {
    // Value properties
    var bool: Boolean = false
    var byte: Byte = 0
    var char: Char = 'a'
    var short: Short = 5
    var int: Int = 5
    var long: Long = 5L
    var float: Float = 5f
    var double: Double = 5.0

    @PrimaryKey
    var string: String = "Realm"
    @FullText
    var fulltext: String = "A very long string"
    var date: RealmInstant = RealmInstant.from(0, 0)
    var objectId: ObjectId = ObjectId.create()
    var bsonObjectId: BsonObjectId = BsonObjectId()
    var decimal128: Decimal128 = Decimal128("1")
    var uuid: RealmUUID = RealmUUID.random()
    var binary: ByteArray = byteArrayOf(22, 66)

    @Index
    var nullableString: String? = "Realm"
    var nullableRealmObject: Sample? = null
    var nullableRealmAny: RealmAny? = RealmAny.create(42)

    // List properties
    var boolList: RealmList<Boolean> = realmListOf()
    var byteList: RealmList<Byte> = realmListOf()
    var charList: RealmList<Char> = realmListOf()
    var shortList: RealmList<Short> = realmListOf()
    var intList: RealmList<Int> = realmListOf()
    var longList: RealmList<Long> = realmListOf()
    var floatList: RealmList<Float> = realmListOf()
    var doubleList: RealmList<Double> = realmListOf()
    var decimal128List: RealmList<Decimal128> = realmListOf()
    var stringList: RealmList<String> = realmListOf()
    var dateList: RealmList<RealmInstant> = realmListOf()
    var objectIdList: RealmList<ObjectId> = realmListOf()
    var bsonObjectIdList: RealmList<BsonObjectId> = realmListOf()
    var uuidList: RealmList<RealmUUID> = realmListOf()
    var binaryList: RealmList<ByteArray> = realmListOf()

    var objectList: RealmList<Sample> = realmListOf()

    var nullableStringList: RealmList<String?> = realmListOf()
    var nullableRealmAnyList: RealmList<RealmAny?> = realmListOf()

    // Set properties
    var boolSet: RealmSet<Boolean> = realmSetOf()
    var byteSet: RealmSet<Byte> = realmSetOf()
    var charSet: RealmSet<Char> = realmSetOf()
    var shortSet: RealmSet<Short> = realmSetOf()
    var intSet: RealmSet<Int> = realmSetOf()
    var longSet: RealmSet<Long> = realmSetOf()
    var floatSet: RealmSet<Float> = realmSetOf()
    var doubleSet: RealmSet<Double> = realmSetOf()
    var decimal128Set: RealmSet<Decimal128> = realmSetOf()
    var stringSet: RealmSet<String> = realmSetOf()
    var dateSet: RealmSet<RealmInstant> = realmSetOf()
    var objectIdSet: RealmSet<ObjectId> = realmSetOf()
    var bsonObjectIdSet: RealmSet<BsonObjectId> = realmSetOf()
    var uuidSet: RealmSet<RealmUUID> = realmSetOf()
    var binarySet: RealmSet<ByteArray> = realmSetOf()

    var objectSet: RealmSet<Sample> = realmSetOf()

    var nullableStringSet: RealmSet<String?> = realmSetOf()
    var nullableRealmAnySet: RealmSet<RealmAny?> = realmSetOf()

    // Dictionary properties
    var boolMap: RealmDictionary<Boolean> = realmDictionaryOf()
    var byteMap: RealmDictionary<Byte> = realmDictionaryOf()
    var charMap: RealmDictionary<Char> = realmDictionaryOf()
    var shortMap: RealmDictionary<Short> = realmDictionaryOf()
    var intMap: RealmDictionary<Int> = realmDictionaryOf()
    var longMap: RealmDictionary<Long> = realmDictionaryOf()
    var floatMap: RealmDictionary<Float> = realmDictionaryOf()
    var doubleMap: RealmDictionary<Double> = realmDictionaryOf()
    var decimal128Map: RealmDictionary<Decimal128> = realmDictionaryOf()
    var stringMap: RealmDictionary<String> = realmDictionaryOf()
    var dateMap: RealmDictionary<RealmInstant> = realmDictionaryOf()
    var objectIdMap: RealmDictionary<ObjectId> = realmDictionaryOf()
    var bsonObjectIdMap: RealmDictionary<BsonObjectId> = realmDictionaryOf()
    var uuidMap: RealmDictionary<RealmUUID> = realmDictionaryOf()
    var binaryMap: RealmDictionary<ByteArray> = realmDictionaryOf()

    var objectMap: RealmDictionary<Sample?> = realmDictionaryOf()

    var nullableStringMap: RealmDictionary<String?> = realmDictionaryOf()
    var nullableRealmAnyMap: RealmDictionary<RealmAny?> = realmDictionaryOf()
}
