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

package io.realm.kotlin.entities.set

import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import kotlin.reflect.KMutableProperty1

class RealmSetContainer : RealmObject {

    var id: Int = -1
    var stringField: String = "Realm"

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
    var objectSetField: RealmSet<RealmSetContainer> = realmSetOf()

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

    companion object {
        @Suppress("UNCHECKED_CAST")
        val nonNullableProperties = listOf(
            String::class to RealmSetContainer::stringSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            Byte::class to RealmSetContainer::byteSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            Char::class to RealmSetContainer::charSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            Short::class to RealmSetContainer::shortSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            Int::class to RealmSetContainer::intSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            Long::class to RealmSetContainer::longSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            Boolean::class to RealmSetContainer::booleanSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            Float::class to RealmSetContainer::floatSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            Double::class to RealmSetContainer::doubleSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            Decimal128::class to RealmSetContainer::decimal128SetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            RealmInstant::class to RealmSetContainer::timestampSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            BsonObjectId::class to RealmSetContainer::bsonObjectIdSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            RealmUUID::class to RealmSetContainer::uuidSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            ByteArray::class to RealmSetContainer::binarySetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>,
            RealmObject::class to RealmSetContainer::objectSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any>>
        ).toMap()

        @Suppress("UNCHECKED_CAST")
        val nullableProperties = listOf(
            String::class to RealmSetContainer::nullableStringSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any?>>,
            Byte::class to RealmSetContainer::nullableByteSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any?>>,
            Char::class to RealmSetContainer::nullableCharSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any?>>,
            Short::class to RealmSetContainer::nullableShortSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any?>>,
            Int::class to RealmSetContainer::nullableIntSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any?>>,
            Long::class to RealmSetContainer::nullableLongSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any?>>,
            Boolean::class to RealmSetContainer::nullableBooleanSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any?>>,
            Float::class to RealmSetContainer::nullableFloatSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any?>>,
            Double::class to RealmSetContainer::nullableDoubleSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any?>>,
            Decimal128::class to RealmSetContainer::nullableDecimal128SetField as KMutableProperty1<RealmSetContainer, RealmSet<Any?>>,
            RealmInstant::class to RealmSetContainer::nullableTimestampSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any?>>,
            BsonObjectId::class to RealmSetContainer::nullableBsonObjectIdSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any?>>,
            RealmUUID::class to RealmSetContainer::nullableUUIDSetField as KMutableProperty1<RealmSetContainer, RealmSet<Any?>>,
            ByteArray::class to RealmSetContainer::nullableBinarySetField as KMutableProperty1<RealmSetContainer, RealmSet<Any?>>,
            RealmAny::class to RealmSetContainer::nullableRealmAnySetField as KMutableProperty1<RealmSetContainer, RealmSet<Any?>>
        ).toMap()
    }
}
