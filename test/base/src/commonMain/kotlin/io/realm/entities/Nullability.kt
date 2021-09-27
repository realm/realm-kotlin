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

package io.realm.entities

import io.realm.RealmObject

class Nullability : RealmObject {
    // TODO Need to test for all types, but requires more thought on how to structure tests to ensure
    //  that we break all tests when introducing new types, etc.
    //  https://github.com/realm/realm-kotlin/issues/133
    var stringNullable: String? = null
    var stringNonNullable: String = ""
    var booleanNullable: Boolean? = null

    var byteNullable: Byte? = null
    var charNullable: Char? = null
    var shortNullable: Short? = null
    var intNullable: Int? = null
    var longNullability: Long? = null

    var floatNullable: Float? = null
    var doubleField: Double? = null
    var objectField: Nullability? = null
}


