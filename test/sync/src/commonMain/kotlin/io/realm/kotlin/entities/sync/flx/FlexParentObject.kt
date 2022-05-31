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

import io.realm.kotlin.ObjectId
import io.realm.kotlin.RealmObject
import io.realm.kotlin.annotations.PrimaryKey

/**
 * Object used when testing Flexible Sync.
 */
class FlexParentObject() : RealmObject {
    constructor(section: Int) : this() {
        this.section = section
    }
    @PrimaryKey
    var _id: ObjectId = ObjectId.create()
    var section: Int = 0
    var name: String = ""
    var age: Int = 42
}
