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

package io.realm.example.kmmsample

import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import io.realm.kotlin.ext.realmListOf

// This class is included to make sure the compiler-plugin can handle various type, given the min/max
// version of Kotlin this project is compiled against.
class AllTypes : RealmObject {
    @PrimaryKey
    @Suppress("VariableNaming")
    var _id: String = "42"

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
    var objectField: AllTypes? = null

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
    var objectNullableField: AllTypes? = null

    // RealmLists
    var stringRealmList: RealmList<String> = realmListOf("hello world")
    var stringRealmListNullable: RealmList<String?> = realmListOf(null)
    var byteRealmList: RealmList<Byte> = realmListOf(0)
    var byteRealmListNullable: RealmList<Byte?> = realmListOf(null)
    var charRealmList: RealmList<Char> = realmListOf(0.toChar())
    var charRealmListNullable: RealmList<Char?> = realmListOf(null)
    var shortRealmList: RealmList<Short> = realmListOf(0)
    var shortRealmListNullable: RealmList<Short?> = realmListOf(null)
    var intRealmList: RealmList<Int> = realmListOf(0)
    var intRealmListNullable: RealmList<Int?> = realmListOf(null)
    var longRealmList: RealmList<Long> = realmListOf(0)
    var longRealmListNullable: RealmList<Long?> = realmListOf(null)
    var booleanRealmList: RealmList<Boolean> = realmListOf(true)
    var booleanRealmListNullable: RealmList<Boolean?> = realmListOf(null)
    var doubleRealmList: RealmList<Double> = realmListOf(0.0)
    var doubleRealmListNullable: RealmList<Double?> = realmListOf(null)
    var floatRealmList: RealmList<Float> = realmListOf(0.0.toFloat())
    var floatRealmListNullable: RealmList<Float?> = realmListOf(null)
    var realmInstantRealmList: RealmList<RealmInstant> = realmListOf(RealmInstant.MIN)
    var realmInstantRealmListNullable: RealmList<RealmInstant?> = realmListOf(null)
    var objectIdRealmList: RealmList<ObjectId> = realmListOf(ObjectId.create())
    var objectIdRealmListNullable: RealmList<ObjectId?> = realmListOf(null)
    var objectRealmList: RealmList<AllTypes> = realmListOf()
}
