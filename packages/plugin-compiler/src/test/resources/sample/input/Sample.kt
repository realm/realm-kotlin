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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sample.input

import io.realm.kotlin.EmbeddedRealmObject
import io.realm.kotlin.ObjectId
import io.realm.kotlin.RealmInstant
import io.realm.kotlin.RealmList
import io.realm.kotlin.RealmObject
import io.realm.kotlin.annotations.Ignore
import io.realm.kotlin.annotations.Index
import io.realm.kotlin.annotations.PrimaryKey
import io.realm.kotlin.realmListOf
import java.util.*

class Sample : RealmObject {

    @PrimaryKey
    var id: Long = Random().nextLong()

    @Ignore
    var ignoredString: String = ""

    @Transient
    var transientString: String = ""

    // Primitive types
    @Index
    var stringField: String? = "Realm"
    var byteField: Byte? = 0xA
    var charField: Char? = 'a'
    var shortField: Short? = 17
    @Index
    var intField: Int? = 42
    var longField: Long? = 256
    var booleanField: Boolean? = true
    var floatField: Float? = 3.14f
    var doubleField: Double? = 1.19840122
    var timestampField: RealmInstant? = RealmInstant.from(0,0)
    var objectIdField: ObjectId? = ObjectId.create()
    var child: Child? = null

    // List types
    var stringListField: RealmList<String> = realmListOf()
    var byteListField: RealmList<Byte> = realmListOf()
    var charListField: RealmList<Char> = realmListOf()
    var shortListField: RealmList<Short> = realmListOf()
    var intListField: RealmList<Int> = realmListOf()
    var longListField: RealmList<Long> = realmListOf()
    var booleanListField: RealmList<Boolean> = realmListOf()
    var floatListField: RealmList<Float> = realmListOf()
    var doubleListField: RealmList<Double> = realmListOf()
    var timestampListField: RealmList<RealmInstant> = realmListOf()
    var objectIdListField: RealmList<ObjectId> = realmListOf()
    var objectListField: RealmList<Sample> = realmListOf()
    var embeddedRealmObjectListField: RealmList<EmbeddedChild> = realmListOf()

    // Nullable list types - RealmList<RealmObject?> is not supported
    var nullableStringListField: RealmList<String?> = realmListOf()
    var nullableByteListField: RealmList<Byte?> = realmListOf()
    var nullableCharListField: RealmList<Char?> = realmListOf()
    var nullableShortListField: RealmList<Short?> = realmListOf()
    var nullableIntListField: RealmList<Int?> = realmListOf()
    var nullableLongListField: RealmList<Long?> = realmListOf()
    var nullableBooleanListField: RealmList<Boolean?> = realmListOf()
    var nullableFloatListField: RealmList<Float?> = realmListOf()
    var nullableDoubleListField: RealmList<Double?> = realmListOf()
    var nullableTimestampListField: RealmList<RealmInstant?> = realmListOf()
    var nullableObjectIdListField: RealmList<ObjectId?> = realmListOf()

    fun dumpSchema(): String = "${Sample.`io_realm_kotlin_schema`()}"
}

class Child : RealmObject {
    var name: String? = "Child-default"
}

class EmbeddedParent: RealmObject {
    var child: EmbeddedChild? = null
}

class EmbeddedChild: EmbeddedRealmObject {
    var name: String? = "Embedded-child"
}
