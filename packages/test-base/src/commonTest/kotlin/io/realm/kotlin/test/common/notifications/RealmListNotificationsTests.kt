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
import io.realm.kotlin.entities.list.RealmListContainer
import io.realm.kotlin.entities.list.listTestSchema
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.notifications.DeletedList
import io.realm.kotlin.notifications.InitialList
import io.realm.kotlin.notifications.ListChange
import io.realm.kotlin.notifications.ListChangeSet.Range
import io.realm.kotlin.notifications.UpdatedList
import io.realm.kotlin.test.common.OBJECT_VALUES
import io.realm.kotlin.test.common.OBJECT_VALUES2
import io.realm.kotlin.test.common.OBJECT_VALUES3
import io.realm.kotlin.test.common.utils.RealmEntityNotificationTests
import io.realm.kotlin.test.common.utils.assertIsChangeSet
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TestChannel
import io.realm.kotlin.test.util.receiveOrFail
import io.realm.kotlin.test.util.trySendOrFail
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

class RealmListNotificationsTests : RealmEntityNotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(schema = listTestSchema)
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
        val dataSet = OBJECT_VALUES

        val container = realm.writeBlocking {
            copyToRealm(RealmListContainer()).also {
                it.objectListField.addAll(dataSet)
            }
        }

        runBlocking {
            val channel = Channel<ListChange<*>>(capacity = 1)
            val observer = async {
                container.objectListField
                    .asFlow()
                    .collect { flowList ->
                        channel.send(flowList)
                    }
            }

            // Assertion after empty list is emitted
            channel.receiveOrFail().let { listChange ->
                assertIs<InitialList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataSet.size, listChange.list.size)
            }

            observer.cancel()
            channel.close()
        }
    }

    @Test
    @Suppress("LongMethod")
    override fun asFlow() {
        val dataset = OBJECT_VALUES
        val dataset2 = OBJECT_VALUES2
        val dataset3 = OBJECT_VALUES3

        val container = realm.writeBlocking {
            // Create an empty container with empty lists
            copyToRealm(RealmListContainer())
        }

        runBlocking {
            val channel = Channel<ListChange<*>>(capacity = 1)
            val observer = async {
                container.objectListField
                    .asFlow()
                    .collect { flowList ->
                        channel.send(flowList)
                    }
            }
            channel.receive().let {
                assertIs<InitialList<*>>(it)
            }

            // Assert a single range is reported
            //
            // objectListField = [<A, B>]
            realm.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedList = queriedContainer!!.objectListField
                queriedList.addAll(dataset)
            }

            channel.receiveOrFail()
                .let { listChange ->
                    assertIs<UpdatedList<*>>(listChange)

                    assertNotNull(listChange.list)
                    assertEquals(dataset.size, listChange.list.size)

                    assertIsChangeSet(
                        (listChange as UpdatedList<*>),
                        insertRanges = arrayOf(
                            Range(0, 2)
                        )
                    )
                }

            // Assert multiple ranges are reported
            //
            // objectListField = [<C, D, E, F>, A, B, <G, H>]
            realm.writeBlocking {
                val queriedContainer = findLatest(container)!!
                val queriedList = queriedContainer.objectListField
                queriedList.addAll(0, dataset2)
                queriedList.addAll(dataset3)
            }

            channel.receiveOrFail().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset.size + dataset2.size + dataset3.size, listChange.list.size)

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    insertRanges = arrayOf(
                        Range(0, 4),
                        Range(6, 2)
                    )
                )
            }

            // Assert multiple ranges are deleted
            //
            // objectListField = [<C, D, E, F>, A, B, <G, H>]
            realm.writeBlocking {
                val queriedContainer = findLatest(container)!!
                val queriedList = queriedContainer.objectListField

                queriedList.removeRange(6..7)
                queriedList.removeRange(0..3)
            }

            channel.receiveOrFail().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset.size, listChange.list.size)

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    deletionRanges = arrayOf(
                        Range(0, 4),
                        Range(6, 2)
                    )
                )
            }

            // Assert a single range is deleted
            //
            // objectListField = [<A, B>]
            realm.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedList = queriedContainer!!.objectListField
                queriedList.removeRange(0..1)
            }

            channel.receiveOrFail().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertTrue(listChange.list.isEmpty())

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    deletionRanges = arrayOf(
                        Range(0, 2)
                    )
                )
            }

            // Add some values to change
            //
            // objectListField = [<C, D, E, F>]
            realm.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedList = queriedContainer!!.objectListField
                queriedList.addAll(dataset2)
            }
            channel.receiveOrFail().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset2.size, listChange.list.size)
            }

            // Change contents of two ranges of values
            //
            // objectListField = [<A>, <B>, E, <D>]
            realm.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedList = queriedContainer!!.objectListField
                queriedList[0].stringField = "A"
                queriedList[1].stringField = "B"
                queriedList[3].stringField = "D"
            }

            channel.receiveOrFail().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset2.size, listChange.list.size)

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    changesRanges = arrayOf(
                        Range(0, 2),
                        Range(3, 1),
                    )
                )
            }

            // Reverse a list
            //
            // objectListField = [<D>, <E>, <B>, <A>]
            realm.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedList = queriedContainer!!.objectListField
                queriedList.reverse()
            }

            channel.receiveOrFail().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset2.size, listChange.list.size)

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    changesRanges = arrayOf(
                        Range(0, 4)
                    )
                )
            }

            observer.cancel()
            channel.close()
        }
    }

    @Test
    override fun cancelAsFlow() {
        runBlocking {
            val values = OBJECT_VALUES
            val container = realm.write {
                copyToRealm(RealmListContainer())
            }
            val channel1 = TestChannel<ListChange<*>>()
            val channel2 = TestChannel<ListChange<*>>()
            val observer1 = async {
                container.objectListField
                    .asFlow()
                    .collect { flowList ->
                        channel1.send(flowList)
                    }
            }
            val observer2 = async {
                container.objectListField
                    .asFlow()
                    .collect { flowList ->
                        channel2.send(flowList)
                    }
            }

            // Ignore first emission with empty lists
            channel1.receiveOrFail()
            channel2.receiveOrFail()

            // Trigger an update
            realm.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.objectListField.addAll(OBJECT_VALUES)
            }
            assertEquals(OBJECT_VALUES.size, channel1.receiveOrFail().list.size)
            assertEquals(OBJECT_VALUES.size, channel2.receiveOrFail().list.size)

            // Cancel observer 1
            observer1.cancel()

            // Trigger another update
            realm.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.objectListField
                    .add(copyToRealm(RealmListContainer().apply { stringField = "C" }))
            }

            // Check channel 1 didn't receive the update
            assertEquals(OBJECT_VALUES.size + 1, channel2.receiveOrFail().list.size)
            assertTrue(channel1.isEmpty)

            observer2.cancel()
            channel1.close()
            channel2.close()
        }
    }

    @Test
    override fun deleteEntity() {
        runBlocking {
            // Freeze values since native complains if we reference a package-level defined variable
            // inside a write block
            val values = OBJECT_VALUES
            val channel1 = Channel<ListChange<*>>(capacity = 1)
            val channel2 = Channel<Boolean>(capacity = 1)
            val container = realm.write {
                RealmListContainer()
                    .apply {
                        objectListField.addAll(values)
                    }.let { container ->
                        copyToRealm(container)
                    }
            }
            val observer = async {
                container.objectListField
                    .asFlow()
                    .onCompletion {
                        // Signal completion
                        channel2.send(true)
                    }.collect { flowList ->
                        channel1.send(flowList)
                    }
            }

            // Assert container got populated correctly
            channel1.receiveOrFail().let { listChange ->
                assertIs<InitialList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(OBJECT_VALUES.size, listChange.list.size)
            }

            // Now delete owner
            realm.write {
                delete(findLatest(container)!!)
            }

            channel1.receiveOrFail().let { listChange ->
                assertIs<DeletedList<*>>(listChange)
                assertTrue(listChange.list.isEmpty())
            }
            // Wait for flow completion
            assertTrue(channel2.receiveOrFail())

            observer.cancel()
            channel1.close()
        }
    }

    @Test
    override fun asFlowOnDeletedEntity() {
        runBlocking {
            val container = realm.write { copyToRealm(RealmListContainer()) }
            val mutex = Mutex(true)
            val flow = async {
                container.stringListField.asFlow().first {
                    mutex.unlock()
                    it is DeletedList<*>
                }
            }

            // Await that flow is actually running
            mutex.lock()
            // And delete containing entity
            realm.write { delete(findLatest(container)!!) }

            // Await that notifier has signalled the deletion so we are certain that the entity
            // has been deleted
            withTimeout(10.seconds) {
                flow.await()
            }

            // Verify that a flow on the deleted entity will signal a deletion and complete gracefully
            withTimeout(10.seconds) {
                container.stringListField.asFlow().collect {
                    assertIs<DeletedList<*>>(it)
                }
            }
        }
    }

    @Test
    @Ignore // FIXME Wait for https://github.com/realm/realm-kotlin/pull/300 to be merged before fleshing this out
    override fun closeRealmInsideFlowThrows() {
        TODO("Waiting for RealmList support")
    }

    @Test
    override fun closingRealmDoesNotCancelFlows() {
        runBlocking {
            val channel = Channel<ListChange<*>>(capacity = 1)
            val container = realm.write {
                copyToRealm(RealmListContainer())
            }
            val observer = async {
                container.objectListField
                    .asFlow()
                    .collect { flowList ->
                        channel.send(flowList)
                    }
                fail("Flow should not be canceled.")
            }

            assertTrue(channel.receiveOrFail().list.isEmpty())

            realm.close()
            observer.cancel()
            channel.close()
        }
    }

    @Test
    override fun keyPath_topLevelProperty() = runBlocking<Unit> {
        val c = Channel<ListChange<RealmListContainer>>(1)
        val obj = realm.write {
            copyToRealm(
                RealmListContainer().apply {
                    this.objectListField = realmListOf(
                        RealmListContainer().apply { this.stringField = "list-item-1" },
                        RealmListContainer().apply { this.stringField = "list-item-2" }
                    )
                }
            )
        }
        val list: RealmList<RealmListContainer> = obj.objectListField
        val observer = async {
            list.asFlow(listOf("stringField")).collect {
                c.trySend(it)
            }
        }
        assertIs<InitialList<RealmListContainer>>(c.receiveOrFail())
        realm.write {
            // Update field that should not trigger a notification
            findLatest(list.first())!!.id = 42
        }
        realm.write {
            // Update field that should trigger a notification
            findLatest(list.first())!!.stringField = "Foo"
        }
        c.receiveOrFail().let { listChange ->
            assertIs<UpdatedList<RealmListContainer>>(listChange)
            when (listChange) {
                is UpdatedList -> {
                    assertEquals(1, listChange.changes.size)
                    // This starts as Realm, so if the first write triggers a change event, it will
                    // catch it here.
                    assertEquals("Foo", listChange.list.first().stringField)
                }
                else -> fail("Unexpected change: $listChange")
            }
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_nestedProperty() = runBlocking<Unit> {
        val c = Channel<ListChange<RealmListContainer>>(1)
        val list = realm.write {
            copyToRealm(
                RealmListContainer().apply {
                    this.stringField = "parent"
                    this.objectListField = realmListOf(
                        RealmListContainer().apply {
                            this.stringField = "child"
                            this.objectListField = realmListOf(
                                RealmListContainer().apply { this.stringField = "list-item-1" }
                            )
                        }
                    )
                }
            )
        }.objectListField
        assertEquals(1, list.size)
        val observer = async {
            list.asFlow(listOf("objectListField.stringField")).collect {
                c.trySend(it)
            }
        }
        assertIs<InitialList<RealmListContainer>>(c.receiveOrFail())
        realm.write {
            // Update field that should not trigger a notification
            findLatest(list.first())!!.id = 1
        }
        realm.write {
            // Update field that should trigger a notification
            findLatest(list.first())!!.objectListField.first().stringField = "Bar"
        }
        c.receiveOrFail().let { listChange ->
            assertIs<UpdatedList<RealmListContainer>>(listChange)
            when (listChange) {
                is UpdatedList -> {
                    assertEquals(1, listChange.changes.size)
                }
                else -> fail("Unexpected change: $listChange")
            }
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_defaultDepth() = runBlocking<Unit> {
        val c = Channel<ListChange<RealmListContainer>>(1)
        val list = realm.write {
            copyToRealm(
                RealmListContainer().apply {
                    this.id = 1
                    this.stringField = "parent"
                    this.objectListField = realmListOf(
                        RealmListContainer().apply {
                            this.stringField = "child"
                            this.objectListField = realmListOf(
                                RealmListContainer().apply {
                                    this.stringField = "child-child"
                                    this.objectListField = realmListOf(
                                        RealmListContainer().apply {
                                            this.stringField = "child-child-child"
                                            this.objectListField = realmListOf(
                                                RealmListContainer().apply {
                                                    this.stringField = "child-child-child-child"
                                                    this.objectListField = realmListOf(
                                                        RealmListContainer().apply {
                                                            this.stringField = "child-child-child-child-child"
                                                            this.objectListField = realmListOf(
                                                                RealmListContainer().apply {
                                                                    this.stringField = "BottomChild"
                                                                }
                                                            )
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }.objectListField
        val observer = async {
            // Default keypath
            list.asFlow().collect {
                c.trySend(it)
            }
        }
        assertIs<InitialList<RealmListContainer>>(c.receiveOrFail())
        realm.write {
            // Update below the default limit should not trigger a notification
            val obj = findLatest(list.first())!!.objectListField.first().objectListField.first().objectListField.first().objectListField.first().objectListField.first()
            obj.stringField = "Bar"
        }
        realm.write {
            // Update field that should trigger a notification
            findLatest(list.first())!!.id = 1
        }
        c.receiveOrFail().let { listChange ->
            assertIs<UpdatedList<RealmListContainer>>(listChange)
            when (listChange) {
                is ListChange -> {
                    // Core will only report something changed to the top-level property.
                    assertEquals(1, listChange.changes.size)
                    // Default value is -1, so if this event is triggered by the first write
                    // this assert will fail
                    assertEquals(1, listChange.list.first().id)
                }
                else -> fail("Unexpected change: $listChange")
            }
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_propertyBelowDefaultLimit() = runBlocking<Unit> {
        val c = Channel<ListChange<RealmListContainer>>(1)
        val list = realm.write {
            copyToRealm(
                RealmListContainer().apply {
                    this.id = 1
                    this.stringField = "parent"
                    this.objectListField = realmListOf(
                        RealmListContainer().apply {
                            this.stringField = "child"
                            this.objectListField = realmListOf(
                                RealmListContainer().apply {
                                    this.stringField = "child-child"
                                    this.objectListField = realmListOf(
                                        RealmListContainer().apply {
                                            this.stringField = "child-child-child"
                                            this.objectListField = realmListOf(
                                                RealmListContainer().apply {
                                                    this.stringField = "child-child-child-child"
                                                    this.objectListField = realmListOf(
                                                        RealmListContainer().apply {
                                                            this.stringField = "child-child-child-child-child"
                                                            this.objectListField = realmListOf(
                                                                RealmListContainer().apply {
                                                                    this.stringField = "BottomChild"
                                                                }
                                                            )
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        }.objectListField
        val observer = async {
            list.asFlow(listOf("objectListField.objectListField.objectListField.objectListField.objectListField.stringField")).collect {
                c.trySend(it)
            }
        }
        assertIs<InitialList<Sample>>(c.receiveOrFail())
        realm.write {
            // Update field that should not trigger a notification
            findLatest(list.first())!!.stringField = "Parent change"
        }
        realm.write {
            // Update field that should trigger a notification
            val obj = findLatest(list.first())!!.objectListField.first().objectListField.first().objectListField.first().objectListField.first().objectListField.first()
            obj.stringField = "Bar"
        }
        c.receiveOrFail().let { listChange ->
            assertIs<UpdatedList<RealmListContainer>>(listChange)
            when (listChange) {
                is ListChange -> {
                    // Core will only report something changed to the top-level property.
                    assertEquals(1, listChange.changes.size)
                }
                else -> fail("Unexpected change: $listChange")
            }
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_unknownTopLevelProperty() = runBlocking<Unit> {
        val list = realm.write { copyToRealm(RealmListContainer()) }.objectListField
        assertFailsWith<IllegalArgumentException>() {
            list.asFlow(listOf("foo"))
        }
    }

    @Test
    override fun keyPath_unknownNestedProperty() = runBlocking<Unit> {
        val list = realm.write { copyToRealm(RealmListContainer()) }.objectListField
        assertFailsWith<IllegalArgumentException>() {
            list.asFlow(listOf("objectListField.foo"))
        }
    }

    @Test
    override fun keyPath_invalidNestedProperty() = runBlocking<Unit> {
        val list = realm.write { copyToRealm(RealmListContainer()) }.objectListField
        assertFailsWith<IllegalArgumentException> {
            list.asFlow(listOf("intField.foo"))
        }
        assertFailsWith<IllegalArgumentException> {
            list.asFlow(listOf("objectListField.intListField.foo"))
        }
    }

    @Test
    fun eventsOnObjectChangesInList() {
        runBlocking {
            val channel = Channel<ListChange<RealmListContainer>>(10)
            val parent = realm.write { copyToRealm(RealmListContainer()).apply { stringField = "PARENT" }}

            val listener = async {
                parent.objectListField.asFlow().collect {
                    channel.trySendOrFail(it)
                }
            }

            channel.receiveOrFail(message = "Initial event").let { assertIs<InitialList<*>>(it) }

            realm.write {
                findLatest(parent)!!.objectListField.add(
                    RealmListContainer().apply { stringField = "CHILD" }
                )
            }
            channel.receiveOrFail(message = "List add").let {
                assertIs<UpdatedList<*>>(it)
                assertEquals(1, it.list.size)
            }

            realm.write {
                findLatest(parent)!!.objectListField[0].stringField = "TEST"
            }
            channel.receiveOrFail(message = "Object updated").let {
                assertIs<UpdatedList<*>>(it)
                assertEquals(1, it.list.size)
                assertEquals("TEST", it.list[0].stringField)
            }

            listener.cancel()
        }
    }
    @Test
    fun eventsOnObjectChangesInRealmAnyList() {
        runBlocking {
            val channel = Channel<ListChange<RealmAny?>>(10)
            val parent = realm.write { copyToRealm(RealmListContainer()).apply { stringField = "PARENT" }}

            val listener = async {
                parent.nullableRealmAnyListField.asFlow().collect {
                    channel.trySendOrFail(it)
                }
            }

            channel.receiveOrFail(message = "Initial event").let { assertIs<InitialList<*>>(it) }

            realm.write {
                findLatest(parent)!!.nullableRealmAnyListField.add(RealmAny.create(
                    RealmListContainer().apply { stringField = "CHILD" }))
            }
            channel.receiveOrFail(message = "List add").let {
                assertIs<UpdatedList<*>>(it)
                assertEquals(1, it.list.size)
            }

            realm.write {
                findLatest(parent)!!.nullableRealmAnyListField[0]!!.asRealmObject<RealmListContainer>().stringField = "TEST"
            }
            channel.receiveOrFail(message = "Object updated").let {
                assertIs<UpdatedList<*>>(it)
                assertEquals(1, it.list.size)
                assertEquals("TEST", it.list[0]!!.asRealmObject<RealmListContainer>().stringField)
            }

            listener.cancel()
        }
    }


    fun RealmList<*>.removeRange(range: IntRange) {
        range.reversed().forEach { index -> removeAt(index) }
    }
}
