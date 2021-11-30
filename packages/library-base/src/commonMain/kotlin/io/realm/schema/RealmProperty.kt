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

package io.realm.schema

interface RealmProperty { // Matches realm_property_info_t
    val name: String
    val type: RealmPropertyType
    // Referring to actual Kotlin KType property nullability and not Core's nullability flag
    // (though being the same for singular types and always false for Non-singular types)
    val isNullable: Boolean
}
