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
package io.realm

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalUnsignedTypes
class VersionIdTests {

    @Suppress("ReplaceAssertBooleanWithAssertEquality")
    @Test
    fun compareVersions() {
        assertTrue(VersionId(0, 0) == VersionId(0, 0))
        assertTrue(VersionId(1, 0) > VersionId(0, 0))
        assertTrue(VersionId(1, 0) >= VersionId(0, 0))
        assertTrue(VersionId(1, 0) < VersionId(2, 0))
        assertTrue(VersionId(1, 0) <= VersionId(2, 0))
    }

    @Suppress("ReplaceAssertBooleanWithAssertEquality")
    @Test
    fun orderingVersionsIgnoreIndex() {
        assertFalse(VersionId(1, 2) > VersionId(1, 1))
        assertFalse(VersionId(1, 1) >= VersionId(2, 0))
        assertFalse(VersionId(1, 0) < VersionId(1, 1))
        assertFalse(VersionId(2, 0) <= VersionId(1, 1))
    }

    @Test
    fun throwsForNegativeNumbers() {
        assertFailsWith<IllegalArgumentException> { VersionId(-1, 0) }
        assertFailsWith<IllegalArgumentException> { VersionId(0, -1) }
    }
}
