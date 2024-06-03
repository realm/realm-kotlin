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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.kotlin.entities

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128

@Suppress("MagicNumber")
class SampleWithPrimaryKey : RealmObject {
    @PrimaryKey
    var primaryKey: Long = 1L

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
    var nullableObject: SampleWithPrimaryKey? = null

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
    var bsonObjectIdListField: RealmList<BsonObjectId> = realmListOf()
    var binaryListField: RealmList<ByteArray> = realmListOf()
    var objectListField: RealmList<SampleWithPrimaryKey> = realmListOf()

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
    var nullableBsonObjectIdListField: RealmList<BsonObjectId?> = realmListOf()
    var nullableBinaryListField: RealmList<ByteArray?> = realmListOf()

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
    var bsonObjectIdSetField: RealmSet<BsonObjectId> = realmSetOf()
    var binarySetField: RealmSet<ByteArray> = realmSetOf()
    var objectSetField: RealmSet<SampleWithPrimaryKey> = realmSetOf()

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
    var nullableBsonObjectIdSetField: RealmSet<BsonObjectId?> = realmSetOf()
    var nullableBinarySetField: RealmSet<ByteArray?> = realmSetOf()

    // For verification that references inside class is also using our modified accessors and are
    // not optimized to use the backing field directly.
    fun stringFieldGetter(): String {
        return stringField
    }
    fun stringFieldSetter(s: String) {
        stringField = s
    }
}
