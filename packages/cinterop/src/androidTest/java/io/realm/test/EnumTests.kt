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

package io.realm.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.realm.internal.interop.CoreErrorUtils
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.realm_errno_e
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

// Test that enum wrappers map all values, which is relevant when the Core API changes.
@RunWith(AndroidJUnit4::class)
class EnumTests {

    @BeforeTest
    fun setup() {
        System.loadLibrary("realmc")
    }

    /**
     * Monitors for changes in to Exception types defined in Core.
     */
    @Test
    fun coreExceptionTypes_watchdog() {
        val coreErrorNativeValues = realm_errno_e::class.java.fields
            .map { it.getInt(null) }
            .toIntArray()

        val mappedKotlinClasses = coreErrorNativeValues
            .map { nativeValue -> CoreErrorUtils.coreErrorAsThrowable(nativeValue, null)::class }
            .toSet()

        // Validate we have a different exception defined for each core native value.
        assertEquals(coreErrorNativeValues.size, mappedKotlinClasses.size)
        // Validate that there is an error defined for each exception.
        assertEquals(RealmCoreException::class.sealedSubclasses.size, coreErrorNativeValues.size)
    }

}
