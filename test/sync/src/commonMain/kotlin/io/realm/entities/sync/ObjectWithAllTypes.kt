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

package io.realm.entities.sync

import io.realm.ObjectId
import io.realm.RealmInstant
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.realmListOf

class ObjectWithAllTypes : RealmObject {
    @PrimaryKey
    var _id: String = "id"

    // Non-nullable types
    var stringField: String = "hello world"
    var byteField: Byte = 0
    var charField: Char = 0.toChar()
    var shortField: Short = 0
    var intField: Int = 0
    var longField: Long = 0
    var booleanField: Boolean = true
    var doubleField: Double = 0.0
    var floatField: Float = 0.0.toFloat()
    var realmInstantField: RealmInstant = RealmInstant.MIN
    var objectIdField: ObjectId = ObjectId.create()
    var objectField: ObjectWithAllTypes? = null

    // Nullable types
    var stringNullableField: String? = null
    var byteNullableField: Byte? = null
    var charNullableField: Char? = null
    var shortNullableField: Short? = null
    var intNullableField: Int? = null
    var longNullableField: Long? = null
    var booleanNullableField: Boolean? = null
    var doubleNullableField: Double? = null
    var floatNullableField: Float? = null
    var realmInstantNullableField: RealmInstant? = null
    var objectIdNullableField: ObjectId? = null
    var objectNullableField: ObjectWithAllTypes? = null

    // RealmLists
    var stringRealmList: RealmList<String> = realmListOf("hello world")
    var byteRealmList: RealmList<Byte> = realmListOf(0)
    var charRealmList: RealmList<Char> = realmListOf(0.toChar())
    var shortRealmList: RealmList<Short> = realmListOf(0)
    var intRealmList: RealmList<Int> = realmListOf(0)
    var longRealmList: RealmList<Long> = realmListOf(0)
    var booleanRealmList: RealmList<Boolean> = realmListOf(true)
    var doubleRealmList: RealmList<Double> = realmListOf(0.0)
    var floatRealmList: RealmList<Float> = realmListOf(0.0.toFloat())
    var realmInstantRealmList: RealmList<RealmInstant> = realmListOf(RealmInstant.MIN)
    var objectIdRealmList: RealmList<ObjectId> = realmListOf(ObjectId.create())
    var objectRealmList: RealmList<ObjectWithAllTypes> = realmListOf()
}
