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

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmResults
import io.realm.VersionId
import io.realm.internal.singleThreadDispatcher
import io.realm.interop.NativePointer
import io.realm.util.PlatformUtils
import io.realm.util.RunLoopThread
import io.realm.util.Utils.printlntid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import test.Sample
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

private const val INITIAL = "Hello, World!"
private const val FIRST = "FIRST"
private const val SECOND = "SECOND"

// FIXME This file is sym-linked into the `nativeTest`-equivalent. Ideally it would just all be in
//  `commonTest` but as it is currently not possible to trigger common tests on Android it is
//  replicated in the various source sets. Sym-linking to `commonTest` would create overlapping
//  definitions.
//  https://youtrack.jetbrains.com/issue/KT-34535
@OptIn(ExperimentalTime::class)
class NotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration =
            RealmConfiguration(path = "$tmpDir/default.realm", schema = setOf(Sample::class))
    }

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    @Ignore // Notifications won't trigger as they are registered on a frozen realm. Fixed by https://github.com/realm/realm-kotlin/pull/296
    fun notificationsOnMain() = RunLoopThread().run {
        val c = Channel<List<Sample>>(1)

        val realm = Realm.open(configuration)

        val results = realm.objects(Sample::class)

        assertEquals(0, results.size)

        val token = results.observe {
            assertTrue(it is RealmResults<Sample>)
            assertEquals(results, it)
            val updatedResults = results.toList()
            this@run.launch {
                c.send(updatedResults)
            }
        }

        launch {
            val size = c.receive().size
            val sample = realm.writeBlocking {
                assertEquals(0, size)
                create(Sample::class).apply { stringField = INITIAL }
            }

            val result = c.receive()
            assertEquals(1, result.size)
            assertEquals(sample.stringField, result[0].stringField)

            token.cancel()

            realm.writeBlocking {
                create(Sample::class).apply { stringField = INITIAL }
            }

            delay(1.seconds)
            assertTrue(c.isEmpty)

            realm.close()
            terminate()
        }
    }

    @Test
    @Ignore // Notifications won't trigger as they are registered on a frozen realm. Fixed by https://github.com/realm/realm-kotlin/pull/296
    fun notificationOnMainFromBackgroundDispatcherUpdates() = RunLoopThread().run {
        val dispatcher = singleThreadDispatcher("notifier")

        val realm = Realm.open(configuration)
        realm.objects<Sample>().observe {
            if (it.size == 1) terminate()
        }

        async(dispatcher) {
            val realm = Realm.open(configuration)
            realm.write {
                copyToRealm(Sample())
            }
        }
    }

    @Test
    @Suppress("invisible_reference", "invisible_member")
    fun notificationOnBackgroundDispatcherFromSuspendableWriterUpdates() {
        printlntid("main")
        val exit = Mutex(true)
        val dispatcher1 = singleThreadDispatcher("notifier")
        val dispatcher2 = singleThreadDispatcher("writer")

        val baseRealm = Realm.open(configuration)

        val notifiers = io.realm.internal.Notifier(configuration, dispatcher1)
        runBlocking {
            val async = CoroutineScope(dispatcher1).async {
                val obs: Flow<RealmResults<Sample>> = notifiers.observe<Sample>()
                obs.collect {
                    if (it.size == 1) exit.unlock()
                }
            }
            delay(1000)
            val writer = io.realm.internal.SuspendableWriter(baseRealm, dispatcher2)
            val write: Triple<NativePointer, VersionId, Sample> = writer.write {
                copyToRealm(Sample())
            }

            exit.lock()
            async.cancel()
        }
    }

    @Test
    @Suppress("invisible_reference", "invisible_member")
    fun notificationOnBackgroundDispatcherFromMainRealmUpdates() {
        printlntid("main")
        val exit = Mutex(true)
        val dispatcher1 = singleThreadDispatcher("notifier")

        val notifiers = io.realm.internal.Notifier(configuration, dispatcher1)
        runBlocking {
            val async = CoroutineScope(dispatcher1).async {
                val obs: Flow<RealmResults<Sample>> = notifiers.observe<Sample>()
                obs.collect {
                    if (it.size == 1) exit.unlock()
                }
            }
            delay(1000)
            Realm.open(configuration).write {
                copyToRealm(Sample())
            }

            exit.lock()
            async.cancel()
        }
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
