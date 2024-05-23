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

package io.realm.kotlin.internal.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Collection of validation methods to ensure uniform input validation.
 */
// TODO Public due to being used in the test package
public object Validation {
    public fun illegalArgument(message: String): Nothing = throw IllegalArgumentException(message)

    public inline fun <reified T> checkType(value: Any?, name: String): T {
        if (value !is T) {
            illegalArgument("Argument '$name' must be of type ${T::class.simpleName}")
        }
        return value
    }

    /**
     * Verifies that a given argument has a given type. If yes, it will be implicitly cast
     * to that type, otherwise an IllegalArgumentException is thrown with the provided error message.
     */
    @OptIn(ExperimentalContracts::class)
    public inline fun <reified T : Any?> isType(arg: Any?, errorMessage: String = "Object '$arg' is not of type ${T::class.qualifiedName}") {
        contract {
            returns() implies (arg is T)
        }
        if (arg !is T) {
            throw IllegalArgumentException(errorMessage)
        }
    }

    public fun isEmptyString(str: String?): Boolean {
        return str == null || str.length == 0
    }

    public fun checkEmpty(value: String?, name: String): String {
        if (isEmptyString(value)) {
            illegalArgument("Argument '$name' must be non-empty.")
        }
        return value!!
    }

    public fun sdkError(message: String): Nothing {
        @Suppress("TooGenericExceptionThrown")
        throw RuntimeException(message)
    }

    public fun require(value: Boolean, lazyMessage: () -> String) {
        if (!value) {
            throw IllegalArgumentException(lazyMessage())
        }
    }
}
