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

package io.realm.kotlin.test.common.notifications

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.ext.query
import io.realm.kotlin.notifications.InitialResults
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.test.common.utils.RealmEntityNotificationTests
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TestChannel
import io.realm.kotlin.test.util.receiveOrFail
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
import kotlin.test.assertFailsWith
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
                val c = TestChannel<ResultsChange<Sample>>()

                val observer = async {
                    results
                        .asFlow()
                        .collect {
                            c.send(it)
                        }
                }

                c.receiveOrFail().let { resultsChange ->
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
                            c.send(it)
                        }
                }

                // Assertion after empty list is emitted
                c.receiveOrFail().let { resultsChange ->
                    assertIs<InitialResults<*>>(resultsChange)

                    assertNotNull(resultsChange.list)
                    assertEquals(1, resultsChange.list.size)
                }

                // remove element by deleting object
                realm.writeBlocking {
                    delete(findLatest(results.first())!!)
                }

                c.receiveOrFail().let { resultsChange ->
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
                    c.send(it)
                }
            }

            c.receiveOrFail().let {
                assertIs<InitialResults<*>>(it)
                assertEquals(0, it.list.size)
            }

            realm.write {
                copyToRealm(Sample().apply { nullableObject = findLatest(target) })
            }

            c.receiveOrFail().let {
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
                val c1 = TestChannel<ResultsChange<Sample>>()
                val c2 = TestChannel<ResultsChange<Sample>>()

                val observer1 = async {
                    results.asFlow().collect {
                        c1.send(it)
                    }
                }
                val observer2 = async {
                    results.asFlow().collect {
                        c2.send(it)
                    }
                }

                c1.receiveOrFail().let { resultsChange ->
                    assertIs<InitialResults<*>>(resultsChange)
                    assertEquals(1, resultsChange.list.size)
                }
                c2.receiveOrFail().let { resultsChange ->
                    assertIs<InitialResults<*>>(resultsChange)
                    assertEquals(1, resultsChange.list.size)
                }

                observer1.cancel()

                realm.writeBlocking {
                    delete(findLatest(results.first())!!)
                }

                c2.receiveOrFail().let { resultsChange ->
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
                            c.send(null)
                        }
                        .collect {
                            c.send(it)
                        }
                }

                val subqueryObserver = async {
                    results
                        .query("TRUEPREDICATE")
                        .asFlow()
                        .onCompletion {
                            sc.send(null)
                        }
                        .collect {
                            sc.send(it)
                        }
                }

                // Assertion after empty list is emitted
                c.receiveOrFail()!!.let { resultsChange ->
                    assertIs<InitialResults<*>>(resultsChange)

                    assertNotNull(resultsChange.list)
                    assertEquals(0, resultsChange.list.size)
                }

                // Assertion after subquery is emitted
                sc.receiveOrFail()!!.let { resultsChange ->
                    assertIs<InitialResults<*>>(resultsChange)

                    assertNotNull(resultsChange.list)
                    assertEquals(0, resultsChange.list.size)
                }

                // remove element by deleting object
                realm.writeBlocking {
                    delete(findLatest(target)!!)
                }

                assertNull(c.receiveOrFail())
                assertNull(sc.receiveOrFail())

                observer.cancel()
                c.close()

                subqueryObserver.cancel()
                sc.close()
            }
        }
    }

    @Test
    override fun asFlowOnDeletedEntity() {
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
                assertEquals(1, c.receiveOrFail())
            }
            realm.close()
            delay(1.seconds)
            observer.cancel()
            c.close()
        }
    }

    @Test
    override fun keyPath_topLevelProperty() = runBlocking<Unit> {
        val c = Channel<ResultsChange<Sample>>(1)
        realm.write {
            copyToRealm(
                Sample().apply {
                    this.stringField = "parent"
                    this.nullableObject = Sample().apply {
                        this.stringField = "child"
                    }
                }
            )
        }
        val result: RealmResults<Sample> = realm.query<Sample>("stringField = 'child'").find()
        assertEquals(1, result.first().objectBacklinks.size)
        val observer = async {
            result.asFlow(listOf("objectBacklinks")).collect {
                c.trySend(it)
            }
        }
        assertIs<InitialResults<Sample>>(c.receiveOrFail())
        realm.write {
            val child = findLatest(result.first())!!
            assertEquals("child", child.stringField)
            copyToRealm(
                Sample().apply {
                    this.stringField = "newParent"
                    this.nullableObject = child
                }
            )
        }
        c.receiveOrFail().let { resultsChange ->
            assertIs<UpdatedResults<Sample>>(resultsChange)
            when (resultsChange) {
                is UpdatedResults -> {
                    assertEquals(0, resultsChange.insertions.size)
                    assertEquals(1, resultsChange.changes.size)
                    assertEquals(0, resultsChange.deletions.size)
                    assertEquals(2, resultsChange.list.first().objectBacklinks.size)
                }
                else -> fail("Unexpected change: $resultsChange")
            }
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_nestedProperty() = runBlocking<Unit> {
        val c = Channel<ResultsChange<Sample>>(1)
        realm.write {
            copyToRealm(
                Sample().apply {
                    this.stringField = "parent"
                    this.nullableObject = Sample().apply {
                        this.stringField = "child"
                    }
                }
            )
        }
        val result: RealmResults<Sample> = realm.query<Sample>("stringField = 'child'").find()
        assertEquals(1, result.first().objectBacklinks.size)
        val observer = async {
            result.asFlow(listOf("objectBacklinks.intField")).collect {
                c.trySend(it)
            }
        }
        assertIs<InitialResults<Sample>>(c.receiveOrFail())
        realm.write {
            // Update field that should not trigger a notification
            findLatest(result.first())!!.booleanField = false
        }
        realm.write {
            findLatest(result.first())!!.objectBacklinks.first().intField = 1
        }
        c.receiveOrFail().let { resultsChange ->
            assertIs<UpdatedResults<Sample>>(resultsChange)
            when (resultsChange) {
                is UpdatedResults -> {
                    assertEquals(0, resultsChange.insertions.size)
                    assertEquals(1, resultsChange.changes.size)
                    assertEquals(0, resultsChange.deletions.size)
                    assertEquals(1, resultsChange.list.first().objectBacklinks.size)
                    // This starts at 42, if the first write triggers a change event, it will
                    // catch it here.
                    assertEquals(resultsChange.list.first().objectBacklinks.first().intField, 1)
                }
                else -> fail("Unexpected change: $resultsChange")
            }
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_defaultDepth() = runBlocking<Unit> {
        val c = Channel<ResultsChange<Sample>>(1)
        realm.write {
            copyToRealm(
                Sample().apply {
                    this.intField = 1
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
        val results = realm.query<Sample>("stringField = 'BottomChild'").find()
        val observer = async {
            // Default keypath
            results.asFlow().collect {
                c.trySend(it)
            }
        }
        assertIs<InitialResults<Sample>>(c.receiveOrFail())
        realm.write {
            // Update field that should not trigger a notification
            findLatest(results.first())!!
                .objectBacklinks.first()
                .objectBacklinks.first()
                .objectBacklinks.first()
                .objectBacklinks.first()
                .objectBacklinks.first()
                .stringField = "Bar"
        }
        realm.write {
            // Update field that should trigger a notification
            findLatest(results.first())!!.intField = 1
        }
        c.receiveOrFail().let { resultsChange ->
            assertIs<UpdatedResults<Sample>>(resultsChange)
            when (resultsChange) {
                is ResultsChange<*> -> {
                    // Default value is 42, so if this event is triggered by the first write
                    // this assert will fail
                    assertEquals(1, resultsChange.list.first().intField)
                }
                else -> fail("Unexpected change: $resultsChange")
            }
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_propertyBelowDefaultLimit() = runBlocking<Unit> {
        val c = Channel<ResultsChange<Sample>>(1)
        realm.write {
            copyToRealm(
                Sample().apply {
                    this.intField = 1
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
        val results = realm.query<Sample>("stringField = 'BottomChild'").find()
        val observer = async {
            results.asFlow(listOf("objectBacklinks.objectBacklinks.objectBacklinks.objectBacklinks.objectBacklinks.stringField")).collect {
                c.trySend(it)
            }
        }
        assertIs<InitialResults<Sample>>(c.receiveOrFail())
        realm.write {
            // Update field that should not trigger a notification
            findLatest(results.first())!!.booleanField = true
        }
        realm.write {
            // Update field that should trigger a notification
            findLatest(results.first())!!
                .objectBacklinks.first()
                .objectBacklinks.first()
                .objectBacklinks.first()
                .objectBacklinks.first()
                .objectBacklinks.first()
                .stringField = "Bar"
        }
        c.receiveOrFail().let { resultsChange ->
            assertIs<UpdatedResults<Sample>>(resultsChange)
            when (resultsChange) {
                is ResultsChange<*> -> {
                    assertEquals(0, resultsChange.insertions.size)
                    assertEquals(1, resultsChange.changes.size)
                    assertEquals(0, resultsChange.deletions.size)
                    assertEquals(1, resultsChange.list.first().objectBacklinks.size)
                }
                else -> fail("Unexpected change: $resultsChange")
            }
        }
        observer.cancel()
        c.close()
    }

    @Test
    override fun keyPath_unknownTopLevelProperty() = runBlocking<Unit> {
        val results = realm.query<Sample>()
        assertFailsWith<IllegalArgumentException>() {
            results.asFlow(listOf("foo"))
        }
    }

    @Test
    override fun keyPath_unknownNestedProperty() = runBlocking<Unit> {
        val results = realm.query<Sample>()
        assertFailsWith<IllegalArgumentException>() {
            results.asFlow(listOf("objectBacklinks.foo"))
        }
    }

    @Test
    override fun keyPath_invalidNestedProperty() = runBlocking<Unit> {
        val results = realm.query<Sample>()
        assertFailsWith<IllegalArgumentException> {
            results.asFlow(listOf("objectBacklinks.intField.foo"))
        }
        assertFailsWith<IllegalArgumentException> {
            results.asFlow(listOf("objectBacklinks.intListField.foo"))
        }
    }
}
