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
import io.realm.RealmResults
import io.realm.entities.Sample
import io.realm.entities.SampleWithPrimaryKey
import io.realm.entities.StringPropertyWithPrimaryKey
import io.realm.entities.link.Child
import io.realm.entities.link.Parent
import io.realm.query
import io.realm.query.RealmQuery
import io.realm.query.RealmSingleQuery
import io.realm.test.assertFailsWithMessage
import io.realm.test.platform.PlatformUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

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
                Sample::class,
                SampleWithPrimaryKey::class
            )
        ).directory(tmpDir).build()
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
    fun copyToRealm_updatePolicy_error_withDefaults() {
        realm.writeBlocking { copyToRealm(Parent()) }
        val parents = realm.query<Parent>().find()
        assertEquals(1, parents.size)
        assertEquals("N.N.", parents[0].name)
    }

    @Test
    fun copyToRealm_updatePolicy_error_throwsOnDuplicatePrimaryKey() {
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
                sample.nullableObject = SampleWithPrimaryKey()
            }
        }
        assertEquals(1, realm.query<SampleWithPrimaryKey>().find().size)
    }

    @Test
    fun copyToRealm_updatePolicy_all() {
        realm.writeBlocking {
            val obj = StringPropertyWithPrimaryKey()
            copyToRealm(obj.apply { id = "PRIMARY_KEY" })

            obj.apply { value = "UPDATED_VALUE" }
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
    fun copyToRealm_updatePolicy_all_nonPrimaryKeyField() {
        realm.writeBlocking {
            copyToRealm(Parent(), MutableRealm.UpdatePolicy.ALL)
        }
        assertEquals(1, realm.query<Parent>().find().size)
    }

    @Test
    @Suppress("LongMethod")
    fun copyToRealm_updatePolicy_all_allTypes() {
        realm.writeBlocking {
            copyToRealm(
                SampleWithPrimaryKey().apply {
                    primaryKey = 1
                    stringField = "ORIGINAL"
                }
            )
        }
        assertEquals(1, realm.query<SampleWithPrimaryKey>().count().find())

        // TODO Verify that we cover all types
        val sample = SampleWithPrimaryKey().apply {
            primaryKey = 1
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

            nullableStringField = "UPDATED"
            nullableByteField = 0x10
            nullableCharField = 'b'
            nullableShortField = 255
            nullableIntField = 255
            nullableLongField = 1024
            nullableBooleanField = false
            nullableFloatField = 42.42f
            nullableDoubleField = 42.42
            nullableTimestampField = RealmInstant.fromEpochSeconds(42, 42)
            nullableObject = this

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
        realm.writeBlocking { copyToRealm(sample, MutableRealm.UpdatePolicy.ALL) }

        val samples = realm.query<SampleWithPrimaryKey>().find()
        assertEquals(1, samples.size)
        samples[0].run {
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

            assertEquals("UPDATED", nullableStringField)
            assertEquals(0x10, nullableByteField)
            assertEquals('b', nullableCharField)
            assertEquals(255, nullableShortField)
            assertEquals(255, nullableIntField)
            assertEquals(1024, nullableLongField)
            assertEquals(false, nullableBooleanField)
            assertEquals(42.42f, nullableFloatField)
            assertEquals(42.42, nullableDoubleField)
            assertEquals(RealmInstant.fromEpochSeconds(42, 42), nullableTimestampField)
            assertEquals(primaryKey, nullableObject!!.primaryKey)

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
    fun copyToRealm_updatePolicy_all_cyclicObject() {
        val sample11 = SampleWithPrimaryKey().apply {
            primaryKey = 1
            stringField = "One"
        }
        val sample22 = SampleWithPrimaryKey().apply {
            primaryKey = 2
            stringField = "Two"
        }
        sample11.nullableObject = sample22
        sample22.nullableObject = sample11

        realm.writeBlocking {
            copyToRealm(sample11)
        }.run {
            assertEquals(1, primaryKey)
            assertEquals("One", stringField)
            nullableObject?.run {
                assertEquals(2, primaryKey)
                assertEquals("Two", stringField)
            } ?: fail("Object shouldn't be null")
        }

        // We need to replicate objects as we cannot update them after passing it to another thread
        // on Kotlin Native
        val sample13 = SampleWithPrimaryKey().apply {
            primaryKey = 1
            stringField = "Three"
        }
        val sample24 = SampleWithPrimaryKey().apply {
            primaryKey = 2
            stringField = "Four"
        }
        sample13.nullableObject = sample24
        sample24.nullableObject = sample13

        realm.writeBlocking {
            copyToRealm(sample13, MutableRealm.UpdatePolicy.ALL)
        }.run {
            assertEquals(1, primaryKey)
            assertEquals("Three", stringField)
            nullableObject?.run {
                assertEquals(2, primaryKey)
                assertEquals("Four", stringField)
            } ?: fail("Object shouldn't be null")
        }
    }

    @Test
    fun copyToRealm_listElements_updatePolicy_error() {
        realm.writeBlocking {
            val child = SampleWithPrimaryKey().apply {
                primaryKey = 1
                stringField = "INITIAL"
            }
            copyToRealm(child)
            child.apply { stringField = "UPDATED" }
            val container = SampleWithPrimaryKey().apply {
                primaryKey = 2
                objectListField.add(child)
            }
            assertFailsWithMessage<IllegalArgumentException>("Object with this primary key already exists") {
                copyToRealm(container, updatePolicy = MutableRealm.UpdatePolicy.ERROR)
            }
        }
        val child = realm.query<SampleWithPrimaryKey>("primaryKey = 1").find().single()
        assertEquals("INITIAL", child.stringField)
    }

    @Test
    fun copyToRealm_listElements_updatePolicy_all() {
        realm.writeBlocking {
            val child = SampleWithPrimaryKey().apply {
                primaryKey = 1
                stringField = "INITIAL"
            }
            copyToRealm(child)
            child.apply { stringField = "UPDATED" }
            val container = SampleWithPrimaryKey().apply {
                primaryKey = 2
                objectListField.add(child)
            }
            copyToRealm(container, updatePolicy = MutableRealm.UpdatePolicy.ALL)
        }
        val child = realm.query<SampleWithPrimaryKey>("primaryKey = 1").find().single()
        assertEquals("UPDATED", child.stringField)
    }

    @Test
    fun copytToRealm_existingListIsFlushed_primitiveType() {
        val child = SampleWithPrimaryKey().apply {
            primaryKey = 1
            stringField = "INITIAL"
        }
        val container = SampleWithPrimaryKey().apply {
            primaryKey = 2
            stringListField.add("ENTRY")
        }
        realm.writeBlocking {
            copyToRealm(container, MutableRealm.UpdatePolicy.ERROR)
            copyToRealm(container, MutableRealm.UpdatePolicy.ALL)
        }
        realm.query<SampleWithPrimaryKey>("primaryKey = 2").find().single().run {
            assertEquals(1, stringListField.size)
        }
    }

    @Test
    fun copytToRealm_existingListIsFlushed_realmObject() {
        val child = SampleWithPrimaryKey().apply {
            primaryKey = 1
            stringField = "INITIAL"
        }
        val container = SampleWithPrimaryKey().apply {
            primaryKey = 2
            objectListField.add(child)
        }
        realm.writeBlocking {
            copyToRealm(container, MutableRealm.UpdatePolicy.ERROR)
            copyToRealm(container, MutableRealm.UpdatePolicy.ALL)
        }
        realm.query<SampleWithPrimaryKey>("primaryKey = 2").find().single().run {
            assertEquals(1, objectListField.size)
        }
    }

    // TODO The cache maintained during import doesn't recognize previously imported object
    @Ignore // https://github.com/realm/realm-kotlin/issues/708
    @Test
    fun copyToRealm_updatePolicy_all_realmJavaBug4957() {
        val parent = SampleWithPrimaryKey().apply {
            primaryKey = 0

            val listElement = SampleWithPrimaryKey().apply { primaryKey = 1 }
            objectListField.add(listElement)

            nullableObject = SampleWithPrimaryKey().apply {
                primaryKey = 0
                objectListField.add(listElement)
                nullableObject = this
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
        realm.writeBlocking {
            val parent = copyToRealm(Parent())
            delete(parent)
            assertFailsWith<IllegalArgumentException> {
                copyToRealm(parent)
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
            findLatest(child)?.let { delete(it) }
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
            assertFailsWithMessage<IllegalArgumentException>("Cannot delete unmanaged object") {
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
