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

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.entities.Sample
import io.realm.observe
import io.realm.test.NotificationTests
import io.realm.test.platform.PlatformUtils
import io.realm.test.util.update
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class RealmObjectNotificationsTests : NotificationTests {

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
        if (!realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    override fun initialElement() {
        runBlocking {
            val c = Channel<Sample?>(1)
            val obj = realm.write {
                copyToRealm(
                    Sample().apply { stringField = "Foo" }
                )
            }
            val observer = async {
                obj.observe().collect {
                    c.trySend(it)
                }
            }
            assertEquals("Foo", c.receive()!!.stringField)
            observer.cancel()
            c.close()
        }
    }

    @Test
    override fun observe() {
        runBlocking {
            val c = Channel<Sample?>(1)
            val obj: Sample = realm.write {
                copyToRealm(Sample().apply { stringField = "Foo" })
            }
            val observer = async {
                obj.observe().collect {
                    c.trySend(it)
                }
            }
            assertEquals("Foo", c.receive()!!.stringField)
            obj.update {
                stringField = "Bar"
            }
            assertEquals("Bar", c.receive()!!.stringField)
            obj.update {
                stringField = "Baz"
            }
            assertEquals("Baz", c.receive()!!.stringField)
            observer.cancel()
            c.close()
        }
    }

    @Test
    override fun cancelObserve() {
        runBlocking {
            val obj: Sample = realm.write {
                copyToRealm(Sample().apply { stringField = "Foo" })
            }
            val c1 = Channel<Sample?>(1)
            val c2 = Channel<Sample?>(1)
            val observer1 = async {
                obj.observe().collect {
                    c1.trySend(it)
                }
            }
            val observer2 = async {
                obj.observe().collect {
                    c2.trySend(it)
                }
            }
            // First event should be the initial value
            assertEquals("Foo", c1.receive()!!.stringField)
            assertEquals("Foo", c2.receive()!!.stringField)
            // Second event should reflect the udpate
            obj.update {
                stringField = "Bar"
            }
            assertEquals("Bar", c1.receive()!!.stringField)
            assertEquals("Bar", c2.receive()!!.stringField)

            observer1.cancel()
            obj.update {
                stringField = "Baz"
            }
            assertEquals("Baz", c2.receive()!!.stringField)
            assertTrue(c1.isEmpty)
            observer2.cancel()
            c1.close()
            c2.close()
        }
    }

    @Test
    override fun deleteObservable() {
        runBlocking {
            val c = Channel<Sample?>(1)
            val obj: Sample = realm.write {
                copyToRealm(Sample().apply { stringField = "Foo" })
            }
            val observer = async {
                obj.observe()
                    .onCompletion {
                        // Emit sentinel value to signal that flow completed
                        c.send(Sample())
                    }
                    .collect {
                        c.send(it)
                    }
            }
            assertNotNull(c.receive())
            realm.write {
                delete(findLatest(obj)!!)
            }
            // Test for sentinel value
            assertEquals(Sample().stringField, c.receive()!!.stringField)
            observer.cancel()
            c.close()
        }
    }

    @Test
    @Ignore
    override fun closeRealmInsideFlowThrows() {
        TODO("Not yet implemented")
    }

    @Test
    override fun closingRealmDoesNotCancelFlows() {
        runBlocking {
            val c = Channel<Sample?>(1)
            val obj: Sample = realm.write {
                copyToRealm(Sample().apply { stringField = "Foo" })
            }
            val observer = async {
                obj.observe().collect {
                    c.trySend(it)
                }
                fail("Flow should not be canceled.")
            }
            assertEquals("Foo", c.receive()!!.stringField)
            realm.close()
            observer.cancel()
            c.close()
        }
    }
}
