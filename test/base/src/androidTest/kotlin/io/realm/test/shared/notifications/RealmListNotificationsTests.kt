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
import io.realm.RealmList
import io.realm.entities.list.RealmListContainer
import io.realm.internal.platform.freeze
import io.realm.notifications.DeletedList
import io.realm.notifications.InitialList
import io.realm.notifications.ListChange
import io.realm.notifications.UpdatedList
import io.realm.test.NotificationTests
import io.realm.test.assertIsChangeSet
import io.realm.test.platform.PlatformUtils
import io.realm.test.shared.OBJECT_VALUES
import io.realm.test.shared.OBJECT_VALUES2
import io.realm.test.shared.OBJECT_VALUES3
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class RealmListNotificationsTests : NotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(schema = setOf(RealmListContainer::class))
            .path("$tmpDir/default.realm").build()
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
            val channel = Channel<ListChange<RealmList<*>>>(capacity = 1)
            val observer = async {
                container.objectListField
                    .asFlow()
                    .collect { flowList ->
                        channel.send(flowList)
                    }
            }

            // Assertion after empty list is emitted
            channel.receive().let { listChange ->
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
            val channel = Channel<ListChange<RealmList<*>>>(capacity = 1)
            val observer = async {
                container.objectListField
                    .asFlow()
                    .collect { flowList ->
                        channel.send(flowList)
                    }
            }

            // Assertion after empty list is emitted
            channel.receive().let { listChange ->
                assertIs<InitialList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(0, listChange.list.size)
            }

            // Assert a single range is reported
            //
            // objectListField = [<A, B>]
            realm.writeBlocking {
                val queriedContainer = findLatest(container)
                val queriedList = queriedContainer!!.objectListField
                queriedList.addAll(dataset)
            }

            channel.receive().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset.size, listChange.list.size)

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    insertRanges = arrayOf(
                        ListChange.Range(0, 2)
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

            channel.receive().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset.size + dataset2.size + dataset3.size, listChange.list.size)

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    insertRanges = arrayOf(
                        ListChange.Range(0, 4),
                        ListChange.Range(6, 2)
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

            channel.receive().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset.size, listChange.list.size)

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    deletionRanges = arrayOf(
                        ListChange.Range(0, 4),
                        ListChange.Range(6, 2)
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

            channel.receive().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertTrue(listChange.list.isEmpty())

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    deletionRanges = arrayOf(
                        ListChange.Range(0, 2)
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
            channel.receive().let { listChange ->
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

            channel.receive().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset2.size, listChange.list.size)

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    changesRanges = arrayOf(
                        ListChange.Range(0, 2),
                        ListChange.Range(3, 1),
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

            channel.receive().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset2.size, listChange.list.size)

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    changesRanges = arrayOf(
                        ListChange.Range(0, 4)
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
            // Freeze values since native complains if we reference a package-level defined variable
            // inside a write block
            val values = OBJECT_VALUES.freeze()
            val container = realm.write {
                copyToRealm(RealmListContainer())
            }
            val channel1 = Channel<ListChange<RealmList<*>>>(1)
            val channel2 = Channel<ListChange<RealmList<*>>>(1)
            val observer1 = async {
                container.objectListField
                    .asFlow()
                    .collect { flowList ->
                        channel1.trySend(flowList)
                    }
            }
            val observer2 = async {
                container.objectListField
                    .asFlow()
                    .collect { flowList ->
                        channel2.trySend(flowList)
                    }
            }

            // Ignore first emission with empty lists
            channel1.receive()
            channel2.receive()

            // Trigger an update
            realm.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.objectListField.addAll(values)
            }
            assertEquals(OBJECT_VALUES.size, channel1.receive().list.size)
            assertEquals(OBJECT_VALUES.size, channel2.receive().list.size)

            // Cancel observer 1
            observer1.cancel()

            // Trigger another update
            realm.write {
                val queriedContainer = findLatest(container)
                queriedContainer!!.objectListField
                    .add(copyToRealm(RealmListContainer().apply { stringField = "C" }))
            }

            // Check channel 1 didn't receive the update
            assertEquals(OBJECT_VALUES.size + 1, channel2.receive().list.size)
            assertTrue(channel1.isEmpty)

            observer2.cancel()
            channel1.close()
            channel2.close()
        }
    }

    @Test
    override fun deleteObservable() {
        runBlocking {
            // Freeze values since native complains if we reference a package-level defined variable
            // inside a write block
            val values = OBJECT_VALUES.freeze()
            val channel1 = Channel<ListChange<RealmList<*>>>(capacity = 1)
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
            channel1.receive().let { listChange ->
                assertIs<InitialList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(OBJECT_VALUES.size, listChange.list.size)
            }

            // Now delete owner
            realm.write {
                delete(findLatest(container)!!)
            }

            channel1.receive().let { listChange ->
                assertIs<DeletedList<*>>(listChange)
                assertTrue(listChange.list.isEmpty())
            }
            // Wait for flow completion
            assertTrue(channel2.receive())

            observer.cancel()
            channel1.close()
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
            val channel = Channel<ListChange<RealmList<*>>>(capacity = 1)
            val container = realm.write {
                copyToRealm(RealmListContainer())
            }
            val observer = async {
                container.objectListField
                    .asFlow()
                    .collect { flowList ->
                        channel.trySend(flowList)
                    }
                fail("Flow should not be canceled.")
            }

            assertTrue(channel.receive().list.isEmpty())

            realm.close()
            observer.cancel()
            channel.close()
        }
    }

    fun RealmList<*>.removeRange(range: IntRange) {
        range.reversed().forEach { index -> removeAt(index) }
    }
}
