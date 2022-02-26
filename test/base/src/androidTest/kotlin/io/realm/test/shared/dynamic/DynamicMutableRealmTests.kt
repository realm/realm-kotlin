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

package io.realm.test.shared.dynamic

import io.realm.RealmConfiguration
import io.realm.delete
import io.realm.dynamic.DynamicMutableRealm
import io.realm.dynamic.getNullableValue
import io.realm.dynamic.getValue
import io.realm.entities.Sample
import io.realm.entities.primarykey.PrimaryKeyString
import io.realm.entities.primarykey.PrimaryKeyStringNullable
import io.realm.internal.InternalConfiguration
import io.realm.isValid
import io.realm.test.StandaloneDynamicMutableRealm
import io.realm.test.assertFailsWithMessage
import io.realm.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
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
            RealmConfiguration.Builder(
                schema = setOf(
                    Sample::class,
                    PrimaryKeyString::class,
                    PrimaryKeyStringNullable::class
                )
            )
                .path("$tmpDir/default.realm").build()

        dynamicMutableRealm =
            StandaloneDynamicMutableRealm(configuration as InternalConfiguration).apply {
                beginTransaction()
            }
    }

    @AfterTest
    fun tearDown() {
        if (this::dynamicMutableRealm.isInitialized && !dynamicMutableRealm.isClosed()) {
            (dynamicMutableRealm as StandaloneDynamicMutableRealm).close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    // TODO Add test for all BaseRealm methods

    @Test
    fun create() {
        val dynamicMutableObject = dynamicMutableRealm.createObject("Sample")
        assertTrue { dynamicMutableObject.isValid() }
    }

    // TODO Add variants for each type
    @Test
    fun createPrimaryKey() {
        val dynamicMutableObject =
            dynamicMutableRealm.createObject("PrimaryKeyString", "PRIMARY_KEY")
        assertTrue { dynamicMutableObject.isValid() }
        assertEquals("PRIMARY_KEY", dynamicMutableObject.getValue("primaryKey"))
    }

    // TODO Add variants for each type
    @Test
    fun createPrimaryKey_nullablePrimaryKey() {
        val dynamicMutableObject =
            dynamicMutableRealm.createObject("PrimaryKeyStringNullable", null)
        assertTrue { dynamicMutableObject.isValid() }
        assertNull(dynamicMutableObject.getNullableValue<String>("primaryKey"))
    }

    @Test
    fun create_throwsOnUnknownClass() {
        assertFailsWithMessage<IllegalArgumentException>("Schema does not contain a class named 'UNKNOWN_CLASS'") {
            dynamicMutableRealm.createObject("UNKNOWN_CLASS")
        }
    }

    @Test
    fun create_throwsWithPrimaryKey() {
        assertFailsWithMessage<IllegalArgumentException>("Failed to create object of type 'Sample': RealmCoreException([12]: Class does not have a primary key)") {
            dynamicMutableRealm.createObject("Sample", "PRIMARY_KEY")
        }
    }

    @Test
    fun createPrimaryKey_throwsOnAbsentPrimaryKey() {
        assertFailsWithMessage<IllegalArgumentException>("'PrimaryKeyString' does not have a primary key defined") {
            dynamicMutableRealm.createObject("PrimaryKeyString")
        }
    }

    @Test
    fun createPrimaryKey_throwsWithWrongPrimaryKeyType() {
        assertFailsWithMessage<IllegalArgumentException>("Wrong primary key type for 'PrimaryKeyString'") {
            dynamicMutableRealm.createObject("PrimaryKeyString", 42)
        }
    }

    @Test
    fun query_returnsDynamicMutableObject() {
        dynamicMutableRealm.createObject("Sample")
        val o1 = dynamicMutableRealm.query("Sample").find().first()
        o1.set("stringField", "value")
    }

    @Test
    fun query_failsOnUnknownClass() {
        assertFailsWithMessage<IllegalArgumentException>("Schema does not contain a class named 'UNKNOWN_CLASS'") {
            dynamicMutableRealm.query("UNKNOWN_CLASS")
        }
    }

    @Test
    fun findLatest() {
        val o1 = dynamicMutableRealm.createObject("Sample")
            .set("stringField", "NEW_VALUE")

        val o2 = dynamicMutableRealm.findLatest(o1)
        assertNotNull(o2)
        assertEquals("NEW_VALUE", o2.getValue("stringField"))
    }

    @Test
    fun findLatest_deleted() {
        val o1 = dynamicMutableRealm.createObject("Sample")
        o1.delete()
        val o2 = dynamicMutableRealm.findLatest(o1)
        assertNull(o2)
    }

    @Test
    fun findLatest_identityForLiveObject() {
        val instance = dynamicMutableRealm.createObject("Sample")
        val latest = dynamicMutableRealm.findLatest(instance)
        assert(instance === latest)
    }

    @Test
    fun findLatest_unmanagedThrows() {
        assertFailsWith<IllegalArgumentException> {
            dynamicMutableRealm.findLatest(Sample())
        }
    }

    // FIXME Align delete behavior with MutableRealm, until that is in place we just test the
    //  various ways of deleting objects
    //  https://github.com/realm/realm-kotlin/issues/181
    @Test
    fun delete() {
        for (i in 0..9) {
            dynamicMutableRealm.createObject("Sample").set("intField", i % 2)
        }
        dynamicMutableRealm.query("Sample").find().forEach { obj ->
            if (obj.getValue<Long>("intField") == 0L) {
                obj.delete()
            }
        }
        val samples = dynamicMutableRealm.query("Sample").find()
        assertEquals(5, samples.size)
        samples.forEach { assertEquals(1L, it.getValue("intField")) }
    }

    @Test
    fun deleteAll() {
        for (i in 0..9) {
            dynamicMutableRealm.createObject("Sample").set("intField", i % 2)
        }
        val samples = dynamicMutableRealm.query("Sample").find()
        assertEquals(10, samples.size)
        samples.delete()

        val noSamples = dynamicMutableRealm.query("Sample").find()
        assertEquals(0, noSamples.size)
    }
}
