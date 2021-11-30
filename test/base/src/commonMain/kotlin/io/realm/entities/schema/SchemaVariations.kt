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

package io.realm.entities.schema

import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.Index
import io.realm.annotations.PrimaryKey
import io.realm.entities.Sample
import io.realm.realmListOf

class SchemaVariations : RealmObject {
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

    @Index
    var nullableString: String? = "Realm"
    var nullableRealmObject: Sample? = null

    var stringList: RealmList<String> = realmListOf()

    var nullableStringList: RealmList<String?> = realmListOf()
}
