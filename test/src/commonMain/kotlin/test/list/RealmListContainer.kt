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

import io.realm.RealmList
import io.realm.RealmObject
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KType
import kotlin.reflect.typeOf

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

    companion object {

        @Suppress("UNCHECKED_CAST")
        val nonNullableProperties: Map<KClassifier, KMutableProperty1<RealmListContainer, RealmList<Any>>> =
            RealmListContainer::class.members
                .filter {
                    it is KMutableProperty1<*, *> &&
                            it.returnType.arguments.isNotEmpty() &&
                            !it.returnType.arguments[0].type!!.isMarkedNullable
                }.map {
                    getClassifier(it) to (it as KMutableProperty1<RealmListContainer, RealmList<Any>>)
                }.toMap()

        @Suppress("UNCHECKED_CAST")
        val nullableProperties: Map<KClassifier, KMutableProperty1<RealmListContainer, RealmList<Any?>>> =
            RealmListContainer::class.members
                .filter {
                    it is KMutableProperty1<*, *> &&
                            it.returnType.arguments.isNotEmpty() &&
                            it.returnType.arguments[0].type!!.isMarkedNullable
                }.map {
                    getClassifier(it) to (it as KMutableProperty1<RealmListContainer, RealmList<Any?>>)
                }.toMap()

        @OptIn(ExperimentalStdlibApi::class)
        private fun getClassifier(callable: KCallable<*>): KClassifier {
            val realmObjectType: KType = typeOf<RealmObject>()
            val supertypes =
                (callable.returnType.arguments[0].type!!.classifier!! as KClass<*>).supertypes
            return when {
                supertypes.contains(realmObjectType) -> RealmObject::class
                else -> callable.returnType.arguments[0].type!!.classifier!!
            }
        }
    }
}
