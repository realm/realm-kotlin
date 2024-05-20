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

package io.realm.kotlin.entities.list

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import kotlin.reflect.KMutableProperty1

val listTestSchema = setOf(RealmListContainer::class, EmbeddedLevel1::class)

class RealmListContainer : RealmObject {

    var id: Int = -1
    var stringField: String = "Realm"

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
    var objectListField: RealmList<RealmListContainer> = realmListOf()
    var embeddedRealmObjectListField: RealmList<EmbeddedLevel1> = realmListOf()

    var nullableStringListField: RealmList<String?> = realmListOf()
    var nullableByteListField: RealmList<Byte?> = realmListOf()
    var nullableCharListField: RealmList<Char?> = realmListOf()
    var nullableShortListField: RealmList<Short?> = realmListOf()
    var nullableIntListField: RealmList<Int?> = realmListOf()
    var nullableLongListField: RealmList<Long?> = realmListOf()
    var nullableBooleanListField: RealmList<Boolean?> = realmListOf()
    var nullableFloatListField: RealmList<Float?> = realmListOf()
    var nullableDoubleListField: RealmList<Double?> = realmListOf()
    var nullableDecimal128Field: RealmList<Decimal128?> = realmListOf()
    var nullableTimestampListField: RealmList<RealmInstant?> = realmListOf()
    var nullableBsonObjectIdListField: RealmList<BsonObjectId?> = realmListOf()
    var nullableUUIDListField: RealmList<RealmUUID?> = realmListOf()
    var nullableBinaryListField: RealmList<ByteArray?> = realmListOf()
    var nullableRealmAnyListField: RealmList<RealmAny?> = realmListOf()

    companion object {

        @Suppress("UNCHECKED_CAST")
        val nonNullableProperties = listOf(
            String::class to RealmListContainer::stringListField as KMutableProperty1<RealmListContainer, RealmList<Any>>,
            Byte::class to RealmListContainer::byteListField as KMutableProperty1<RealmListContainer, RealmList<Any>>,
            Char::class to RealmListContainer::charListField as KMutableProperty1<RealmListContainer, RealmList<Any>>,
            Short::class to RealmListContainer::shortListField as KMutableProperty1<RealmListContainer, RealmList<Any>>,
            Int::class to RealmListContainer::intListField as KMutableProperty1<RealmListContainer, RealmList<Any>>,
            Long::class to RealmListContainer::longListField as KMutableProperty1<RealmListContainer, RealmList<Any>>,
            Boolean::class to RealmListContainer::booleanListField as KMutableProperty1<RealmListContainer, RealmList<Any>>,
            Float::class to RealmListContainer::floatListField as KMutableProperty1<RealmListContainer, RealmList<Any>>,
            Double::class to RealmListContainer::doubleListField as KMutableProperty1<RealmListContainer, RealmList<Any>>,
            Decimal128::class to RealmListContainer::decimal128ListField as KMutableProperty1<RealmListContainer, RealmList<Any>>,
            RealmInstant::class to RealmListContainer::timestampListField as KMutableProperty1<RealmListContainer, RealmList<Any>>,
            BsonObjectId::class to RealmListContainer::bsonObjectIdListField as KMutableProperty1<RealmListContainer, RealmList<Any>>,
            RealmUUID::class to RealmListContainer::uuidListField as KMutableProperty1<RealmListContainer, RealmList<Any>>,
            ByteArray::class to RealmListContainer::binaryListField as KMutableProperty1<RealmListContainer, RealmList<Any>>,
            RealmObject::class to RealmListContainer::objectListField as KMutableProperty1<RealmListContainer, RealmList<Any>>
        ).toMap()

        @Suppress("UNCHECKED_CAST")
        val nullableProperties = listOf(
            String::class to RealmListContainer::nullableStringListField as KMutableProperty1<RealmListContainer, RealmList<Any?>>,
            Byte::class to RealmListContainer::nullableByteListField as KMutableProperty1<RealmListContainer, RealmList<Any?>>,
            Char::class to RealmListContainer::nullableCharListField as KMutableProperty1<RealmListContainer, RealmList<Any?>>,
            Short::class to RealmListContainer::nullableShortListField as KMutableProperty1<RealmListContainer, RealmList<Any?>>,
            Int::class to RealmListContainer::nullableIntListField as KMutableProperty1<RealmListContainer, RealmList<Any?>>,
            Long::class to RealmListContainer::nullableLongListField as KMutableProperty1<RealmListContainer, RealmList<Any?>>,
            Boolean::class to RealmListContainer::nullableBooleanListField as KMutableProperty1<RealmListContainer, RealmList<Any?>>,
            Float::class to RealmListContainer::nullableFloatListField as KMutableProperty1<RealmListContainer, RealmList<Any?>>,
            Double::class to RealmListContainer::nullableDoubleListField as KMutableProperty1<RealmListContainer, RealmList<Any?>>,
            Decimal128::class to RealmListContainer::nullableDecimal128Field as KMutableProperty1<RealmListContainer, RealmList<Any?>>,
            RealmInstant::class to RealmListContainer::nullableTimestampListField as KMutableProperty1<RealmListContainer, RealmList<Any?>>,
            BsonObjectId::class to RealmListContainer::nullableBsonObjectIdListField as KMutableProperty1<RealmListContainer, RealmList<Any?>>,
            RealmUUID::class to RealmListContainer::nullableUUIDListField as KMutableProperty1<RealmListContainer, RealmList<Any?>>,
            ByteArray::class to RealmListContainer::nullableBinaryListField as KMutableProperty1<RealmListContainer, RealmList<Any?>>,
            RealmAny::class to RealmListContainer::nullableRealmAnyListField as KMutableProperty1<RealmListContainer, RealmList<Any?>>
        ).toMap()
    }
}

// Circular dependencies with lists
class Level1 : RealmObject {
    var name: String = ""
    var list: RealmList<Level2> = realmListOf()
}

class Level2 : RealmObject {
    var name: String = ""
    var list: RealmList<Level3> = realmListOf()
}

class Level3 : RealmObject {
    var name: String = ""
    var list: RealmList<Level1> = realmListOf()
}

class EmbeddedLevel1 : EmbeddedRealmObject {
    var id: Int = -1
    var list: RealmList<RealmListContainer> = realmListOf()
}
