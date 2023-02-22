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

package io.realm.kotlin.entities.dictionary

import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
import kotlin.reflect.KMutableProperty1

class RealmDictionaryContainer : RealmObject {

    var id: Int = -1
    var stringField: String = "Realm"

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
    var nullableObjectDictionaryField: RealmDictionary<RealmDictionaryContainer?> = realmDictionaryOf()
    var nullableEmbeddedObjectDictionaryField: RealmDictionary<DictionaryEmbeddedLevel1?> = realmDictionaryOf()
    var nullableRealmAnyDictionaryField: RealmDictionary<RealmAny?> = realmDictionaryOf()

    companion object {
        @Suppress("UNCHECKED_CAST")
        val nonNullableProperties = listOf(
            String::class to RealmDictionaryContainer::stringDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any>>,
            Byte::class to RealmDictionaryContainer::byteDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any>>,
            Char::class to RealmDictionaryContainer::charDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any>>,
            Short::class to RealmDictionaryContainer::shortDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any>>,
            Int::class to RealmDictionaryContainer::intDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any>>,
            Long::class to RealmDictionaryContainer::longDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any>>,
            Boolean::class to RealmDictionaryContainer::booleanDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any>>,
            Float::class to RealmDictionaryContainer::floatDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any>>,
            Double::class to RealmDictionaryContainer::doubleDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any>>,
            RealmInstant::class to RealmDictionaryContainer::timestampDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any>>,
            ObjectId::class to RealmDictionaryContainer::objectIdDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any>>,
            BsonObjectId::class to RealmDictionaryContainer::bsonObjectIdDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any>>,
            RealmUUID::class to RealmDictionaryContainer::uuidDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any>>,
            ByteArray::class to RealmDictionaryContainer::binaryDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any>>,
            Decimal128::class to RealmDictionaryContainer::decimal128DictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any>>,
        ).toMap()

        @Suppress("UNCHECKED_CAST")
        val nullableProperties = listOf(
            String::class to RealmDictionaryContainer::nullableStringDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any?>>,
            Byte::class to RealmDictionaryContainer::nullableByteDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any?>>,
            Char::class to RealmDictionaryContainer::nullableCharDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any?>>,
            Short::class to RealmDictionaryContainer::nullableShortDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any?>>,
            Int::class to RealmDictionaryContainer::nullableIntDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any?>>,
            Long::class to RealmDictionaryContainer::nullableLongDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any?>>,
            Boolean::class to RealmDictionaryContainer::nullableBooleanDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any?>>,
            Float::class to RealmDictionaryContainer::nullableFloatDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any?>>,
            Double::class to RealmDictionaryContainer::nullableDoubleDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any?>>,
            RealmInstant::class to RealmDictionaryContainer::nullableTimestampDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any?>>,
            ObjectId::class to RealmDictionaryContainer::nullableObjectIdDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any?>>,
            BsonObjectId::class to RealmDictionaryContainer::nullableBsonObjectIdDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any?>>,
            RealmUUID::class to RealmDictionaryContainer::nullableUUIDDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any?>>,
            ByteArray::class to RealmDictionaryContainer::nullableBinaryDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any?>>,
            Decimal128::class to RealmDictionaryContainer::nullableDecimal128DictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any?>>,
            RealmObject::class to RealmDictionaryContainer::nullableObjectDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any?>>,
            RealmAny::class to RealmDictionaryContainer::nullableRealmAnyDictionaryField as KMutableProperty1<RealmDictionaryContainer, RealmDictionary<Any?>>
        ).toMap()
    }
}

class DictionaryEmbeddedLevel1 : EmbeddedRealmObject {
    var id: Int = -1
    var dictionary: RealmDictionary<RealmDictionaryContainer?> = realmDictionaryOf()
}
