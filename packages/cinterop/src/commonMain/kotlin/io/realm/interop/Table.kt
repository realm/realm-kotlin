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

package io.realm.interop

// FIXME API-SCHEMA Platform independent class definition. Maybe rework into utility method called in Realm
//  object's companion schema mechanism depending on how we relate this to the actual schema/runtime
//  realm_class_info_t.
data class Table(
    val name: String,
    val primaryKey: String?,
    val flags: Set<ClassFlag> = setOf(ClassFlag.RLM_CLASS_NORMAL),
    val properties: List<Property>
)
