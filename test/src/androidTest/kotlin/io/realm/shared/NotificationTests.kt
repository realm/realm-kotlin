@file:Suppress("invisible_reference", "invisible_member")
/*
 * Copyright 2020 Realm Inc.
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
package io.realm.shared

import co.touchlab.stately.concurrency.AtomicReference
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.internal.singleThreadDispatcher
import io.realm.util.PlatformUtils
import io.realm.util.Utils.printlntid
import io.realm.util.awaitTestComplete
import io.realm.util.completeTest
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import test.Sample
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationTests {

    enum class ClassType {
        REALM,
        REALM_RESULTS,
        REALM_LIST,
        REALM_OBJECT
    }

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration(path = "$tmpDir/default.realm", schema = setOf(Sample::class))
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        realm.close()
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun observe() {
        ClassType.values().forEach {
            when (it) {
                ClassType.REALM -> observeRealm()
                ClassType.REALM_RESULTS -> observeRealmResults()
                ClassType.REALM_LIST -> observeRealmList()
                ClassType.REALM_OBJECT -> observeRealmObject()
                else -> throw NotImplementedError(it.toString())
            }
        }
    }

    @Test
    fun cancelObserve() {
        // FIXME
    }

    @Test
    fun closingRealmCancelObservers() {
        // FIXME
    }

    @Test
    fun addChangeListener() {
        // FIXME
    }

    @Test
    fun addChangeListener_emitOnProvidedDispatcher() {
        // FIXME
    }

    private fun observeRealmObject() {
        // FIXME
    }

    private fun observeRealmList() {
        // FIXME
    }

    private fun observeRealmResults() {

        @Suppress("JoinDeclarationAndAssignment")
        val listenerJob: AtomicReference<Deferred<Any>?> = AtomicReference(null)
        runBlocking {
            listenerJob.set(
                async {
                    realm.objects(Sample::class).observe().collect {
                        assertEquals(1, it.size)
                        listenerJob.get()!!.completeTest()
                    }
                }
            )

            realm.write {
                copyToRealm(Sample().apply { stringField = "Foo" })
            }
            listenerJob.get()!!.awaitTestComplete()
        }
    }

    private fun observeRealm() {
        // FIXME
    }

//    fun addChangeListenerResults() = RunLoopThread().run {
//        realm.objects(Sample::class).addChangeListener {
//            assertEquals(1, it.size)
//            terminate()
//        }
//
//        realm.writeBlocking {
//            copyToRealm(Sample())
//        }
//    }

    @Test
    fun openSameRealmFileWithDifferentDispatchers() {
        // FIXME This seems to not work
    }

    // Verify that the Main dispatcher can be used for both writes and notifications
    // It should be considered an anti-pattern in production, but is plausible in tests.
    @Test
    fun useMainDispatchers() {
        // FIXME
    }

    // Verify that users can use the Main dispatcher for notifications and a background
    // dispatcher for writes. This is the closest match to how this currently works
    // in Realm Java.
    @Test
    fun useMainNotifierDispatcherAndBackgroundWriterDispatcher() {
    }

    // Verify that the special test dispatchers provided by Google also when using Realm.
    @Test
    fun useTestDispatchers() {
        // FIXME
    }

    // Sanity check to ensure that this doesn't cause crashes
    @Test
    @Ignore
    // I think there is some kind of resource issue when combining too many realms/schedulers. If
    // this test is enabled execution of all test sometimes fails. Something similarly happens if
    // the public realm_open in Realm.open is extended to take a dispatcher to setup notifications.
    fun multipleSchedulersOnSameThread() {
        printlntid("main")
        val baseRealm = Realm.open(configuration)
        val dispatcher = singleThreadDispatcher("background")
        val writer1 = io.realm.internal.SuspendableWriter(baseRealm, dispatcher)
        val writer2 = io.realm.internal.SuspendableWriter(baseRealm, dispatcher)
        runBlocking {
            baseRealm.write { copyToRealm(Sample()) }
            writer1.write { copyToRealm(Sample()) }
            writer2.write { copyToRealm(Sample()) }
            baseRealm.write { copyToRealm(Sample()) }
            writer1.write { copyToRealm(Sample()) }
            writer2.write { copyToRealm(Sample()) }
        }
    }
}
