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

package io.realm.kotlin.entities.sync.flx

import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

/**
 * Object used when testing Flexible Sync.
 */
class FlexParentObject() : RealmObject {
    constructor(section: Int) : this() {
        this.section = section
    }
    @PrimaryKey
    @Suppress("VariableNaming")
    var _id: ObjectId = ObjectId.create()
    var section: Int = 0
    var name: String = ""
    @Suppress("MagicNumber")
    var age: Int = 42
    var child: FlexChildObject? = null
    var embedded: FlexEmbeddedObject? = null
}
