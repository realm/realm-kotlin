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

package io.realm.kotlin.test.common.notifications

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.ext.asFlow
import io.realm.kotlin.notifications.DeletedObject
import io.realm.kotlin.notifications.InitialObject
import io.realm.kotlin.notifications.ObjectChange
import io.realm.kotlin.notifications.UpdatedObject
import io.realm.kotlin.test.common.utils.RealmEntityNotificationTests
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TestChannel
import io.realm.kotlin.test.util.receiveOrFail
import io.realm.kotlin.test.util.update
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

class RealmObjectNotificationsTests : RealmEntityNotificationTests {

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
            val c = TestChannel<ObjectChange<Sample>>()
            val obj = realm.write {
                copyToRealm(
                    Sample().apply { stringField = "Foo" }
                )
            }
            val observer = async {
                obj.asFlow().collect {
                    c.send(it)
                }
            }

            c.receiveOrFail().let { objectChange ->
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
            val c = TestChannel<ObjectChange<Sample>>()
            val obj: Sample = realm.write {
                copyToRealm(Sample().apply { stringField = "Foo" })
            }
            val observer = async {
                obj.asFlow().collect {
                    c.send(it)
                }
            }

            c.receiveOrFail().let { objectChange ->
                assertIs<InitialObject<Sample>>(objectChange)
                assertEquals("Foo", objectChange.obj.stringField)
            }

            obj.update {
                stringField = "Bar"
            }
            c.receiveOrFail().let { objectChange ->
                assertIs<UpdatedObject<Sample>>(objectChange)

                assertEquals(1, objectChange.changedFields.size)
                assertContains(objectChange.changedFields, Sample::stringField.name)

                assertEquals("Bar", objectChange.obj.stringField)
            }

            obj.update {
                stringField = "Baz"
                booleanField = false
            }
            c.receiveOrFail().let { objectChange ->
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
            val c1 = TestChannel<ObjectChange<Sample>>()
            val c2 = TestChannel<ObjectChange<Sample>>()
            val observer1 = async {
                obj.asFlow().collect {
                    c1.send(it)
                }
            }
            val observer2 = async {
                obj.asFlow().collect {
                    c2.send(it)
                }
            }
            // First event should be the initial value
            c1.receiveOrFail().let { objectChange ->
                assertIs<InitialObject<Sample>>(objectChange)
                assertEquals("Foo", objectChange.obj.stringField)
            }
            c2.receiveOrFail().let { objectChange ->
                assertIs<InitialObject<Sample>>(objectChange)
                assertEquals("Foo", objectChange.obj.stringField)
            }
            // Second event should reflect the udpate
            obj.update {
                stringField = "Bar"
            }
            c1.receiveOrFail().let { objectChange ->
                assertIs<UpdatedObject<Sample>>(objectChange)
                assertEquals("Bar", objectChange.obj.stringField)
            }
            c2.receiveOrFail().let { objectChange ->
                assertIs<UpdatedObject<Sample>>(objectChange)
                assertEquals("Bar", objectChange.obj.stringField)
            }

            observer1.cancel()
            obj.update {
                stringField = "Baz"
            }
            c2.receiveOrFail().let { objectChange ->
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
    override fun deleteEntity() {
        runBlocking {
            val c1 = TestChannel<ObjectChange<Sample>>()
            val c2 = TestChannel<Unit>()
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
            c1.receiveOrFail().let { objectChange ->
                assertIs<InitialObject<Sample>>(objectChange)
                assertNotNull(objectChange.obj)
            }
            realm.write {
                delete(findLatest(obj)!!)
            }
            c1.receiveOrFail().let { objectChange ->
                assertIs<DeletedObject<Sample>>(objectChange)
                assertNull(objectChange.obj)
            }
            // Test for sentinel value
            assertEquals(Unit, c2.receiveOrFail())
            observer.cancel()
            c1.close()
            c2.close()
        }
    }

    @Test
    override fun asFlowOnDeleteEntity() {
        runBlocking {
            val sample = realm.write { copyToRealm(Sample()) }
            val mutex = Mutex(true)
            val flow = async {
                sample.asFlow().first {
                    mutex.unlock()
                    it is DeletedObject<*>
                }
            }

            // Await that flow is actually running
            mutex.lock()
            // And delete containing entity
            realm.write { delete(findLatest(sample)!!) }

            // Await that notifier has signalled the deletion so we are certain that the entity
            // has been deleted
            withTimeout(10.seconds) {
                flow.await()
            }

            // Verify that a flow on the deleted entity will signal a deletion and complete gracefully
            withTimeout(10.seconds) {
                // First and only change should be a deletion event
                sample.asFlow().single().also {
                    assertIs<DeletedObject<*>>(it)
                }
            }
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
            val c = TestChannel<ObjectChange<Sample>>()
            val obj: Sample = realm.write {
                copyToRealm(Sample().apply { stringField = "Foo" })
            }
            val observer = async {
                obj.asFlow().collect {
                    c.send(it)
                }
                fail("Flow should not be canceled.")
            }
            c.receiveOrFail().let { objectChange ->
                assertIs<InitialObject<Sample>>(objectChange)
                assertEquals("Foo", objectChange.obj.stringField)
            }
            realm.close()
            observer.cancel()
            c.close()
        }
    }

    @Test
    override fun keyPath_topLevelProperty() = runBlocking<Unit> {
        val c = TestChannel<ObjectChange<Sample>>(1)
        val obj: Sample = realm.write { copyToRealm(Sample()) }
        val observer = async {
            obj.asFlow(listOf("stringField")).collect {
                c.trySend(it)
            }
        }
        assertIs<InitialObject<Sample>>(c.receiveOrFail())
        realm.write {
            // Update field that should not trigger a notification
            findLatest(obj)!!.intField = 42
        }
        realm.write {
            // Update field that should trigger a notification
            findLatest(obj)!!.stringField = "Bar"
        }
        c.receiveOrFail().let { objectChange ->
            assertIs<UpdatedObject<Sample>>(objectChange)
            when (objectChange) {
                is UpdatedObject -> {
                    assertEquals(1, objectChange.changedFields.size)
                    assertEquals("stringField", objectChange.changedFields.first())
                    assertEquals("Bar", objectChange.obj.stringField)
                }
                else -> fail("Unexpected change: $objectChange")
            }
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_nestedProperty() = runBlocking<Unit> {
        val c = Channel<ObjectChange<Sample>>(1)
        val obj: Sample = realm.write {
            copyToRealm(
                Sample().apply {
                    this.stringField = "parent"
                    this.nullableObject = Sample().apply {
                        this.stringField = "child"
                    }
                }
            )
        }
        val observer = async {
            obj.asFlow(listOf("nullableObject.stringField")).collect {
                c.trySend(it)
            }
        }
        assertIs<InitialObject<Sample>>(c.receiveOrFail())
        realm.write {
            // Update field that should not trigger a notification
            findLatest(obj)!!.stringField = "Parent change"
        }
        realm.write {
            // Update field that should trigger a notification
            findLatest(obj)!!.nullableObject!!.stringField = "Bar"
        }
        c.receiveOrFail().let { objectChange ->
            assertIs<UpdatedObject<Sample>>(objectChange)
            when (objectChange) {
                is UpdatedObject -> {
                    // Core will only report something changed to the top-level property.
                    assertEquals(1, objectChange.changedFields.size)
                    assertEquals("nullableObject", objectChange.changedFields.first())
                    assertEquals("Bar", objectChange.obj.nullableObject!!.stringField)
                }
                else -> fail("Unexpected change: $objectChange")
            }
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_defaultDepth() = runBlocking<Unit> {
        val c = Channel<ObjectChange<Sample>>(1)
        val obj: Sample = realm.write {
            copyToRealm(
                Sample().apply {
                    this.stringField = "parent"
                    this.nullableObject = Sample().apply {
                        this.stringField = "child"
                        this.nullableObject = Sample().apply {
                            this.stringField = "child-child"
                            this.nullableObject = Sample().apply {
                                this.stringField = "child-child-child"
                                this.nullableObject = Sample().apply {
                                    this.stringField = "child-child-child-child"
                                    this.nullableObject = Sample().apply {
                                        this.stringField = "BottomChild"
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
        val observer = async {
            // Default keypath
            obj.asFlow().collect {
                c.trySend(it)
            }
        }
        assertIs<InitialObject<Sample>>(c.receiveOrFail())
        realm.write {
            // Update field that should not trigger a notification
            findLatest(obj)!!.nullableObject!!.nullableObject!!.nullableObject!!.nullableObject!!.nullableObject!!.intField = 1
        }
        realm.write {
            // Update field that should trigger a notification
            findLatest(obj)!!.stringField = "Parent change"
        }
        c.receiveOrFail().let { objectChange ->
            assertIs<UpdatedObject<Sample>>(objectChange)
            when (objectChange) {
                is UpdatedObject -> {
                    // Default value is Realm, so if this event is triggered by the first write
                    // this assert will fail
                    assertEquals("Parent change", objectChange.obj.stringField)
                }
                else -> fail("Unexpected change: $objectChange")
            }
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_propertyBelowDefaultLimit() = runBlocking<Unit> {
        val c = Channel<ObjectChange<Sample>>(1)
        val obj: Sample = realm.write {
            copyToRealm(
                Sample().apply {
                    this.stringField = "parent"
                    this.nullableObject = Sample().apply {
                        this.stringField = "child"
                        this.nullableObject = Sample().apply {
                            this.stringField = "child-child"
                            this.nullableObject = Sample().apply {
                                this.stringField = "child-child-child"
                                this.nullableObject = Sample().apply {
                                    this.stringField = "child-child-child-child"
                                    this.nullableObject = Sample().apply {
                                        this.stringField = "BottomChild"
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
        val observer = async {
            obj.asFlow(listOf("nullableObject.nullableObject.nullableObject.nullableObject.nullableObject.stringField")).collect {
                c.trySend(it)
            }
        }
        assertIs<InitialObject<Sample>>(c.receiveOrFail())
        realm.write {
            // Update field that should not trigger a notification
            findLatest(obj)!!.stringField = "Parent change"
        }
        realm.write {
            // Update field that should trigger a notification
            findLatest(obj)!!.nullableObject!!.nullableObject!!.nullableObject!!.nullableObject!!.nullableObject!!.stringField = "Bar"
        }
        c.receiveOrFail().let { objectChange ->
            assertIs<UpdatedObject<Sample>>(objectChange)
            when (objectChange) {
                is UpdatedObject -> {
                    // Core will only report something changed to the top-level property.
                    assertEquals(1, objectChange.changedFields.size)
                    assertEquals("nullableObject", objectChange.changedFields.first())
                    assertEquals("Bar", objectChange.obj.nullableObject!!.nullableObject!!.nullableObject!!.nullableObject!!.nullableObject!!.stringField)
                }
                else -> fail("Unexpected change: $objectChange")
            }
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_unknownTopLevelProperty() = runBlocking<Unit> {
        val obj: Sample = realm.write { copyToRealm(Sample()) }
        assertFailsWith<IllegalArgumentException>() {
            obj.asFlow(listOf("foo"))
        }
    }

    @Test
    override fun keyPath_unknownNestedProperty() = runBlocking<Unit> {
        val obj: Sample = realm.write { copyToRealm(Sample()) }
        assertFailsWith<IllegalArgumentException>() {
            obj.asFlow(listOf("nullableObject.foo"))
        }
    }

    @Test
    override fun keyPath_invalidNestedProperty() = runBlocking<Unit> {
        val obj: Sample = realm.write { copyToRealm(Sample()) }
        assertFailsWith<IllegalArgumentException> {
            obj.asFlow(listOf("nullableObject.intField.foo"))
        }
        assertFailsWith<IllegalArgumentException> {
            obj.asFlow(listOf("nullableObject.intListField.foo"))
        }
    }
}
