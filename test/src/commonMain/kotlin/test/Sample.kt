/*
 * Copyright 2020 Realm Inc.
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

package test

import io.realm.realmList
import io.realm.RealmList
import io.realm.RealmObject

class Sample : RealmObject {
    var stringField: String = "Realm"
    var byteField: Byte = 0xA
    var charField: Char = 'a'
    var shortField: Short = 17
    var intField: Int = 42
    var longField: Long = 256
    var booleanField: Boolean = true
    var floatField: Float = 3.14f
    var doubleField: Double = 1.19840122
    var child: Sample? = null

    var stringListField: RealmList<String> = realmList()
    var byteListField: RealmList<Byte> = realmList()
    var charListField: RealmList<Char> = realmList()
    var shortListField: RealmList<Short> = realmList()
    var intListField: RealmList<Int> = realmList()
    var longListField: RealmList<Long> = realmList()
    var booleanListField: RealmList<Boolean> = realmList()
    var floatListField: RealmList<Float> = realmList()
    var doubleListField: RealmList<Double> = realmList()
    var objectListField: RealmList<Sample> = realmList()

    var nullableStringListField: RealmList<String?> = realmList()
    var nullableByteListField: RealmList<Byte?> = realmList()
    var nullableCharListField: RealmList<Char?> = realmList()
    var nullableShortListField: RealmList<Short?> = realmList()
    var nullableIntListField: RealmList<Int?> = realmList()
    var nullableLongListField: RealmList<Long?> = realmList()
    var nullableBooleanListField: RealmList<Boolean?> = realmList()
    var nullableFloatListField: RealmList<Float?> = realmList()
    var nullableDoubleListField: RealmList<Double?> = realmList()
}
