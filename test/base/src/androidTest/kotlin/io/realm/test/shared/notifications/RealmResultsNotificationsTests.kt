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
import io.realm.entities.list.RealmListContainer
import io.realm.notifications.InitialList
import io.realm.notifications.ListChange
import io.realm.notifications.UpdatedList
import io.realm.query
import io.realm.query.find
import io.realm.test.NotificationTests
import io.realm.test.assertIsChangeSet
import io.realm.test.platform.PlatformUtils
import io.realm.test.shared.OBJECT_VALUES
import io.realm.test.shared.OBJECT_VALUES2
import io.realm.test.shared.OBJECT_VALUES3
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filterNot
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

class RealmResultsNotificationsTests : NotificationTests {

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(schema = setOf(Sample::class, RealmListContainer::class))
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
            val c = Channel<ListChange<RealmResults<Sample>>>(1)
            val observer = async {
                realm.query<Sample>()
                    .asFlow()
                    .collect {
                        c.trySend(it)
                    }
            }

            c.receive().let { listChange ->
                assertIs<InitialList<*>>(listChange)
                assertTrue(listChange.list.isEmpty())
            }

            observer.cancel()
            c.close()
        }
    }

    @Test
    @Suppress("LongMethod")
    override fun asFlow() {
        val dataset1 = OBJECT_VALUES
        val dataset2 = OBJECT_VALUES2
        val dataset3 = OBJECT_VALUES3

        runBlocking {
            val c = Channel<ListChange<RealmResults<*>>>(capacity = 1)
            val observer = async {
                realm.query<RealmListContainer>()
                    .sort("stringField")
                    .asFlow()
                    .collect {
                        c.trySend(it)
                    }
            }

            // Assertion after empty list is emitted
            c.receive().let { listChange ->
                assertIs<InitialList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(0, listChange.list.size)
            }

            // Assert a single range is reported
            //
            // objectListField = [C, D, E, F]
            realm.writeBlocking {
                dataset2.forEach {
                    copyToRealm(it)
                }
            }

            c.receive().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset2.size, listChange.list.size)

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    insertRanges = arrayOf(
                        ListChange.Range(0, 4)
                    )
                )
            }

            // Assert multiple ranges are reported
            //
            // objectListField = [<A, B>, C, D, E, F, <G, H>]
            realm.writeBlocking {
                (dataset1 + dataset3).forEach {
                    copyToRealm(it)
                }
            }

            c.receive().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset1.size + dataset2.size + dataset3.size, listChange.list.size)

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    insertRanges = arrayOf(
                        ListChange.Range(0, 2),
                        ListChange.Range(6, 2)
                    )
                )
            }

            // Assert multiple ranges are deleted
            //
            // objectListField = [<A, B>, C, D, E, F, <G, H>]
            realm.writeBlocking {
                (dataset1 + dataset3).forEach { element ->
                    delete(
                        query<RealmListContainer>("stringField = $0", element.stringField)
                            .first()
                            .find()!!
                    )
                }
            }

            c.receive().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset2.size, listChange.list.size)

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    deletionRanges = arrayOf(
                        ListChange.Range(0, 2),
                        ListChange.Range(6, 2)
                    )
                )
            }

            // Assert a single range is deleted
            //
            // objectListField = [<A, B>]
            realm.writeBlocking {
                dataset2.forEach { element ->
                    delete(
                        query<RealmListContainer>("stringField = $0", element.stringField)
                            .first()
                            .find()!!
                    )
                }
            }

            c.receive().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertTrue(listChange.list.isEmpty())

                assertIsChangeSet(
                    (listChange as UpdatedList<*>),
                    deletionRanges = arrayOf(
                        ListChange.Range(0, 4)
                    )
                )
            }

            // Add some values to change
            //
            // objectListField = [<C, D, E, F>]
            realm.writeBlocking {
                dataset2.forEach {
                    copyToRealm(it)
                }
            }

            c.receive().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)

                assertNotNull(listChange.list)
                assertEquals(dataset2.size, listChange.list.size)
            }

            // Change contents of two ranges of values
            //
            // objectListField = [<A>, <B>, E, <F>]
            realm.writeBlocking {
                query<RealmListContainer>()
                    .sort("stringField")
                    .find { queriedList ->
                        queriedList[0].stringField = "A"
                        queriedList[1].stringField = "B"
                        queriedList[3].stringField = "F"
                    }
            }

            c.receive().let { listChange ->
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

            observer.cancel()
            c.close()
        }
    }

    @Test
    override fun cancelAsFlow() {
        runBlocking {
            val c1 = Channel<ListChange<RealmResults<Sample>>>(1)
            val c2 = Channel<ListChange<RealmResults<Sample>>>(1)

            realm.write {
                copyToRealm(Sample().apply { stringField = "Bar" })
            }

            val observer1 = async {
                realm.query<Sample>()
                    .asFlow()
                    .collect {
                        c1.trySend(it)
                    }
            }
            val observer2 = async {
                realm.query<Sample>()
                    .asFlow()
                    .collect {
                        c2.trySend(it)
                    }
            }

            c1.receive().let { listChange ->
                assertIs<InitialList<*>>(listChange)
                assertEquals(1, listChange.list.size)
            }
            c2.receive().let { listChange ->
                assertIs<InitialList<*>>(listChange)
                assertEquals(1, listChange.list.size)
            }

            observer1.cancel()

            realm.write {
                copyToRealm(Sample().apply { stringField = "Baz" })
            }

            c2.receive().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)
                assertEquals(2, listChange.list.size)
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
            val c = Channel<ListChange<RealmResults<Sample>>>(1)
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
            c.receive().let { listChange ->
                assertIs<InitialList<*>>(listChange)
                assertEquals(1, listChange.list.size)
            }
            realm.write {
                query<Sample>()
                    .first()
                    .find { sample ->
                        assertNotNull(sample)
                        delete(sample)
                    }
            }
            c.receive().let { listChange ->
                assertIs<UpdatedList<*>>(listChange)
                assertTrue(listChange.list.isEmpty())
            }
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
                            1 -> c.trySend(it.list.size)
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
                        println(it.list.first().stringField)
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
                        it.list.isEmpty()
                    }.collect {
                        c.send(it.list.size)
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
