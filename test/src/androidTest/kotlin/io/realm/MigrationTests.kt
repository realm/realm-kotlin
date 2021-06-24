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

import io.realm.util.PlatformUtils
import test.Sample
import test.link.Child
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MigrationTests {

    lateinit var tmpDir: String
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    /**
     * DEBUGGING NATIVE WITH LLDB:
     * - Location of the kexe file that contains this test - make sure to compile the test beforehand:
     * test/build/bin/macos/debugTest/test.kexe
     *
     * - Open:
     * lldb lldb test/build/bin/macos/debugTest/test.kexe
     * - Set breakpoints, e.g.:
     * breakpoint set --file realm_coordinator.cpp --line 288
     * - Run ONLY the test you want:
     * r --gtest_filter="io.realm.MigrationTests.deleteOnMigration"
     * - Step into:
     * s
     * - Step over:
     * n
     * - Step out:
     * finish
     */
    @Test
    fun deleteOnMigration() {
        val path = "$tmpDir/default.realm"

        RealmConfiguration(
            path = path,
            schema = setOf(Sample::class),
            schemaVersion = 2
        ).also {
            val realm = Realm.open(it)
            println("PATH 1: ${realm.configuration.path}")
            realm.close()
        }

        RealmConfiguration(
            path = path,
            schema = setOf(Child::class),
            schemaVersion = 1
        ).also {
            val realm = Realm.open(it)
            println("PATH 2: ${realm.configuration.path}")
            realm.close()
        }
    }
}
