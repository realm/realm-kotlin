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

package io.realm.kotlin.internal.interop

// The C-API uses the empty string as value for string properties that are not set
const val SCHEMA_NO_VALUE = ""

data class ClassInfo(
    val name: String,
    val primaryKey: String = SCHEMA_NO_VALUE,
    val numProperties: Long,
    val numComputedProperties: Long = 0,
    val key: ClassKey = INVALID_CLASS_KEY,
    val flags: Int = ClassFlags.RLM_CLASS_NORMAL
) {

    val isEmbedded = flags and ClassFlags.RLM_CLASS_EMBEDDED != 0

    companion object {
        // Convenience wrapper to ease maintaining compiler plugin
        fun create(
            name: String,
            primaryKey: String?,
            numProperties: Long,
            isEmbedded: Boolean = false,
        ): ClassInfo {
            val flags: Int = when {
                isEmbedded -> ClassFlags.RLM_CLASS_EMBEDDED
                else -> ClassFlags.RLM_CLASS_NORMAL
            }
            return ClassInfo(name, primaryKey ?: SCHEMA_NO_VALUE, numProperties, 0, flags = flags)
        }
    }
}
