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

package io.realm.test.shared.notifications

import co.touchlab.stately.concurrency.AtomicInt
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmResults
import io.realm.entities.Sample
import io.realm.query
import io.realm.query.find
import io.realm.test.NotificationTests
import io.realm.test.platform.PlatformUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class RealmResultsNotificationsTests : NotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .path(path = "$tmpDir/default.realm").build()
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
    override fun initialElement() {
        runBlocking {
            val c = Channel<RealmResults<Sample>>(1)
            val observer = async {
                realm.query<Sample>()
                    .asFlow()
                    .collect {
                        c.trySend(it)
                    }
            }
            val initialElement: RealmResults<Sample> = c.receive()
            assertEquals(0, initialElement.size)
            observer.cancel()
            c.close()
        }
    }

    @Test
    override fun observe() {
        runBlocking {
            val c = Channel<Int>(capacity = 1)
            val observer = async {
                realm.query<Sample>()
                    .asFlow()
                    .filterNot {
                        it.isEmpty()
                    }.collect {
                        c.trySend(it.size)
                    }
            }
            realm.write {
                copyToRealm(Sample().apply { stringField = "Foo" })
            }
            assertEquals(1, c.receive())
            observer.cancel()
            c.close()
        }
    }

    @Test
    override fun cancelObserve() {
        runBlocking {
            val c1 = Channel<RealmResults<Sample>>(1)
            val c2 = Channel<RealmResults<Sample>>(1)
            val observer1 = async {
                realm.query<Sample>()
                    .asFlow()
                    .filterNot {
                        it.isEmpty()
                    }.collect {
                        c1.trySend(it)
                    }
            }
            val observer2 = async {
                realm.query<Sample>()
                    .asFlow()
                    .filterNot {
                        it.isEmpty()
                    }.collect {
                        c2.trySend(it)
                    }
            }
            realm.write {
                copyToRealm(Sample().apply { stringField = "Bar" })
            }
            assertEquals(1, c1.receive().size)
            assertEquals(1, c2.receive().size)
            observer1.cancel()
            realm.write {
                copyToRealm(Sample().apply { stringField = "Baz" })
            }
            assertEquals(2, c2.receive().size)
            assertTrue(c1.isEmpty)
            observer2.cancel()
            c1.close()
            c2.close()
        }
    }

    @Test
    override fun deleteObservable() {
        runBlocking {
            val c = Channel<RealmResults<Sample>>(1)
            realm.write {
                copyToRealm(
                    Sample().apply {
                        stringField = "Foo"
                    }
                )
            }
            val observer = async {
                realm.query<Sample>()
                    .asFlow()
                    .collect {
                        c.trySend(it)
                    }
            }
            assertEquals(1, c.receive().size)
            realm.write {
                query<Sample>()
                    .first()
                    .find { sample ->
                        assertNotNull(sample)
                        delete(sample)
                    }
            }
            assertEquals(0, c.receive().size)
            observer.cancel()
            c.close()
        }
    }

    @Test
    @Ignore // FIXME Not correctly implemented yet
    override fun closeRealmInsideFlowThrows() {
        runBlocking {
            val c = Channel<Int>(capacity = 1)
            val counter = AtomicInt(0)
            val observer1 = async {
                realm.query<Sample>()
                    .asFlow()
                    .collect {
                        when (counter.incrementAndGet()) {
                            1 -> c.trySend(it.size)
                            2 -> {
                                realm.close()
                                c.trySend(-1)
                                println("realm closed")
                            }
                        }
                    }
            }
            val observer2 = async {
                realm.query<Sample>()
                    .asFlow()
                    .collect {
                        println(it.first().stringField)
                        println("$it -> ${realm.isClosed()}")
                    }
            }
            realm.write {
                copyToRealm(Sample().apply { stringField = "Foo" })
            }
            assertEquals(1, c.receive())
            realm.write {
                copyToRealm(Sample().apply { stringField = "Bar" })
            }
            assertEquals(-1, c.receive())
            observer1.cancel()
            observer2.cancel()
            c.close()
        }
    }

    @Test
    override fun closingRealmDoesNotCancelFlows() {
        runBlocking {
            val c = Channel<Int>(capacity = 1)
            val observer = async {
                realm.query<Sample>()
                    .asFlow()
                    .filterNot {
                        it.isEmpty()
                    }.collect {
                        c.send(it.size)
                    }
                fail("Flow should not be canceled.")
            }
            realm.write {
                copyToRealm(Sample().apply { stringField = "Foo" })
            }
            assertEquals(1, c.receive())
            realm.close()
            observer.cancel()
            c.close()
        }
    }
}
