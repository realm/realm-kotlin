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

    var stringListField: RealmList<String> = RealmList()
    var byteListField: RealmList<Byte> = RealmList()
    var charListField: RealmList<Char> = RealmList()
    var shortListField: RealmList<Short> = RealmList()
    var intListField: RealmList<Int> = RealmList()
    var longListField: RealmList<Long> = RealmList()
    var booleanListField: RealmList<Boolean> = RealmList()
    var floatListField: RealmList<Float> = RealmList()
    var doubleListField: RealmList<Double> = RealmList()
    var objectListField: RealmList<Sample> = RealmList()

    var nullableStringListField: RealmList<String?> = RealmList()
    var nullableByteListField: RealmList<Byte?> = RealmList()
    var nullableCharListField: RealmList<Char?> = RealmList()
    var nullableShortListField: RealmList<Short?> = RealmList()
    var nullableIntListField: RealmList<Int?> = RealmList()
    var nullableLongListField: RealmList<Long?> = RealmList()
    var nullableBooleanListField: RealmList<Boolean?> = RealmList()
    var nullableFloatListField: RealmList<Float?> = RealmList()
    var nullableDoubleListField: RealmList<Double?> = RealmList()

    // For verification that references inside class is also using our modified accessors and are
    // not optimized to use the backing field directly.
    fun stringFieldGetter(): String {
        return stringField
    }
    fun stringFieldSetter(s: String) {
        stringField = s
    }
}
