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

package io.realm.internal.schema

import io.realm.internal.interop.PropertyKey

class ClassMetadata(private val className: String, private val keyMap: Map<String, PropertyKey>) {

    operator fun get(propertyName: String): PropertyKey? = keyMap[propertyName]
    fun getOrThrow(propertyName: String): PropertyKey = keyMap[propertyName] ?: throw IllegalArgumentException("Object of type '${className} doesn't have a property named '$propertyName'")
}
