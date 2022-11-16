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

package `invalid-sample`.input

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.RealmField

class InvalidSample : RealmObject {

    // Invalid @RealmField annotations

    // Empty internal name
    @RealmField("")
    var publicName1: String? = ""

    // Duplicate names (annotation and its corresponding field)
    @RealmField("duplicateName1")
    var duplicateName1: String? = ""

    // Duplicate names (annotation and a lexically later field)
    @RealmField("duplicateName2")
    var publicName2: String? = ""
    var duplicateName2: String? = ""

    // Duplicate names (annotation and a lexically previous field)
    var duplicateName3: String? = ""
    @RealmField("duplicateName3")
    var publicName3: String? = ""

    // Duplicate names (annotation and another annotation)
    @RealmField("duplicateName4")
    var publicName4: String? = ""
    @RealmField("duplicateName4")
    var publicName5: String? = ""
}
