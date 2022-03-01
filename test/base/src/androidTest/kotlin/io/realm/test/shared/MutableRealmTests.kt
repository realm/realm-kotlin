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

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmResults
import io.realm.entities.Sample
import io.realm.entities.StringPropertyWithPrimaryKey
import io.realm.entities.link.Child
import io.realm.entities.link.Parent
import io.realm.query
import io.realm.query.RealmQuery
import io.realm.query.RealmSingleQuery
import io.realm.test.platform.PlatformUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
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
                Sample::class
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
    fun copyToRealmWithDefaults() {
        realm.writeBlocking { copyToRealm(Parent()) }
        val parents = realm.query<Parent>().find()
        assertEquals(1, parents.size)
        assertEquals("N.N.", parents[0].name)
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
    fun delete_realmObject() {
        realm.writeBlocking {
            val liveObject = copyToRealm(Parent())
            assertEquals(1, query<Parent>().count().find())
            delete(liveObject)
            assertEquals(0, query<Parent>().count().find())
        }
    }

    @Test
    fun delete_realmList() {
        realm.writeBlocking {
            val liveObject = copyToRealm(Sample()).apply {
                stringField = "PARENT"
                objectListField.add(Sample())
                objectListField.add(Sample())
                objectListField.add(Sample())
                stringListField.add("ELEMENT1")
                stringListField.add("ELEMENT2")
            }

            assertEquals(4, query<Sample>().count().find())
            assertEquals(3, liveObject.objectListField.size)
            assertEquals(2, liveObject.stringListField.size)
            delete(liveObject.objectListField)
            delete(liveObject.stringListField)
            assertEquals(0, liveObject.objectListField.size)
            assertEquals(0, liveObject.stringListField.size)
            assertEquals(1, query<Sample>().count().find())
        }
    }

    @Test
    fun delete_realmQuery() {
        realm.writeBlocking {
            for (i in 0..9) {
                copyToRealm(Sample().apply { intField = i % 2 })
            }
            assertEquals(10, query<Sample>().count().find())
            val deleteable: RealmQuery<Sample> = query<Sample>("intField = 1")
            delete(deleteable)
            val samples: RealmResults<Sample> = query<Sample>().find()
            assertEquals(5, samples.size)
            for (sample in samples) {
                assertEquals(0, sample.intField)
            }
        }
    }

    @Test
    fun delete_realmSingleQuery() {
        realm.writeBlocking {
            for (i in 0..3) {
                copyToRealm(Sample().apply { intField = i })
            }
            assertEquals(4, query<Sample>().count().find())
            val singleQuery: RealmSingleQuery<Sample> = query<Sample>("intField = 1").first()
            delete(singleQuery)
            val samples: RealmResults<Sample> = query<Sample>().find()
            assertEquals(3, samples.size)
            for (sample in samples) {
                assertNotEquals(1, sample.intField)
            }
        }
    }

    @Test
    fun delete_realmResult() {
        realm.writeBlocking {
            for (i in 0..9) {
                copyToRealm(Sample().apply { intField = i % 2 })
            }
            assertEquals(10, query<Sample>().count().find())
            val deleteable: RealmResults<Sample> = query<Sample>("intField = 1").find()
            delete(deleteable)
            val samples: RealmResults<Sample> = query<Sample>().find()
            assertEquals(5, samples.size)
            for (sample in samples) {
                assertEquals(0, sample.intField)
            }
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
