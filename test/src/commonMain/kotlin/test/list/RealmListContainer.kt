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

package test.list

import io.realm.RealmList
import io.realm.RealmObject

class RealmListContainer : RealmObject {

    var stringField: String = "Realm"

    var stringListField: RealmList<String> = RealmList()
    var byteListField: RealmList<Byte> = RealmList()
    var charListField: RealmList<Char> = RealmList()
    var shortListField: RealmList<Short> = RealmList()
    var intListField: RealmList<Int> = RealmList()
    var longListField: RealmList<Long> = RealmList()
    var booleanListField: RealmList<Boolean> = RealmList()
    var floatListField: RealmList<Float> = RealmList()
    var doubleListField: RealmList<Double> = RealmList()
    var objectListField: RealmList<RealmListContainer> = RealmList()

    var nullableStringListField: RealmList<String?> = RealmList()
    var nullableByteListField: RealmList<Byte?> = RealmList()
    var nullableCharListField: RealmList<Char?> = RealmList()
    var nullableShortListField: RealmList<Short?> = RealmList()
    var nullableIntListField: RealmList<Int?> = RealmList()
    var nullableLongListField: RealmList<Long?> = RealmList()
    var nullableBooleanListField: RealmList<Boolean?> = RealmList()
    var nullableFloatListField: RealmList<Float?> = RealmList()
    var nullableDoubleListField: RealmList<Double?> = RealmList()
}
