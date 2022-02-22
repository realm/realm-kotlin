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

import io.realm.DynamicMutableRealm
import io.realm.RealmConfiguration
import io.realm.delete
import io.realm.entities.Sample
import io.realm.entities.primarykey.PrimaryKeyString
import io.realm.entities.primarykey.PrimaryKeyStringNullable
import io.realm.get
import io.realm.internal.InternalConfiguration
import io.realm.isValid
import io.realm.test.DynamicMutableTransactionRealm
import io.realm.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicMutableRealmTests {
    private lateinit var tmpDir: String
    private lateinit var dynamicMutableRealm: DynamicMutableRealm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration.Builder(schema = setOf(Sample::class, PrimaryKeyString::class, PrimaryKeyStringNullable::class))
                .path("$tmpDir/default.realm").build()

        dynamicMutableRealm = DynamicMutableTransactionRealm(configuration as InternalConfiguration).apply {
            beginTransaction()
        }
    }

    @AfterTest
    fun tearDown() {
        if (this::dynamicMutableRealm.isInitialized && !dynamicMutableRealm.isClosed()) {
            (dynamicMutableRealm as DynamicMutableTransactionRealm).close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun create() {
        val dynamicMutableObject = dynamicMutableRealm.createObject("Sample")
        assertTrue { dynamicMutableObject.isValid() }
    }

    // FIXME Do we need this for each type?
    @Test
    fun createPrimaryKey() {
        val dynamicMutableObject = dynamicMutableRealm.createObject("PrimaryKeyString", "PRIMARY_KEY")
        assertTrue { dynamicMutableObject.isValid() }
        assertEquals("PRIMARY_KEY", dynamicMutableObject.get("primaryKey"))
    }

    // FIXME Do we need this for each type?
    @Test
    fun createPrimaryKey_nullablePrimaryKey() {
        dynamicMutableRealm.createObject("PrimaryKeyStringNullable", null)
    }

    @Test
    fun create_throwsOnUnknownClass() {
        assertFailsWith<IllegalArgumentException> {
            dynamicMutableRealm.createObject("UNKNOWN_CLASS")
        }.run {
            assertEquals("Schema doesn't include class 'UNKNOWN_CLASS'", message)
        }
    }

    @Test
    fun create_throwsWithPrimaryKey() {
        assertFailsWith<IllegalArgumentException> {
            dynamicMutableRealm.createObject("Sample", "PRIMARY_KEY")
        }.run {
            assertContains(message!!, "Class does not have a primary key Failed to create object of type 'Sample'")
        }
    }

    @Test
    fun createPrimaryKey_throwsOnAbsentPrimaryKey() {
        assertFailsWith<IllegalArgumentException> {
            dynamicMutableRealm.createObject("PrimaryKeyString")
        }.run {
            assertContains(message!!, "'PrimaryKeyString' does not have a primary key defined")
        }
    }

    @Test
    fun createPrimaryKey_throwsWithWrongPrimaryKeyType() {
        assertFailsWith<IllegalArgumentException> {
            dynamicMutableRealm.createObject("PrimaryKeyString", 42)
        }.run {
            assertContains(message!!, "Wrong primary key type for 'PrimaryKeyString'")
        }
    }

    @Test
    fun query_returnsDynamicMutableObject() {
        dynamicMutableRealm.createObject("Sample")
        val o1 = dynamicMutableRealm.query("Sample").find().first()
        o1.set("stringField", "value")

        val o2 = dynamicMutableRealm.query("Sample").find().first()
        assertEquals("value", o2.get("stringField"))
    }

    @Test
    fun query_failsOnUnknownClass() {
        assertFailsWith<IllegalArgumentException> {
            dynamicMutableRealm.query("UNKNOWN_CLASS")
        }.run {
            assertEquals("Schema does not contain a class named 'UNKNOWN_CLASS'", message)
        }
    }

    @Test
    fun findLatest() {
        val o1 = dynamicMutableRealm.createObject("Sample")
            .set("stringField", "NEW_VALUE")

        val o2 = dynamicMutableRealm.findLatest(o1)
        assertNotNull(o2)
        assertEquals("NEW_VALUE", o2.get("stringField"))
    }

    @Test
    fun findLatest_deleted() {
        val o1 = dynamicMutableRealm.createObject("Sample")
        o1.delete()
        val o2 = dynamicMutableRealm.findLatest(o1)
        assertNull(o2)
    }

    // fun delete() ? See comment in DynamicMutableRealm.delete
}
