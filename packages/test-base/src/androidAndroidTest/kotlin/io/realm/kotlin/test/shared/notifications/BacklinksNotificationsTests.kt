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

package io.realm.kotlin.test.shared.notifications

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.notifications.InitialResults
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.test.RealmEntityNotificationTests
import io.realm.kotlin.test.platform.PlatformUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

class BacklinksNotificationsTests : RealmEntityNotificationTests {

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
            realm.write {
                copyToRealm(Sample())
            }.let { sample ->
                listOf(
                    sample.objectBacklinks,
                    sample.listBacklinks,
                    sample.setBacklinks
                )
            }.forEach { results ->
                val c = Channel<ResultsChange<Sample>>(1)

                val observer = async {
                    results
                        .asFlow()
                        .collect {
                            c.trySend(it)
                        }
                }

                c.receive().let { resultsChange ->
                    assertIs<InitialResults<*>>(resultsChange)
                    assertTrue(resultsChange.list.isEmpty())
                }

                observer.cancel()
                c.close()
            }
        }
    }

    @Test
    @Suppress("LongMethod")
    override fun asFlow() {
        runBlocking {
            val targetObject = realm.write {
                val target = copyToRealm(Sample())
                copyToRealm(
                    Sample().apply {
                        nullableObject = target
                    }
                )
                copyToRealm(
                    Sample().apply {
                        objectListField.add(target)
                    }
                )
                copyToRealm(
                    Sample().apply {
                        objectSetField.add(target)
                    }
                )

                target
            }

            targetObject.run {
                listOf(
                    objectBacklinks,
                    listBacklinks,
                    setBacklinks
                )
            }.forEach { results ->
                val c = Channel<ResultsChange<*>>(capacity = 1)
                val observer = async {
                    results
                        .asFlow()
                        .collect {
                            c.trySend(it)
                        }
                }

                // Assertion after empty list is emitted
                c.receive().let { resultsChange ->
                    assertIs<InitialResults<*>>(resultsChange)

                    assertNotNull(resultsChange.list)
                    assertEquals(1, resultsChange.list.size)
                }

                // remove element by deleting object
                realm.writeBlocking {
                    delete(findLatest(results.first())!!)
                }

                c.receive().let { resultsChange ->
                    assertIs<UpdatedResults<*>>(resultsChange)

                    assertNotNull(resultsChange.list)
                    assertEquals(0, resultsChange.list.size)
                }

                observer.cancel()
                c.close()
            }
        }
    }

    @Test
    fun verifyChangeEvents() {
        runBlocking {
            val target = realm.write {
                copyToRealm(Sample())
            }
            val c = Channel<ResultsChange<Sample>>(capacity = 5)
            val collection = async {
                target.objectBacklinks.asFlow().collect {
                    c.trySend(it)
                }
            }

            c.receive().let {
                assertIs<InitialResults<*>>(it)
                assertEquals(0, it.list.size)
            }

            realm.write {
                copyToRealm(Sample().apply { nullableObject = findLatest(target) })
            }

            c.receive().let {
                assertIs<UpdatedResults<*>>(it)
                assertEquals(1, it.list.size)
                assertEquals(0, it.deletions.size)
                assertEquals(1, it.insertions.size)
            }
            collection.cancel()
        }
    }

    @Suppress("LongMethod")
    @Test
    override fun cancelAsFlow() {
        runBlocking {
            val targetObject = realm.write {
                val target = copyToRealm(Sample())
                copyToRealm(
                    Sample().apply {
                        nullableObject = target
                    }
                )
                copyToRealm(
                    Sample().apply {
                        objectListField.add(target)
                    }
                )
                copyToRealm(
                    Sample().apply {
                        objectSetField.add(target)
                    }
                )

                target
            }

            targetObject.run {
                listOf(
                    objectBacklinks,
                    listBacklinks,
                    setBacklinks
                )
            }.forEach { results ->
                val c1 = Channel<ResultsChange<Sample>>(1)
                val c2 = Channel<ResultsChange<Sample>>(1)

                val observer1 = async {
                    results.asFlow().collect {
                        c1.trySend(it)
                    }
                }
                val observer2 = async {
                    results.asFlow().collect {
                        c2.trySend(it)
                    }
                }

                c1.receive().let { resultsChange ->
                    assertIs<InitialResults<*>>(resultsChange)
                    assertEquals(1, resultsChange.list.size)
                }
                c2.receive().let { resultsChange ->
                    assertIs<InitialResults<*>>(resultsChange)
                    assertEquals(1, resultsChange.list.size)
                }

                observer1.cancel()

                realm.writeBlocking {
                    delete(findLatest(results.first())!!)
                }

                c2.receive().let { resultsChange ->
                    assertIs<UpdatedResults<*>>(resultsChange)
                    assertEquals(0, resultsChange.list.size)
                }
                assertTrue(c1.isEmpty)
                observer2.cancel()
                c1.close()
                c2.close()
            }
        }
    }

    @Test
    override fun deleteEntity() {
        runBlocking {
            listOf(
                realm.write { copyToRealm(Sample()) }.let { Pair(it, it.objectBacklinks) },
                realm.write { copyToRealm(Sample()) }.let { Pair(it, it.listBacklinks) },
                realm.write { copyToRealm(Sample()) }.let { Pair(it, it.setBacklinks) }
            ).forEach { (target: Sample, results: RealmResults<Sample>) ->
                val c = Channel<ResultsChange<*>?>(capacity = 1)
                val sc = Channel<ResultsChange<*>?>(capacity = 1)
                val observer = async {
                    results
                        .asFlow()
                        .onCompletion {
                            c.trySend(null)
                        }
                        .collect {
                            c.trySend(it)
                        }
                }

                val subqueryObserver = async {
                    results
                        .query("TRUEPREDICATE")
                        .asFlow()
                        .onCompletion {
                            sc.trySend(null)
                        }
                        .collect {
                            sc.trySend(it)
                        }
                }

                // Assertion after empty list is emitted
                c.receive()!!.let { resultsChange ->
                    assertIs<InitialResults<*>>(resultsChange)

                    assertNotNull(resultsChange.list)
                    assertEquals(0, resultsChange.list.size)
                }

                // Assertion after subquery is emitted
                sc.receive()!!.let { resultsChange ->
                    assertIs<InitialResults<*>>(resultsChange)

                    assertNotNull(resultsChange.list)
                    assertEquals(0, resultsChange.list.size)
                }

                // remove element by deleting object
                realm.writeBlocking {
                    delete(findLatest(target)!!)
                }

                assertNull(c.receive())
                assertNull(sc.receive())

                observer.cancel()
                c.close()

                subqueryObserver.cancel()
                sc.close()
            }
        }
    }

    @Test
    override fun asFlowOnDeleteEntity() {
        runBlocking {
            val sample = realm.write { copyToRealm(Sample()) }
            val mutex = Mutex(true)
            val flow = async { sample.objectBacklinks.asFlow().collect { mutex.unlock() } }

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
                sample.objectBacklinks.asFlow().collect { fail("Flow on deleted backlinks shouldn't emit any events") }
            }
        }
    }

    @Test
    @Ignore // FIXME Not correctly implemented yet
    override fun closeRealmInsideFlowThrows(): Unit = TODO("Not correctly implemented yet")

    @Test
    override fun closingRealmDoesNotCancelFlows() {
        runBlocking {
            val target = realm.write {
                copyToRealm(Sample())
            }

            val c = Channel<Int>(capacity = 1)
            val observer = async {
                target.objectBacklinks
                    .asFlow()
                    .filterNot {
                        it.list.isEmpty()
                    }
                    .collect {
                        c.send(it.list.size)
                    }
                fail("Flow should not be canceled.")
            }

            realm.write {
                copyToRealm(
                    Sample().apply {
                        nullableObject = findLatest(target)
                    }
                )
            }

            // Await that collect is actually collecting
            withTimeout(10.seconds) {
                assertEquals(1, c.receive())
            }
            realm.close()
            delay(1.seconds)
            observer.cancel()
            c.close()
        }
    }
}
