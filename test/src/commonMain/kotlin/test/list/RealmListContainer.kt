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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package test.list

import io.realm.realmList
import io.realm.RealmList
import io.realm.RealmObject
import kotlin.reflect.KMutableProperty1

class RealmListContainer : RealmObject {

    var stringField: String = "Realm"

    var stringListField: RealmList<String> = realmList()
    var byteListField: RealmList<Byte> = realmList()
    var charListField: RealmList<Char> = realmList()
    var shortListField: RealmList<Short> = realmList()
    var intListField: RealmList<Int> = realmList()
    var longListField: RealmList<Long> = realmList()
    var booleanListField: RealmList<Boolean> = realmList()
    var floatListField: RealmList<Float> = realmList()
    var doubleListField: RealmList<Double> = realmList()
    var objectListField: RealmList<RealmListContainer> = realmList()

    var nullableStringListField: RealmList<String?> = realmList()
    var nullableByteListField: RealmList<Byte?> = realmList()
    var nullableCharListField: RealmList<Char?> = realmList()
    var nullableShortListField: RealmList<Short?> = realmList()
    var nullableIntListField: RealmList<Int?> = realmList()
    var nullableLongListField: RealmList<Long?> = realmList()
    var nullableBooleanListField: RealmList<Boolean?> = realmList()
    var nullableFloatListField: RealmList<Float?> = realmList()
    var nullableDoubleListField: RealmList<Double?> = realmList()

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
            Double::class to RealmListContainer::nullableDoubleListField as KMutableProperty1<RealmListContainer, RealmList<Any?>>
        ).toMap()
    }
}

// Circular dependencies with lists
class Level1 : RealmObject {
    var name: String = ""
    var list: RealmList<Level2> = realmList()
}

class Level2 : RealmObject {
    var name: String = ""
    var list: RealmList<Level3> = realmList()
}

class Level3 : RealmObject {
    var name: String = ""
    var list: RealmList<Level1> = realmList()
}
