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

import co.touchlab.stately.concurrency.AtomicInt
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmResults
import io.realm.internal.singleThreadDispatcher
import io.realm.observe
import io.realm.util.PlatformUtils
import io.realm.util.Utils.createRandomString
import io.realm.util.Utils.printlntid
import io.realm.util.update
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import test.Sample
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class NotificationTests {

    enum class ClassType {
        REALM,
        REALM_RESULTS,
        REALM_LIST,
        REALM_OBJECT
    }

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration(path = "$tmpDir/${createRandomString(16)}.realm", schema = setOf(Sample::class))
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (!realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    private fun testClassTypes(block: (type: ClassType) -> Unit) {
        ClassType.values().forEach {
            try {
                block(it)
            } catch (ex: Throwable) {
                println("Test for type failed: $it")
                throw ex
            }
        }
    }

    @Test
    fun observe() {
        testClassTypes {
            when (it) {
                ClassType.REALM -> observeRealm()
                ClassType.REALM_RESULTS -> observeRealmResults()
                ClassType.REALM_LIST -> observeRealmList()
                ClassType.REALM_OBJECT -> observeRealmObject()
                else -> throw NotImplementedError(it.toString())
            }
        }
    }

    // Verify that a flow can be cancelled
    @Test
    fun cancelObserve() {
        testClassTypes {
            when (it) {
                ClassType.REALM -> cancelObserveRealm()
                ClassType.REALM_RESULTS -> cancelObserveRealmResults()
                ClassType.REALM_LIST -> cancelObserveRealmList()
                ClassType.REALM_OBJECT -> cancelObserveRealmObject()
                else -> throw NotImplementedError(it.toString())
            }
        }
    }

    // Verify that the initial element in a Flow is the element itself
    // TODO Is this the semantics we want?
    @Test
    fun initialElement() {
        testClassTypes {
            when (it) {
                ClassType.REALM -> initialElementRealm()
                ClassType.REALM_RESULTS -> initialElementRealmResults()
                ClassType.REALM_LIST -> initialElementRealmList()
                ClassType.REALM_OBJECT -> initialElementRealmObject()
                else -> throw NotImplementedError(it.toString())
            }
        }
    }

    // Verify that `null` is emitted and the Flow is closed whenever the object
    // being observed is deleted.
    @Test
    fun deleteObservable() {
        testClassTypes {
            when (it) {
                ClassType.REALM -> { /* Realms cannot be deleted, ignore */ }
                ClassType.REALM_RESULTS -> deleteObservableRealmResults()
                ClassType.REALM_LIST -> deleteObservableRealmList()
                ClassType.REALM_OBJECT -> deleteObservableRealmObject()
                else -> throw NotImplementedError(it.toString())
            }
        }
    }

    // Verify that closing the Realm while inside a flow throws an exception (I think)
    @Test
    @Ignore // Wait for https://github.com/realm/realm-kotlin/pull/300 to be merged before fleshing this out
    fun closeRealmInsideFlowThrows() {
        testClassTypes {
            when (it) {
                ClassType.REALM -> { closeInsideFlowRealm() }
                ClassType.REALM_RESULTS -> closeInsideFlowRealmResults()
                ClassType.REALM_LIST -> closeInsideFlowRealmList()
                ClassType.REALM_OBJECT -> closeInsideFlowRealmObject()
                else -> throw NotImplementedError(it.toString())
            }
        }
    }

    // Currently, closing a Realm will not cancel any flows from Realm
    //
    @Test
    @Ignore // Until proper Realm tracking is in place
    fun closingRealmDoesNotCancelFlows() {
        testClassTypes {
            when (it) {
                ClassType.REALM -> { closingRealmDoesNotCancelRealmFlow() }
                ClassType.REALM_RESULTS -> closingRealmDoesNotCancelRealmResultsFlow()
                ClassType.REALM_LIST -> closingRealmDoesNotCancelRealmListFlow()
                ClassType.REALM_OBJECT -> closingRealmDoesNotCancelRealmObjectFlow()
                else -> throw NotImplementedError(it.toString())
            }
        }
    }

    private fun closingRealmDoesNotCancelRealmFlow() {
        // FIXME Wait for Realm change listener support
    }

    private fun closingRealmDoesNotCancelRealmResultsFlow() = runBlocking {
        val c = Channel<Int>(capacity = 1)
        val observer = async {
            realm.objects(Sample::class).observe().collect {
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

    private fun closingRealmDoesNotCancelRealmListFlow() {
        // FIXME Wait for RealmList change listener support
    }

    private fun closingRealmDoesNotCancelRealmObjectFlow() = runBlocking {
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

    @Test
    fun addChangeListener() {
        // FIXME Implement in another PR
    }

    @Test
    fun addChangeListener_emitOnProvidedDispatcher() {
        // FIXME Implement in another PR
    }

    @Test
    fun openSameRealmFileWithDifferentDispatchers() {
        // FIXME This seems to not work
    }

    // Verify that the Main dispatcher can be used for both writes and notifications
    // It should be considered an anti-pattern in production, but is plausible in tests.
    @Test
    fun useMainDispatchers() {
        // FIXME
    }

    // Verify that users can use the Main dispatcher for notifications and a background
    // dispatcher for writes. This is the closest match to how this currently works
    // in Realm Java.
    @Test
    fun useMainNotifierDispatcherAndBackgroundWriterDispatcher() {
        // FIXME
    }

    // Verify that the special test dispatchers provided by Google also when using Realm.
    @Test
    fun useTestDispatchers() {
        // FIXME
    }

    private fun closeInsideFlowRealm() = runBlocking {
        // FIXME Waiting for Realm changelistener support
    }

    private fun closeInsideFlowRealmResults() = runBlocking {
        val c = Channel<Int>(capacity = 1)
        val counter = AtomicInt(0)
        val observer1 = async {
            realm.objects(Sample::class).observe().collect {
                when(counter.incrementAndGet()) {
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
            realm.objects(Sample::class).observe().collect {
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

    private fun closeInsideFlowRealmList() {
        // FIXME Waiting for list support
    }

    private fun closeInsideFlowRealmObject() {
        // FIXME TODO("Not yet implemented")
    }

    private fun initialElementRealm() {
        // FIXME Waiting for Realm change listener support
    }

    private fun initialElementRealmResults() = runBlocking {
        val c = Channel<RealmResults<Sample>>(1)
        val observer = async {
            realm.objects(Sample::class).observe().collect {
                c.trySend(it)
            }
        }
        val initialElement: RealmResults<Sample> = c.receive()
        assertEquals(0, initialElement.size)
        observer.cancel()
        c.close()
    }

    private fun initialElementRealmObject() = runBlocking {
        val c = Channel<Sample?>(1)
        val obj = realm.write {
            copyToRealm(Sample().apply {
                stringField = "Foo"
            })
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

    private fun initialElementRealmList() {
        // FIXME Waiting for RealmList support
    }

    private fun deleteObservableRealmResults() = runBlocking {
        val c = Channel<RealmResults<Sample>>(1)
        realm.write {
            copyToRealm(Sample().apply {
                stringField = "Foo"
            })
        }
        val observer = async {
            realm.objects(Sample::class).observe().collect {
                c.trySend(it)
            }
        }
        assertEquals(1, c.receive().size)
        realm.write {
            delete(objects(Sample::class).first())
        }
        assertEquals(0, c.receive().size)
        observer.cancel()
        c.close()
    }

    private fun deleteObservableRealmList() {
        // FIXME Waiting for list observer tests
    }

    private fun deleteObservableRealmObject() = runBlocking {
        val c = Channel<Sample?>(1)
        val obj: Sample = realm.write {
            copyToRealm(Sample().apply { stringField = "Foo" })
        }
        val observer = async {
            obj.observe().collect {
                c.trySend(it)
            }
            // Emit sentinel value to signal that flow completed
            c.send(Sample())
        }
        assertNotNull(c.receive())
        realm.write {
            delete(findLatest(obj)!!)
        }
        assertNull(c.receive()) // Null is sent when object is deleted
        assertEquals(Sample().stringField, c.receive()!!.stringField) // Test for sentinel value
        observer.cancel()
        c.close()
    }

    private fun cancelObserveRealmObject() = runBlocking {
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

    private fun cancelObserveRealmList() {
        // FIXME Wait for RealmList support
    }

    private fun cancelObserveRealmResults() = runBlocking {
        val c1 = Channel<RealmResults<Sample>>(1)
        val c2 = Channel<RealmResults<Sample>>(1)
        val observer1 = async {
            realm.objects(Sample::class).observe().collect {
                c1.trySend(it)
            }
        }
        val observer2 = async {
            realm.objects(Sample::class).observe().collect {
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

    private fun cancelObserveRealm() {
        // FIXME Wait for Realm observer support
    }

    private fun observeRealmObject() = runBlocking {
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

    private fun observeRealmList() {
        // FIXME Wait for RealmList support
    }

    private fun observeRealmResults() = runBlocking {
        val c = Channel<Int>(capacity = 1)
        val observer = async {
            realm.objects(Sample::class).observe().collect {
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

    private fun observeRealm() {
        // FIXME Wait for a Global change listener to become available
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
