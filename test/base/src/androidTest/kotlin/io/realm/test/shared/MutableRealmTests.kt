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
package io.realm.test.shared

import io.realm.MutableRealm
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmInstant
import io.realm.delete
import io.realm.entities.SampleWithPrimaryKey
import io.realm.entities.StringPropertyWithPrimaryKey
import io.realm.entities.link.Child
import io.realm.entities.link.Parent
import io.realm.query
import io.realm.test.platform.PlatformUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MutableRealmTests {

    private lateinit var configuration: RealmConfiguration
    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(
            schema = setOf(
                Parent::class,
                Child::class,
                StringPropertyWithPrimaryKey::class,
                SampleWithPrimaryKey::class
            )
        ).path("$tmpDir/default.realm").build()
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
    fun copyToRealm_none_withDefaults() {
        realm.writeBlocking { copyToRealm(Parent()) }
        val parents = realm.query<Parent>().find()
        assertEquals(1, parents.size)
        assertEquals("N.N.", parents[0].name)
    }

    @Test
    fun copyToRealm_none_throwsOnDuplicatePrimaryKey() {
        realm.writeBlocking {
            copyToRealm(SampleWithPrimaryKey())
            assertFailsWith<IllegalArgumentException> {
                copyToRealm(SampleWithPrimaryKey())
            }
        }
        assertEquals(1, realm.query<SampleWithPrimaryKey>().find().size)
    }

    @Test
    fun set_throwsOnDuplicatePrimaryKey() {
        realm.writeBlocking {
            val sample = copyToRealm(SampleWithPrimaryKey())
            assertFailsWith<IllegalArgumentException> {
                sample.child = SampleWithPrimaryKey()
            }
        }
        assertEquals(1, realm.query<SampleWithPrimaryKey>().find().size)
    }

    @Test
    fun copyToRealm_update() {
        realm.writeBlocking {
            val obj = StringPropertyWithPrimaryKey()
            copyToRealm(obj.apply { id = "PRIMARY_KEY" })

            obj.apply { this.value = "UPDATED_VALUE" }
            copyToRealm(obj, MutableRealm.UpdatePolicy.ALL)
        }

        val objects = realm.query<StringPropertyWithPrimaryKey>().find()
        assertEquals(1, objects.size)
        objects[0].run {
            assertEquals("PRIMARY_KEY", id)
            assertEquals("UPDATED_VALUE", value)
        }
    }

    @Test
    fun copyToRealm_update_noPrimaryKeyField() {
        realm.writeBlocking {
            copyToRealm(Parent(), MutableRealm.UpdatePolicy.ALL)
        }
        assertEquals(1, realm.query<Parent>().find().size)
    }

    @Test
    @Suppress("LongMethod")
    fun copyToRealm_update_allTypes() {
        val sample = SampleWithPrimaryKey().apply {
            primaryKey = 1
        }
        realm.writeBlocking { copyToRealm(sample) }

        // TODO Vefiry that we cover all types
        sample.apply {
            stringField = "UPDATED"
            byteField = 0x10
            charField = 'b'
            shortField = 255
            intField = 255
            longField = 1024
            booleanField = false
            floatField = 42.42f
            doubleField = 42.42
            timestampField = RealmInstant.fromEpochSeconds(42, 42)
            child = this
            stringListField.add("UPDATED")
            byteListField.add(0x10)
            charListField.add('b')
            shortListField.add(255)
            intListField.add(255)
            longListField.add(1024)
            booleanListField.add(false)
            floatListField.add(3.14f)
            doubleListField.add(3.14)
            timestampListField.add(RealmInstant.fromEpochSeconds(42, 42))
            objectListField.add(this)
            nullableStringListField.add(null)
            nullableByteListField.add(null)
            nullableCharListField.add(null)
            nullableShortListField.add(null)
            nullableIntListField.add(null)
            nullableLongListField.add(null)
            nullableBooleanListField.add(null)
            nullableFloatListField.add(null)
            nullableDoubleListField.add(null)
            nullableTimestampListField.add(null)
        }
        realm.writeBlocking { copyToRealm(sample, MutableRealm.UpdatePolicy.ALL) }.run {
            assertEquals("UPDATED", stringField)
            assertEquals(0x10, byteField)
            assertEquals('b', charField)
            assertEquals(255, shortField)
            assertEquals(255, intField)
            assertEquals(1024, longField)
            assertEquals(false, booleanField)
            assertEquals(42.42f, floatField)
            assertEquals(42.42, doubleField)
            assertEquals(RealmInstant.fromEpochSeconds(42, 42), timestampField)

            // FIXME Lacks nullable types
            assertEquals(primaryKey, child!!.primaryKey)

            assertEquals("UPDATED", stringListField[0])
            assertEquals(0x10, byteListField[0])
            assertEquals('b', charListField[0])
            assertEquals(255, shortListField[0])
            assertEquals(255, intListField[0])
            assertEquals(1024, longListField[0])
            assertEquals(false, booleanListField[0])
            assertEquals(3.14f, floatListField[0])
            assertEquals(3.14, doubleListField[0])
            assertEquals(RealmInstant.fromEpochSeconds(42, 42), timestampListField[0])
            assertEquals(primaryKey, objectListField[0].primaryKey)

            assertEquals(null, nullableStringListField[0])
            assertEquals(null, nullableByteListField[0])
            assertEquals(null, nullableCharListField[0])
            assertEquals(null, nullableShortListField[0])
            assertEquals(null, nullableIntListField[0])
            assertEquals(null, nullableLongListField[0])
            assertEquals(null, nullableBooleanListField[0])
            assertEquals(null, nullableFloatListField[0])
            assertEquals(null, nullableDoubleListField[0])
            assertEquals(null, nullableTimestampListField[0])
        }
    }

    @Test
    fun copyToRealm_update_cyclicObject() {
        val sample1 = SampleWithPrimaryKey().apply {
            primaryKey = 1
            stringField = "One"
        }
        val sample2 = SampleWithPrimaryKey().apply {
            primaryKey = 2
            stringField = "Two"
        }
        sample1.child = sample2
        sample2.child = sample1
        realm.writeBlocking {
            copyToRealm(sample1)
        }.run {
            assertEquals(1, primaryKey)
            assertEquals("One", stringField)
            child?.run {
                assertEquals(2, primaryKey)
                assertEquals("Two", stringField)
            }
        }

        sample1.stringField = "Three"
        sample2.stringField = "Four"

        realm.writeBlocking {
            copyToRealm(sample1, MutableRealm.UpdatePolicy.ALL)
        }.run {
            assertEquals(1, primaryKey)
            assertEquals("Three", stringField)
            child?.run {
                assertEquals(2, primaryKey)
                assertEquals("Four", stringField)
            }
        }
    }

    @Test
    fun copyToRealm_update_nonPrimaryKeyObject() {
        realm.writeBlocking {
            copyToRealm(Parent(), MutableRealm.UpdatePolicy.ALL)
        }
    }

    // The cache maintained during import doesn't recognize previously imported object
    @Ignore
    @Test
    fun copyToRealm_update_realmJavaBug4957() {
        val parent = SampleWithPrimaryKey().apply {
            primaryKey = 0

            val listElement = SampleWithPrimaryKey().apply { primaryKey = 1 }
            objectListField.add(listElement)

            child = SampleWithPrimaryKey().apply {
                primaryKey = 0
                objectListField.add(listElement)
                child = this
            }
        }
        realm.writeBlocking {
            copyToRealm(parent, MutableRealm.UpdatePolicy.ALL)
        }.run {
            assertEquals(1, objectListField.size)
        }
    }

    @Test
    fun copyToRealm_throwsOnManagedObjectFromDifferentVersion() {
        val frozenParent = realm.writeBlocking { copyToRealm(Parent()) }

        realm.writeBlocking {
            assertFailsWith<IllegalArgumentException> {
                copyToRealm(frozenParent)
            }
        }
    }

    @Test
    fun copyToRealm_throwsWithDeletedObject() {
        val frozenParent = realm.writeBlocking { copyToRealm(Parent()) }
        realm.writeBlocking {
            assertFailsWith<IllegalArgumentException> {
                copyToRealm(frozenParent)
            }
        }
    }

    @Test
    fun set_throwsOnManagedObjectFromDifferentVersion() {
        val child = realm.writeBlocking { copyToRealm(Child()).apply { name = "CHILD" } }
        realm.writeBlocking {
            val parent = copyToRealm(Parent())
            assertFailsWith<IllegalArgumentException> {
                parent.child = child
            }
        }
    }

    @Test
    fun set_throwWithDeletedObject() {
        val child = realm.writeBlocking { copyToRealm(Child()).apply { name = "CHILD" } }
        realm.writeBlocking {
            findLatest(child)?.delete()
            val parent = copyToRealm(Parent())
            assertFailsWith<IllegalArgumentException> {
                parent.child = child
            }
        }
    }

    @Test
    fun writeReturningUnmanaged() {
        assertTrue(realm.writeBlocking { Parent() } is Parent)
    }

    @Test
    fun cancelingWrite() {
        assertEquals(0, realm.query<Parent>().find().size)
        realm.writeBlocking {
            copyToRealm(Parent())
            cancelWrite()
        }
        assertEquals(0, realm.query<Parent>().count().find())
    }

    @Test
    fun cancellingWriteTwiceThrows() {
        realm.writeBlocking {
            cancelWrite()
            assertFailsWith<IllegalStateException> {
                cancelWrite()
            }
        }
    }

    @Test
    fun findLatest_basic() {
        val instance = realm.writeBlocking { copyToRealm(StringPropertyWithPrimaryKey()) }

        realm.writeBlocking {
            val latest = findLatest(instance)
            assertNotNull(latest)
            assertEquals(instance.id, latest.id)
        }
    }

    @Test
    fun findLatest_updated() {
        val updatedValue = "UPDATED"
        val instance = realm.writeBlocking { copyToRealm(StringPropertyWithPrimaryKey()) }
        assertNull(instance.value)

        realm.writeBlocking {
            val latest = findLatest(instance)
            assertNotNull(latest)
            assertEquals(instance.id, latest.id)
            latest.value = updatedValue
        }
        assertNull(instance.value)

        realm.writeBlocking {
            val latest = findLatest(instance)
            assertNotNull(latest)
            assertEquals(instance.id, latest.id)
            assertEquals(updatedValue, latest.value)
        }
    }

    @Test
    fun findLatest_deleted() {
        val instance = realm.writeBlocking { copyToRealm(StringPropertyWithPrimaryKey()) }

        realm.writeBlocking {
            findLatest(instance)?.let {
                delete(it)
            }
        }
        realm.writeBlocking {
            assertNull(findLatest(instance))
        }
    }

    @Test
    fun findLatest_identityForLiveObject() {
        realm.writeBlocking {
            val instance = copyToRealm(StringPropertyWithPrimaryKey())
            val latest = findLatest(instance)
            assert(instance === latest)
        }
    }

    @Test
    fun findLatest_unmanagedThrows() {
        realm.writeBlocking {
            assertFailsWith<IllegalArgumentException> {
                val latest = findLatest(StringPropertyWithPrimaryKey())
            }
        }
    }

    @Test
    fun findLatest_inLongHistory() {
        runBlocking {
            val child = realm.write { copyToRealm(Child()) }
            for (i in 1..10) {
                realm.write {
                    assertNotNull(findLatest(child))
                }
                delay(100)
            }
        }
    }

    @Test
    fun delete() {
        realm.writeBlocking {
            val liveObject = copyToRealm(Parent())
            assertEquals(1, query<Parent>().count().find())
            delete(liveObject)
            assertEquals(0, query<Parent>().count().find())
        }
    }

    @Test
    fun delete_deletedObjectThrows() {
        realm.writeBlocking {
            val liveObject = copyToRealm(Parent())
            assertEquals(1, query<Parent>().count().find())
            delete(liveObject)
            assertEquals(0, query<Parent>().count().find())
            assertFailsWith<IllegalArgumentException> {
                delete(liveObject)
            }
        }
    }

    @Test
    fun delete_unmanagedObjectsThrows() {
        realm.writeBlocking {
            assertFailsWith<IllegalArgumentException> {
                delete(Parent())
            }
        }
    }

    @Test
    fun delete_frozenObjectsThrows() {
        val frozenObj = realm.writeBlocking {
            copyToRealm(Parent())
        }
        realm.writeBlocking {
            assertFailsWith<IllegalArgumentException> {
                delete(frozenObj)
            }
        }
    }
}
