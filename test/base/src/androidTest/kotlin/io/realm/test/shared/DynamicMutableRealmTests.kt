@file:Suppress("invisible_member", "invisible_reference")
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

package io.realm.test.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.isValid
import io.realm.get
import io.realm.entities.Sample
import io.realm.entities.migration.SampleMigrated
import io.realm.entities.primarykey.PrimaryKeyString
import io.realm.internal.asDynamicMutableRealm
import io.realm.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DynamicMutableRealmTests {
    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration.Builder(schema = setOf(Sample::class, SampleMigrated::class, PrimaryKeyString::class))
                .path("$tmpDir/default.realm").build()

        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun create() {
        val dynamicMutableRealm = realm.asDynamicMutableRealm()
        val dynamicMutableObject = dynamicMutableRealm.createObject("Sample")
        assertTrue { dynamicMutableObject.isValid() }
    }

    // FIXME Do we need this for each type?
    @Test
    fun createPrimaryKey() {
        val dynamicMutableRealm = realm.asDynamicMutableRealm()
        val dynamicMutableObject = dynamicMutableRealm.createObject("PrimaryKeyString", "PRIMARY_KY")
        assertTrue { dynamicMutableObject.isValid() }
        assertEquals("PRIMARY_KEY", dynamicMutableObject.get("primaryKey"))
    }

    // FIXME Do we need this for each type?
    @Test
    fun createPrimaryKey_nullablePrimaryKey() {
        val dynamicMutableRealm = realm.asDynamicMutableRealm()
        dynamicMutableRealm.createObject("PrimaryKeyStringNullable", null)
    }

    @Test
    fun create_throwsWithPrimaryKey() {
        val dynamicMutableRealm = realm.asDynamicMutableRealm()
        assertFailsWith<IllegalArgumentException> {
            dynamicMutableRealm.createObject("SampleMigrated", "PRIMARY_KEY")
        }.run {
            assertContains(message!!, "Class does not have a primary key Failed to create object of type 'SampleMigrated'")
        }
    }

    @Test
    fun createPrimaryKey_throwsOnAbsentPrimaryKey() {
        val dynamicMutableRealm = realm.asDynamicMutableRealm()
        assertFailsWith<IllegalArgumentException> {
            dynamicMutableRealm.createObject("PrimaryKeyString")
        }.run {
            assertContains(message!!, "'PrimaryKeyString' does not have a primary key defined")
        }
    }

    @Test
    fun createPrimaryKey_throwsWithWrongPrimaryKeyType() {
        val dynamicMutableRealm = realm.asDynamicMutableRealm()
        assertFailsWith<IllegalArgumentException> {
            dynamicMutableRealm.createObject("PrimaryKeyString", 42)
        }.run {
            assertContains(message!!, "Wrong primary key type for 'PrimaryKeyString'")
        }
    }

}
