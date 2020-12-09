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
import io.realm.runtimeapi.RealmObject

@RealmObject
class Sample {
    var name: String? = "Realm"
    var age: Int? = 42
    var adult: Boolean? = true
    fun dumpSchema() : String = "${Sample.`$realm$schema`()}"
}
