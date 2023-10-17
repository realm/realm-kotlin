/*
 * Copyright 2023 Realm Inc.
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
package io.realm.kotlin.test.common.utils

import io.realm.kotlin.internal.platform.directoryExists
import io.realm.kotlin.test.platform.PlatformUtils
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PlatformUtilsTests {
    @Test
    fun createTempDir_createDifferentDirs() {
        val testDir1 = PlatformUtils.createTempDir("test-dir")
        val testDir2 = PlatformUtils.createTempDir("test-dir")

        assertTrue(directoryExists(testDir1))
        assertTrue(directoryExists(testDir2))

        assertNotEquals(testDir1, testDir2)
    }

    @Test
    fun createTempDir_deleteDifferentDirs() {
        val testDir = PlatformUtils.createTempDir("test-dir")
        assertTrue(directoryExists(testDir))
        PlatformUtils.deleteTempDir(testDir)
        assertFalse(directoryExists(testDir))
    }
}
