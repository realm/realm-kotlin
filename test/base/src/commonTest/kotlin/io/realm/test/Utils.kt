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
package io.realm.test

import kotlin.reflect.KClass
import kotlin.test.assertFailsWith

/**
 * Assert that a statement fails with a specific Exception type AND message. The message match is
 * fuzzy, i.e. we only check that the provided message is contained within the whole exception
 * message. The match is case sensitive.
 */
fun <T : Throwable> assertFailsWithMessage(exceptionClass: KClass<T>, exceptionMessage: String, block: () -> Unit): T {
    val exception: T = assertFailsWith(exceptionClass, null, block)
    if (exception.message?.contains(exceptionMessage, ignoreCase = false) != true) {
        throw AssertionError(
            """
            The exception message did not match.
            
            Expected:
            $exceptionMessage
            
            Actual:
            ${exception.message}
            
            """.trimIndent()
        )
    }
    return exception
}

inline fun <reified T : Throwable> assertFailsWithMessage(exceptionMessage: String, noinline block: () -> Unit): T =
    assertFailsWithMessage(T::class, exceptionMessage, block)
