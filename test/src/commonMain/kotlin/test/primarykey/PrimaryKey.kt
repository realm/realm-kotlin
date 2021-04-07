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

package test.primarykey

import io.realm.PrimaryKey
import io.realm.RealmObject
import kotlin.random.Random
import kotlin.random.nextULong

class NoPrimaryKey: RealmObject {
    var nonPrimaryKey: String = Random.nextULong().toString()
}

class PrimaryKeyString: RealmObject {
    @PrimaryKey
    var primaryKey: String = Random.nextULong().toString()
}

class PrimaryKeyStringNullable : RealmObject {
    @PrimaryKey
    var primaryKey: String? = Random.nextULong().toString()
}
