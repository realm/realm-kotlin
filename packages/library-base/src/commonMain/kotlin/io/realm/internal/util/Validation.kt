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

package io.realm.internal.util

/**
 * Collection of validation methods to ensure uniform input validation.
 */
internal object Validation {
    fun illegalArgument(message: String): Nothing = throw IllegalArgumentException(message)

    inline fun <reified T> checkType(value: Any?, name: String): T {
        if (value !is T) {
            illegalArgument("Argument '$name' must be of type ${T::class.simpleName}")
        }
        return value
    }

    fun isEmptyString(str: String?): Boolean {
        return str == null || str.length == 0
    }

    fun checkEmpty(value: String?, name: String): String {
        if (isEmptyString(value)) {
            illegalArgument("Argument '$name' must be non-empty")
        }
        return value!!
    }
}
