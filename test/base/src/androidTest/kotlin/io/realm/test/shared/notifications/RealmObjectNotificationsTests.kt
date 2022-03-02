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
import io.realm.asFlow
import io.realm.entities.Sample
import io.realm.notifications.DeletedObject
import io.realm.notifications.InitialObject
import io.realm.notifications.ObjectChange
import io.realm.notifications.UpdatedObject
import io.realm.test.NotificationTests
import io.realm.test.platform.PlatformUtils
import io.realm.test.util.update
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
            .directory(tmpDir)
            .build()
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
            val c = Channel<ObjectChange<Sample>>(1)
            val obj = realm.write {
                copyToRealm(
                    Sample().apply { stringField = "Foo" }
                )
            }
            val observer = async {
                obj.asFlow().collect {
                    c.trySend(it)
                }
            }

            c.receive().let { objectChange ->
                assertIs<InitialObject<Sample>>(objectChange)
                assertEquals("Foo", objectChange.obj.stringField)
            }

            observer.cancel()
            c.close()
        }
    }

    @Test
    override fun asFlow() {
        runBlocking {
            val c = Channel<ObjectChange<Sample>>(1)
            val obj: Sample = realm.write {
                copyToRealm(Sample().apply { stringField = "Foo" })
            }
            val observer = async {
                obj.asFlow().collect {
                    c.trySend(it)
                }
            }

            c.receive().let { objectChange ->
                assertIs<InitialObject<Sample>>(objectChange)
                assertEquals("Foo", objectChange.obj.stringField)
            }

            obj.update {
                stringField = "Bar"
            }
            c.receive().let { objectChange ->
                assertIs<UpdatedObject<Sample>>(objectChange)

                assertEquals(1, objectChange.changedFields.size)
                assertContains(objectChange.changedFields, Sample::stringField.name)

                assertEquals("Bar", objectChange.obj.stringField)
            }

            obj.update {
                stringField = "Baz"
                booleanField = false
            }
            c.receive().let { objectChange ->
                assertIs<UpdatedObject<Sample>>(objectChange)

                assertEquals(2, objectChange.changedFields.size)

                assertContains(objectChange.changedFields, Sample::stringField.name)
                assertContains(objectChange.changedFields, Sample::booleanField.name)

                assertEquals("Baz", objectChange.obj.stringField)
                assertEquals(false, objectChange.obj.booleanField)
            }

            observer.cancel()
            c.close()
        }
    }

    @Test
    override fun cancelAsFlow() {
        runBlocking {
            val obj: Sample = realm.write {
                copyToRealm(Sample().apply { stringField = "Foo" })
            }
            val c1 = Channel<ObjectChange<Sample>>(1)
            val c2 = Channel<ObjectChange<Sample>>(1)
            val observer1 = async {
                obj.asFlow().collect {
                    c1.trySend(it)
                }
            }
            val observer2 = async {
                obj.asFlow().collect {
                    c2.trySend(it)
                }
            }
            // First event should be the initial value
            c1.receive().let { objectChange ->
                assertIs<InitialObject<Sample>>(objectChange)
                assertEquals("Foo", objectChange.obj.stringField)
            }
            c2.receive().let { objectChange ->
                assertIs<InitialObject<Sample>>(objectChange)
                assertEquals("Foo", objectChange.obj.stringField)
            }
            // Second event should reflect the udpate
            obj.update {
                stringField = "Bar"
            }
            c1.receive().let { objectChange ->
                assertIs<UpdatedObject<Sample>>(objectChange)
                assertEquals("Bar", objectChange.obj.stringField)
            }
            c2.receive().let { objectChange ->
                assertIs<UpdatedObject<Sample>>(objectChange)
                assertEquals("Bar", objectChange.obj.stringField)
            }

            observer1.cancel()
            obj.update {
                stringField = "Baz"
            }
            c2.receive().let { objectChange ->
                assertIs<UpdatedObject<Sample>>(objectChange)
                assertEquals("Baz", objectChange.obj.stringField)
            }
            assertTrue(c1.isEmpty)
            observer2.cancel()
            c1.close()
            c2.close()
        }
    }

    @Test
    override fun deleteObservable() {
        runBlocking {
            val c1 = Channel<ObjectChange<Sample>>(1)
            val c2 = Channel<Unit>(1)
            val obj: Sample = realm.write {
                copyToRealm(Sample().apply { stringField = "Foo" })
            }
            val observer = async {
                obj.asFlow()
                    .onCompletion {
                        // Emit sentinel value to signal that flow completed
                        c2.send(Unit)
                    }
                    .collect {
                        c1.send(it)
                    }
            }
            c1.receive().let { objectChange ->
                assertIs<InitialObject<Sample>>(objectChange)
                assertNotNull(objectChange.obj)
            }
            realm.write {
                delete(findLatest(obj)!!)
            }
            c1.receive().let { objectChange ->
                assertIs<DeletedObject<Sample>>(objectChange)
                assertNull(objectChange.obj)
            }
            // Test for sentinel value
            assertEquals(Unit, c2.receive())
            observer.cancel()
            c1.close()
            c2.close()
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
            val c = Channel<ObjectChange<Sample>>(1)
            val obj: Sample = realm.write {
                copyToRealm(Sample().apply { stringField = "Foo" })
            }
            val observer = async {
                obj.asFlow().collect {
                    c.trySend(it)
                }
                fail("Flow should not be canceled.")
            }
            c.receive().let { objectChange ->
                assertIs<InitialObject<Sample>>(objectChange)
                assertEquals("Foo", objectChange.obj.stringField)
            }
            realm.close()
            observer.cancel()
            c.close()
        }
    }
}
