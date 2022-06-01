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
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Index
import io.realm.kotlin.types.annotations.PrimaryKey

/**
 * Class used for testing of the schema API; thus, doesn't exhaust modeling features but provides
 * sufficient model features to cover all code paths of the schema API.
 */
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
    var date: RealmInstant = RealmInstant.fromEpochSeconds(0, 0)
    var objectId: ObjectId = ObjectId.create()
    @Index
    var nullableString: String? = "Realm"
    var nullableRealmObject: Sample? = null

    // List properties
    var boolList: RealmList<Boolean> = realmListOf()
    var byteList: RealmList<Byte> = realmListOf()
    var charList: RealmList<Char> = realmListOf()
    var shortList: RealmList<Short> = realmListOf()
    var intList: RealmList<Int> = realmListOf()
    var longList: RealmList<Long> = realmListOf()
    var floatList: RealmList<Float> = realmListOf()
    var doubleList: RealmList<Double> = realmListOf()
    var stringList: RealmList<String> = realmListOf()
    var dateList: RealmList<RealmInstant> = realmListOf()
    var objectIdList: RealmList<ObjectId> = realmListOf()

    var objectList: RealmList<Sample> = realmListOf()

    var nullableStringList: RealmList<String?> = realmListOf()
}
