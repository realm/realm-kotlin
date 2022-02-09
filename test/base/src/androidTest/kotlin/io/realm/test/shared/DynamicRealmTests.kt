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

import androidx.test.annotation.UiThreadTest
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmList
import io.realm.RealmResults
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class DynamicRealmTests {

//    @Rule
//    val looperThread: RunInLooperThread = RunInLooperThread()
//
//    @Rule
//    val configFactory: TestRealmConfigurationFactory = TestRealmConfigurationFactory()
//
//    @Rule
//    val thrown = ExpectedException.none()
//
//    private var defaultConfig: RealmConfiguration? = null
//    private var realm: DynamicRealm? = null
//
//    @Before
//    fun setUp() {
//        defaultConfig = configFactory.createConfiguration()
//
//        // Initializes schema. DynamicRealm will not do that, so let a normal Realm create the file first.
//        Realm.getInstance(defaultConfig).close()
//        realm = DynamicRealm.getInstance(defaultConfig)
//    }
//
//    @After
//    fun tearDown() {
//        if (realm != null) {
//            realm.close()
//        }
//    }
//
//    private fun populateTestRealm(realm: DynamicRealm?, objects: Int) {
//        val autoRefreshEnabled: Boolean = realm.isAutoRefresh()
//        if (autoRefreshEnabled) {
//            realm.setAutoRefresh(false)
//        }
//        realm.beginTransaction()
//        realm.deleteAll()
//        for (i in 0 until objects) {
//            val allTypes: DynamicRealmObject = realm.createObject(AllTypes.CLASS_NAME)
//            allTypes.setBoolean(AllTypes.FIELD_BOOLEAN, i % 3 == 0)
//            allTypes.setBlob(AllTypes.FIELD_BINARY, byteArrayOf(1, 2, 3))
//            allTypes.setDate(AllTypes.FIELD_DATE, Date())
//            allTypes.setDouble(AllTypes.FIELD_DOUBLE, Math.PI + i)
//            allTypes.setFloat(AllTypes.FIELD_FLOAT, 1.234567f + i)
//            allTypes.setString(AllTypes.FIELD_STRING, "test data $i")
//            allTypes.setLong(AllTypes.FIELD_LONG, i)
//            allTypes.getList(AllTypes.FIELD_REALMLIST).add(realm.createObject(Dog.CLASS_NAME))
//            allTypes.getList(AllTypes.FIELD_REALMLIST).add(realm.createObject(Dog.CLASS_NAME))
//        }
//        realm.commitTransaction()
//        if (autoRefreshEnabled) {
//            realm.setAutoRefresh(true)
//        }
//    }
//
//    // Tests that the SharedGroupManager is not reused across Realm/DynamicRealm on the same thread.
//    // This is done by starting a write transaction in one Realm and verifying that none of the data
//    // written (but not committed) is available in the other Realm.
//    @Test
//    fun separateSharedGroups() {
//        val typedRealm: Realm = Realm.getInstance(defaultConfig)
//        val dynamicRealm: DynamicRealm = DynamicRealm.getInstance(defaultConfig)
//        assertEquals(0, typedRealm.where(AllTypes::class.java).count())
//        assertEquals(0, dynamicRealm.where(AllTypes.CLASS_NAME).count())
//        typedRealm.beginTransaction()
//        try {
//            typedRealm.createObject(AllTypes::class.java)
//            assertEquals(1, typedRealm.where(AllTypes::class.java).count())
//            assertEquals(0, dynamicRealm.where(AllTypes.CLASS_NAME).count())
//            typedRealm.cancelTransaction()
//        } finally {
//            typedRealm.close()
//            dynamicRealm.close()
//        }
//    }
//
//    // Tests that Realms can only be deleted after all Typed and Dynamic instances are closed.
//    @Test
//    fun deleteRealm_ThrowsIfDynamicRealmIsOpen() {
//        realm.close() // Close Realm opened in setUp();
//        val typedRealm: Realm = Realm.getInstance(defaultConfig)
//        val dynamicRealm: DynamicRealm = DynamicRealm.getInstance(defaultConfig)
//        typedRealm.close()
//        try {
//            Realm.deleteRealm(defaultConfig)
//            Assert.fail()
//        } catch (ignored: IllegalStateException) {
//        }
//        dynamicRealm.close()
//        Assert.assertTrue(Realm.deleteRealm(defaultConfig))
//    }
//
//    // Test that Realms can only be deleted after all Typed and Dynamic instances are closed.
//    @Test
//    fun deleteRealm_throwsIfTypedRealmIsOpen() {
//        realm.close() // Close Realm opened in setUp();
//        val typedRealm: Realm = Realm.getInstance(defaultConfig)
//        val dynamicRealm: DynamicRealm = DynamicRealm.getInstance(defaultConfig)
//        dynamicRealm.close()
//        try {
//            Realm.deleteRealm(defaultConfig)
//            Assert.fail()
//        } catch (ignored: IllegalStateException) {
//        }
//        typedRealm.close()
//        Assert.assertTrue(Realm.deleteRealm(defaultConfig))
//    }
//
    // FIXME
//    @Test
//    fun createObject() {
//        realm.beginTransaction()
//        val obj: DynamicRealmObject = realm.createObject(AllTypes.CLASS_NAME)
//        realm.commitTransaction()
//        Assert.assertTrue(obj.isValid())
//    }
//

    // FIXME
//    @Test
//    fun createObject_withPrimaryKey() {
//        realm.beginTransaction()
//        val dog: DynamicRealmObject = realm.createObject(DogPrimaryKey.CLASS_NAME, 42)
//        assertEquals(42, dog.getLong("id"))
//        realm.cancelTransaction()
//    }
//
    // FIXME
//    @Test
//    fun createObject_withNullStringPrimaryKey() {
//        realm.beginTransaction()
//        realm.createObject(PrimaryKeyAsString.CLASS_NAME, null as String?)
//        realm.commitTransaction()
//        assertEquals(1, realm.where(PrimaryKeyAsString.CLASS_NAME).equalTo(PrimaryKeyAsString.FIELD_PRIMARY_KEY, null as String?).count())
//    }
//
    // FIXME
//    @Test
//    fun createObject_withNullBytePrimaryKey() {
//        realm.beginTransaction()
//        realm.createObject(PrimaryKeyAsBoxedByte.CLASS_NAME, null as Byte?)
//        realm.commitTransaction()
//        assertEquals(1, realm.where(PrimaryKeyAsBoxedByte.CLASS_NAME).equalTo(PrimaryKeyAsBoxedByte.FIELD_PRIMARY_KEY, null as Byte?).count())
//    }
//
    // FIXME
//    @Test
//    fun createObject_withNullShortPrimaryKey() {
//        realm.beginTransaction()
//        realm.createObject(PrimaryKeyAsBoxedShort.CLASS_NAME, null as Short?)
//        realm.commitTransaction()
//        assertEquals(1, realm.where(PrimaryKeyAsBoxedShort.CLASS_NAME).equalTo(PrimaryKeyAsBoxedShort.FIELD_PRIMARY_KEY, null as Short?).count())
//    }
//
    // FIXME
//    @Test
//    fun createObject_withNullIntegerPrimaryKey() {
//        realm.beginTransaction()
//        realm.createObject(PrimaryKeyAsBoxedInteger.CLASS_NAME, null as Int?)
//        realm.commitTransaction()
//        assertEquals(1, realm.where(PrimaryKeyAsBoxedInteger.CLASS_NAME).equalTo(PrimaryKeyAsBoxedInteger.FIELD_PRIMARY_KEY, null as Int?).count())
//    }
//
//    @Test
    // FIXME
//    fun createObject_withNullLongPrimaryKey() {
//        realm.beginTransaction()
//        realm.createObject(PrimaryKeyAsBoxedLong.CLASS_NAME, null as Long?)
//        realm.commitTransaction()
//        assertEquals(1, realm.where(PrimaryKeyAsBoxedLong.CLASS_NAME).equalTo(PrimaryKeyAsBoxedLong.FIELD_PRIMARY_KEY, null as Long?).count())
//    }
//
//    @Test(expected = IllegalArgumentException::class)
    // FIXME
//    fun createObject_illegalPrimaryKeyValue() {
//        realm.beginTransaction()
//        realm.createObject(DogPrimaryKey.CLASS_NAME, "bar")
//    }
//
//    @Test(expected = RealmException::class)
    // FIXME
//    fun createObject_absentPrimaryKeyThrows() {
//        realm.beginTransaction()
//        realm.createObject(DogPrimaryKey.CLASS_NAME)
//    }
//
//    @Test
    // FIXME
//    fun where() {
//        realm.beginTransaction()
//        realm.createObject(AllTypes.CLASS_NAME)
//        realm.commitTransaction()
//        val results: RealmResults<DynamicRealmObject> = realm.where(AllTypes.CLASS_NAME).findAll()
//        Assert.assertEquals(1, results.size.toLong())
//    }
//
//    @Test(expected = IllegalArgumentException::class)
    // FIXME
//    fun delete_type_invalidName() {
//        realm.beginTransaction()
//        realm.delete("I don't exist")
//    }
//
//    @Test(expected = IllegalStateException::class)
//    fun delete_type_outsideTransactionClearOutsideTransactionThrows() {
    // FIXME
//        realm.delete(AllTypes.CLASS_NAME)
//    }
//
//    @Test
//    fun delete_type() {
//        realm.beginTransaction()
//        realm.createObject(AllTypes.CLASS_NAME)
//        realm.commitTransaction()
//        assertEquals(1, realm.where(AllTypes.CLASS_NAME).count())
//        realm.beginTransaction()
//        realm.delete(AllTypes.CLASS_NAME)
//        realm.commitTransaction()
//        assertEquals(0, realm.where(AllTypes.CLASS_NAME).count())
//    }
//
//    @Test(expected = IllegalArgumentException::class)
//    fun executeTransaction_null() {
//        realm.executeTransaction(null)
//    }
//
//    @Test
//    fun executeTransaction() {
//        assertEquals(0, realm.where(Owner.CLASS_NAME).count())
//        realm.executeTransaction(object : Transaction() {
//            fun execute(realm: DynamicRealm) {
//                val owner: DynamicRealmObject = realm.createObject(Owner.CLASS_NAME)
//                owner.setString("name", "Owner")
//            }
//        })
//        val allObjects: RealmResults<DynamicRealmObject> = realm.where(Owner.CLASS_NAME).findAll()
//        Assert.assertEquals(1, allObjects.size.toLong())
//        assertEquals("Owner", allObjects[0].getString("name"))
//    }
//
//    @Test
//    fun executeTransaction_cancelled() {
//        val thrownException = AtomicReference<RuntimeException?>(null)
//        assertEquals(0, realm.where(Owner.CLASS_NAME).count())
//        try {
//            realm.executeTransaction(object : Transaction() {
//                fun execute(realm: DynamicRealm) {
//                    val owner: DynamicRealmObject = realm.createObject(Owner.CLASS_NAME)
//                    owner.setString("name", "Owner")
//                    thrownException.set(RuntimeException("Boom"))
//                    throw thrownException.get()!!
//                }
//            })
//        } catch (e: RuntimeException) {
//            Assert.assertTrue(e === thrownException.get())
//        }
//        assertEquals(0, realm.where(Owner.CLASS_NAME).count())
//    }
//
//    @Test
//    fun executeTransaction_warningIfManuallyCancelled() {
//        assertEquals(0, realm.where("Owner").count())
//        val testLogger: TestHelper.TestLogger = TestLogger()
//        try {
//            RealmLog.add(testLogger)
//            realm.executeTransaction(object : Transaction() {
//                fun execute(realm: DynamicRealm) {
//                    val owner: DynamicRealmObject = realm.createObject("Owner")
//                    owner.setString("name", "Owner")
//                    realm.cancelTransaction()
//                    throw RuntimeException("Boom")
//                }
//            })
//        } catch (ignored: RuntimeException) {
//            // Ensures that we pass a valuable error message to the logger for developers.
//            assertEquals("Could not cancel transaction, not currently in a transaction.", testLogger.message)
//        } finally {
//            RealmLog.remove(testLogger)
//        }
//        assertEquals(0, realm.where("Owner").count())
//    }
//
//    @Test
//    @UiThreadTest
//    fun executeTransaction_mainThreadWritesAllowed() {
//        val configuration: RealmConfiguration = configFactory.createConfigurationBuilder()
//            .allowWritesOnUiThread(true)
//            .name("ui_realm")
//            .build()
//
//        // Initializes schema. DynamicRealm will not do that, so let a normal Realm create the file first.
//        Realm.getInstance(configuration).close()
//        val uiRealm: DynamicRealm = DynamicRealm.getInstance(configuration)
//        uiRealm.executeTransaction(object : Transaction() {
//            fun execute(realm: DynamicRealm) {
//                val owner: DynamicRealmObject = realm.createObject(Owner.CLASS_NAME)
//                owner.setString("name", "Mortimer Smith")
//            }
//        })
//        val results: RealmResults<DynamicRealmObject> = uiRealm.where(Owner.CLASS_NAME).equalTo("name", "Mortimer Smith").findAll()
//        Assert.assertEquals(1, results.size.toLong())
//        Assert.assertNotNull(results.first())
//        assertEquals("Mortimer Smith", Objects.requireNonNull(results.first()).getString(Dog.FIELD_NAME))
//        uiRealm.close()
//    }
//
//    @Test
//    @UiThreadTest
//    fun executeTransaction_mainThreadWritesNotAllowed() {
//        val configuration: RealmConfiguration = configFactory.createConfigurationBuilder()
//            .allowWritesOnUiThread(false)
//            .name("ui_realm")
//            .build()
//
//        // Initializes schema. DynamicRealm will not do that, so let a normal Realm create the file first.
//        Realm.getInstance(configuration).close()
//
//        // Try-with-resources
//        try {
//            DynamicRealm.getInstance(configuration).use { uiRealm ->
//                uiRealm.executeTransaction(object : Transaction() {
//                    fun execute(realm: DynamicRealm?) {
//                        // no-op
//                    }
//                })
//                Assert.fail("the call to executeTransaction should have failed, this line should not be reached.")
//            }
//        } catch (e: RealmException) {
//            Assert.assertTrue(Objects.requireNonNull(e.getMessage()).contains("allowWritesOnUiThread"))
//        }
//    }
//
//    @Test
//    fun findFirst() {
//        populateTestRealm(realm, 10)
//        val allTypes: DynamicRealmObject = realm.where(AllTypes.CLASS_NAME)
//            .between(AllTypes.FIELD_LONG, 4, 9)
//            .findFirst()
//        assertEquals("test data 4", allTypes.getString(AllTypes.FIELD_STRING))
//    }
//
//    @Test
//    @RunTestInLooperThread
//    fun findFirstAsync() {
//        val dynamicRealm: DynamicRealm = initializeDynamicRealm()
//        val allTypes: DynamicRealmObject = dynamicRealm.where(AllTypes.CLASS_NAME)
//            .between(AllTypes.FIELD_LONG, 4, 9)
//            .findFirstAsync()
//        Assert.assertFalse(allTypes.isLoaded())
//        looperThread.keepStrongReference(allTypes)
//        allTypes.addChangeListener(object : RealmChangeListener<DynamicRealmObject?>() {
//            fun onChange(`object`: DynamicRealmObject?) {
//                assertEquals("test data 4", allTypes.getString(AllTypes.FIELD_STRING))
//                dynamicRealm.close()
//                looperThread.testComplete()
//            }
//        })
//    }
//
//    @Test
//    @RunTestInLooperThread
//    fun findAllAsync() {
//        val dynamicRealm: DynamicRealm = initializeDynamicRealm()
//        val allTypes: RealmResults<DynamicRealmObject> = dynamicRealm.where(AllTypes.CLASS_NAME)
//            .between(AllTypes.FIELD_LONG, 4, 9)
//            .findAllAsync()
//        Assert.assertFalse(allTypes.isLoaded())
//        Assert.assertEquals(0, allTypes.size.toLong())
//        allTypes.addChangeListener(object : RealmChangeListener<RealmResults<DynamicRealmObject?>?>() {
//            fun onChange(`object`: RealmResults<DynamicRealmObject?>?) {
//                Assert.assertEquals(6, allTypes.size.toLong())
//                for (i in allTypes.indices) {
//                    assertEquals("test data " + (4 + i), allTypes[i].getString(AllTypes.FIELD_STRING))
//                }
//                dynamicRealm.close()
//                looperThread.testComplete()
//            }
//        })
//        looperThread.keepStrongReference(allTypes)
//    }
//
//    @Test
//    @RunTestInLooperThread
//    fun sort_async() {
//        val dynamicRealm: DynamicRealm = initializeDynamicRealm()
//        val allTypes: RealmResults<DynamicRealmObject> = dynamicRealm.where(AllTypes.CLASS_NAME)
//            .between(AllTypes.FIELD_LONG, 0, 4)
//            .sort(AllTypes.FIELD_STRING, Sort.DESCENDING)
//            .findAllAsync()
//        Assert.assertFalse(allTypes.isLoaded())
//        Assert.assertEquals(0, allTypes.size.toLong())
//        allTypes.addChangeListener(object : RealmChangeListener<RealmResults<DynamicRealmObject?>?>() {
//            fun onChange(`object`: RealmResults<DynamicRealmObject?>?) {
//                Assert.assertEquals(5, allTypes.size.toLong())
//                for (i in 0..4) {
//                    val iteration = 4 - i
//                    assertEquals("test data $iteration", allTypes[4 - iteration].getString(AllTypes.FIELD_STRING))
//                }
//                dynamicRealm.close()
//                looperThread.testComplete()
//            }
//        })
//        looperThread.keepStrongReference(allTypes)
//    }
//
//    // Initializes a Dynamic Realm used by the *Async tests and keeps it ref in the looperThread.
//    private fun initializeDynamicRealm(): DynamicRealm {
//        val defaultConfig: RealmConfiguration = looperThread.getConfiguration()
//        val dynamicRealm: DynamicRealm = DynamicRealm.getInstance(defaultConfig)
//        populateTestRealm(dynamicRealm, 10)
//        looperThread.keepStrongReference(dynamicRealm)
//        return dynamicRealm
//    }
//
//    @Test
//    @RunTestInLooperThread
//    fun sort_async_usingMultipleFields() {
//        val dynamicRealm: DynamicRealm = initializeDynamicRealm()
//        dynamicRealm.setAutoRefresh(false)
//        dynamicRealm.beginTransaction()
//        dynamicRealm.delete(AllTypes.CLASS_NAME)
//        var i = 0
//        while (i < 5) {
//            var allTypes: DynamicRealmObject = dynamicRealm.createObject(AllTypes.CLASS_NAME)
//            allTypes.set(AllTypes.FIELD_LONG, i)
//            allTypes.set(AllTypes.FIELD_STRING, "data " + i % 3)
//            allTypes = dynamicRealm.createObject(AllTypes.CLASS_NAME)
//            allTypes.set(AllTypes.FIELD_LONG, i)
//            allTypes.set(AllTypes.FIELD_STRING, "data " + ++i % 3)
//        }
//        dynamicRealm.commitTransaction()
//        dynamicRealm.setAutoRefresh(true)
//
//        // Sorts first set by using: String[ASC], Long[DESC].
//        val realmResults1: RealmResults<DynamicRealmObject> = dynamicRealm.where(AllTypes.CLASS_NAME)
//            .sort(arrayOf<String>(AllTypes.FIELD_STRING, AllTypes.FIELD_LONG), arrayOf<Sort>(Sort.ASCENDING, Sort.DESCENDING))
//            .findAllAsync()
//
//        // Sorts second set by using: String[DESC], Long[ASC].
//        val realmResults2: RealmResults<DynamicRealmObject> = dynamicRealm.where(AllTypes.CLASS_NAME)
//            .between(AllTypes.FIELD_LONG, 0, 5)
//            .sort(arrayOf<String>(AllTypes.FIELD_STRING, AllTypes.FIELD_LONG), arrayOf<Sort>(Sort.DESCENDING, Sort.ASCENDING))
//            .findAllAsync()
//        val signalCallbackDone: Runnable = object : Runnable {
//            val callbacksDone = AtomicInteger(2)
//            override fun run() {
//                if (callbacksDone.decrementAndGet() == 0) {
//                    dynamicRealm.close()
//                    looperThread.testComplete()
//                }
//            }
//        }
//        realmResults1.addChangeListener(object : RealmChangeListener<RealmResults<DynamicRealmObject?>?>() {
//            fun onChange(`object`: RealmResults<DynamicRealmObject?>?) {
//                assertEquals("data 0", realmResults1[0].get(AllTypes.FIELD_STRING))
//                assertEquals(3L, realmResults1[0].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                assertEquals("data 0", realmResults1[1].get(AllTypes.FIELD_STRING))
//                assertEquals(2L, realmResults1[1].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                assertEquals("data 0", realmResults1[2].get(AllTypes.FIELD_STRING))
//                assertEquals(0L, realmResults1[2].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                assertEquals("data 1", realmResults1[3].get(AllTypes.FIELD_STRING))
//                assertEquals(4L, realmResults1[3].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                assertEquals("data 1", realmResults1[4].get(AllTypes.FIELD_STRING))
//                assertEquals(3L, realmResults1[4].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                assertEquals("data 1", realmResults1[5].get(AllTypes.FIELD_STRING))
//                assertEquals(1L, realmResults1[5].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                assertEquals("data 1", realmResults1[6].get(AllTypes.FIELD_STRING))
//                assertEquals(0L, realmResults1[6].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                assertEquals("data 2", realmResults1[7].get(AllTypes.FIELD_STRING))
//                assertEquals(4L, realmResults1[7].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                assertEquals("data 2", realmResults1[8].get(AllTypes.FIELD_STRING))
//                assertEquals(2L, realmResults1[8].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                assertEquals("data 2", realmResults1[9].get(AllTypes.FIELD_STRING))
//                assertEquals(1L, realmResults1[9].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                signalCallbackDone.run()
//            }
//        })
//        realmResults2.addChangeListener(object : RealmChangeListener<RealmResults<DynamicRealmObject?>?>() {
//            fun onChange(`object`: RealmResults<DynamicRealmObject?>?) {
//                assertEquals("data 2", realmResults2[0].get(AllTypes.FIELD_STRING))
//                assertEquals(1L, realmResults2[0].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                assertEquals("data 2", realmResults2[1].get(AllTypes.FIELD_STRING))
//                assertEquals(2L, realmResults2[1].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                assertEquals("data 2", realmResults2[2].get(AllTypes.FIELD_STRING))
//                assertEquals(4L, realmResults2[2].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                assertEquals("data 1", realmResults2[3].get(AllTypes.FIELD_STRING))
//                assertEquals(0L, realmResults2[3].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                assertEquals("data 1", realmResults2[4].get(AllTypes.FIELD_STRING))
//                assertEquals(1L, realmResults2[4].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                assertEquals("data 1", realmResults2[5].get(AllTypes.FIELD_STRING))
//                assertEquals(3L, realmResults2[5].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                assertEquals("data 1", realmResults2[6].get(AllTypes.FIELD_STRING))
//                assertEquals(4L, realmResults2[6].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                assertEquals("data 0", realmResults2[7].get(AllTypes.FIELD_STRING))
//                assertEquals(0L, realmResults2[7].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                assertEquals("data 0", realmResults2[8].get(AllTypes.FIELD_STRING))
//                assertEquals(2L, realmResults2[8].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                assertEquals("data 0", realmResults2[9].get(AllTypes.FIELD_STRING))
//                assertEquals(3L, realmResults2[9].< Long > get < kotlin . Long ? > AllTypes.FIELD_LONG.longValue())
//                signalCallbackDone.run()
//            }
//        })
//        looperThread.keepStrongReference(realmResults1)
//        looperThread.keepStrongReference(realmResults2)
//    }
//
//    @Test
//    @RunTestInLooperThread
//    fun accessingDynamicRealmObjectBeforeAsyncQueryCompleted() {
//        val dynamicRealm: DynamicRealm = initializeDynamicRealm()
//        val dynamicRealmObject: DynamicRealmObject = dynamicRealm.where(AllTypes.CLASS_NAME)
//            .between(AllTypes.FIELD_LONG, 4, 9)
//            .findFirstAsync()
//        Assert.assertFalse(dynamicRealmObject.isLoaded())
//        Assert.assertFalse(dynamicRealmObject.isValid())
//        try {
//            dynamicRealmObject.getObject(AllTypes.FIELD_BINARY)
//            Assert.fail("trying to access a DynamicRealmObject property should throw")
//        } catch (ignored: IllegalStateException) {
//        } finally {
//            dynamicRealm.close()
//            looperThread.testComplete()
//        }
//    }
//
//    @Test
//    fun deleteAll() {
//        realm.beginTransaction()
//        realm.createObject(AllTypes.CLASS_NAME)
//        val cat: DynamicRealmObject = realm.createObject(Cat.CLASS_NAME)
//        val owner: DynamicRealmObject = realm.createObject(Owner.CLASS_NAME)
//        owner.setObject("cat", cat)
//        realm.getSchema().create("TestRemoveAll").addField("Field1", String::class.java)
//        realm.createObject("TestRemoveAll")
//        realm.commitTransaction()
//        assertEquals(1, realm.where(AllTypes.CLASS_NAME).count())
//        assertEquals(1, realm.where(Owner.CLASS_NAME).count())
//        assertEquals(1, realm.where(Cat.CLASS_NAME).count())
//        assertEquals(1, realm.where("TestRemoveAll").count())
//        realm.beginTransaction()
//        realm.deleteAll()
//        realm.commitTransaction()
//        assertEquals(0, realm.where(AllTypes.CLASS_NAME).count())
//        assertEquals(0, realm.where(Owner.CLASS_NAME).count())
//        assertEquals(0, realm.where(Cat.CLASS_NAME).count())
//        assertEquals(0, realm.where("TestRemoveAll").count())
//        Assert.assertTrue(realm.isEmpty())
//    }
//
//    @Test
//    fun realmListRemoveAllFromRealm() {
//        populateTestRealm(realm, 1)
//        val list: RealmList<DynamicRealmObject> = realm.where(AllTypes.CLASS_NAME).findFirst().getList(AllTypes.FIELD_REALMLIST)
//        Assert.assertEquals(2, list.size.toLong())
//        realm.beginTransaction()
//        list.deleteAllFromRealm()
//        realm.commitTransaction()
//        Assert.assertEquals(0, list.size.toLong())
//        assertEquals(0, realm.where(Dog.CLASS_NAME).count())
//    }
//
//    @Test
//    @RunTestInLooperThread
//    fun addChangeListener_throwOnAddingNullListenerFromLooperThread() {
//        val dynamicRealm: DynamicRealm = initializeDynamicRealm()
//        try {
//            dynamicRealm.addChangeListener(null)
//            Assert.fail("adding null change listener must throw an exception.")
//        } catch (ignore: IllegalArgumentException) {
//        } finally {
//            dynamicRealm.close()
//            looperThread.testComplete()
//        }
//    }
//
//    @Test
//    @Throws(Throwable::class)
//    fun addChangeListener_throwOnAddingNullListenerFromNonLooperThread() {
//        TestHelper.executeOnNonLooperThread(object : Task() {
//            @Throws(Exception::class)
//            fun run() {
//                val dynamicRealm: DynamicRealm = DynamicRealm.getInstance(defaultConfig)
//                try {
//                    dynamicRealm.addChangeListener(null)
//                    Assert.fail("adding null change listener must throw an exception.")
//                } catch (ignore: IllegalArgumentException) {
//                } finally {
//                    dynamicRealm.close()
//                }
//            }
//        })
//    }
//
//    @Test
//    @RunTestInLooperThread
//    fun removeChangeListener_throwOnRemovingNullListenerFromLooperThread() {
//        val dynamicRealm: DynamicRealm = initializeDynamicRealm()
//        try {
//            dynamicRealm.removeChangeListener(null)
//            Assert.fail("removing null change listener must throw an exception.")
//        } catch (ignore: IllegalArgumentException) {
//        } finally {
//            dynamicRealm.close()
//            looperThread.testComplete()
//        }
//    }
//
//    @Test
//    @Throws(Throwable::class)
//    fun removeChangeListener_throwOnRemovingNullListenerFromNonLooperThread() {
//        TestHelper.executeOnNonLooperThread(object : Task() {
//            @Throws(Exception::class)
//            fun run() {
//                val dynamicRealm: DynamicRealm = DynamicRealm.getInstance(defaultConfig)
//                try {
//                    dynamicRealm.removeChangeListener(null)
//                    Assert.fail("removing null change listener must throw an exception.")
//                } catch (ignore: IllegalArgumentException) {
//                } finally {
//                    dynamicRealm.close()
//                }
//            }
//        })
//    }
//
//    @Test
//    fun equalTo_noFieldObjectShouldThrow() {
//        val className = "NoField"
//        val emptyConfig: RealmConfiguration = configFactory.createConfiguration("empty")
//        val dynamicRealm: DynamicRealm = DynamicRealm.getInstance(emptyConfig)
//        dynamicRealm.beginTransaction()
//        dynamicRealm.getSchema().create(className)
//        dynamicRealm.commitTransaction()
//        thrown.expect(IllegalArgumentException::class.java)
//        thrown.expectMessage("Illegal Argument: 'class_NoField' has no property: 'nonExisting'")
//        dynamicRealm.where(className).equalTo("nonExisting", 1)
//    }
//
//    @Test(expected = IllegalStateException::class)
//    fun getInstanceAsync_nonLooperThreadShouldThrow() {
//        DynamicRealm.getInstanceAsync(defaultConfig, object : Callback() {
//            fun onSuccess(realm: DynamicRealm?) {
//                Assert.fail()
//            }
//        })
//    }
//
//    @Test
//    @RunTestInLooperThread
//    fun getInstanceAsync_nullConfigShouldThrow() {
//        try {
//            DynamicRealm.getInstanceAsync(null, object : Callback() {
//                fun onSuccess(realm: DynamicRealm?) {
//                    Assert.fail()
//                }
//            })
//        } catch (ignored: IllegalArgumentException) {
//        }
//        looperThread.testComplete()
//    }
//
//    @Test
//    @RunTestInLooperThread
//    fun getInstanceAsync_nullCallbackShouldThrow() {
//        try {
//            DynamicRealm.getInstanceAsync(defaultConfig, null)
//        } catch (ignored: IllegalArgumentException) {
//        }
//        looperThread.testComplete()
//    }
}
