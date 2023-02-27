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

package io.realm.kotlin.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.realm.kotlin.internal.interop.ErrorCode
import io.realm.kotlin.internal.interop.realm_errno_e
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

/**
 * Test that non-sync enum wrappers map all values, which is relevant when the Core API changes.
 * This test is isolated to the JVM as Native doesn't have the reflection capabilities required
 * to test this efficiently.
 */
@RunWith(AndroidJUnit4::class)
class EnumTests {

    @BeforeTest
    fun setup() {
        System.loadLibrary("realmc")
    }

    /**
     * Monitors for changes in to Exception types defined in Core.
     *
     * It checks that all the error code values defined in realm_errno_e are mapped by ErrorCode
     */
    @Test
    fun coreExceptionTypes_watchdog() {
        val coreErrorCodesValues = realm_errno_e::class.java.fields
            .map { it.getInt(null) }
            .toSet()

        val errorCodeValues = ErrorCode.values()
            .map {
                it.nativeValue
            }
            .toSet()

        // Validate we have a different exception defined for each core native value.
        assertEquals(coreErrorCodesValues, errorCodeValues)
    }
}
