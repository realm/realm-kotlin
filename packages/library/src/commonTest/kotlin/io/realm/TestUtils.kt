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

package io.realm

import io.realm.model.Person
import io.realm.runtimeapi.RealmModel
import kotlin.reflect.KClass

// FIXME MEDIATOR Work around lack of reflection/codegen in Kotlin/Native, this should be solved by adding a compiler plugin
object TestUtils {
    fun factory(): (KClass<out RealmModel>) -> RealmModel {
        return {
            if (Person::class == it) Person()
            else error("Unsupported type: ${it.simpleName}")
        }
    }
}
