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
package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.Realm
import io.realm.kotlin.entities.sync.flx.FlexChildObject
import io.realm.kotlin.entities.sync.flx.FlexEmbeddedObject
import io.realm.kotlin.entities.sync.flx.FlexParentObject
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.exceptions.BadFlexibleSyncQueryException
import io.realm.kotlin.mongodb.subscriptions
import io.realm.kotlin.mongodb.sync.Subscription
import io.realm.kotlin.mongodb.sync.SubscriptionSetState
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TEST_APP_PARTITION
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.use
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Class wrapping tests for SubscriptionSets
 */
class SubscriptionSetTests {

    private lateinit var app: TestApp
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        app = TestApp(appName = TEST_APP_FLEX)
        val (email, password) = TestHelper.randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
        val config = SyncConfiguration.Builder(
            user,
            schema = setOf(FlexParentObject::class, FlexChildObject::class, FlexEmbeddedObject::class)
        )
            .build()
        realm = Realm.open(config)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        if (this::app.isInitialized) {
            app.close()
        }
    }

    // Verify that we only have a single SubscriptionSet instance exposed to end users
    @Test
    fun realmSubscriptionsReturnSameInstance() {
        val sub1 = realm.subscriptions
        val sub2 = realm.subscriptions
        assertSame(sub1, sub2)
        sub1.close()
        sub2.close()
    }

    @Test
    fun subscriptions_failOnNonFlexibleSyncRealms() {
        val app = TestApp(appName = TEST_APP_PARTITION)
        val (email, password) = TestHelper.randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
        val config = SyncConfiguration.create(
            user,
            TestHelper.randomPartitionValue(),
            setOf(FlexParentObject::class, FlexChildObject::class, FlexEmbeddedObject::class)
        )
        Realm.open(config).use { partionBasedRealm ->
            assertFailsWith<IllegalStateException> { partionBasedRealm.subscriptions }
        }
    }

    @Test
    fun subscriptions_throwsOnClosedRealm() {
        realm.close()
        assertFailsWith<IllegalStateException> { realm.subscriptions }
    }

    @Test
    fun initialSubscriptions() {
        val subscriptions = realm.subscriptions
        assertEquals(0, subscriptions.size)
        val initialState = subscriptions.state
        val expectedStates = listOf(
            SubscriptionSetState.PENDING,
            SubscriptionSetState.BOOTSTRAPPING,
            SubscriptionSetState.COMPLETE,
        )
        assertTrue(expectedStates.contains(initialState), "State was: $initialState")
        subscriptions.close()
    }

    @Test
    fun findByQuery() = runBlocking {
        val query = realm.query<FlexParentObject>()
        val subscriptions = realm.subscriptions
        assertNull(subscriptions.findByQuery(query))
        subscriptions.update { add(query) }
        val sub: Subscription = subscriptions.findByQuery(query)!!
        assertNotNull(sub)
        assertEquals("FlexParentObject", sub.objectType)
        assertEquals("TRUEPREDICATE ", sub.queryDescription)
        subscriptions.close()
    }

    @Test
    fun findByName() = runBlocking {
        val subscriptions = realm.subscriptions
        assertNull(subscriptions.findByName("foo"))
        subscriptions.update {
            realm.query<FlexParentObject>().subscribe("foo")
        }
        val sub: Subscription = subscriptions.findByName("foo")!!
        assertNotNull(sub)
        assertEquals("foo", sub.name)
        subscriptions.close()
    }

    @Test
    fun state() = runBlocking {
        val subscriptions = realm.subscriptions
        subscriptions.update {
            realm.query<FlexParentObject>().subscribe("test1")
        }
        assertEquals(SubscriptionSetState.PENDING, subscriptions.state)
        subscriptions.waitForSynchronization()
        assertEquals(SubscriptionSetState.COMPLETE, subscriptions.state)
        subscriptions.update {
            // Flexible Sync queries cannot use limit
            realm.query<FlexParentObject>("age > 42 LIMIT(1)").subscribe("test2")
        }
        assertFailsWith<BadFlexibleSyncQueryException> {
            subscriptions.waitForSynchronization()
        }
        assertEquals(SubscriptionSetState.ERROR, subscriptions.state)
        subscriptions.close()
    }

    @Test
    fun size() = runBlocking {
        val subscriptions = realm.subscriptions
        assertEquals(0, subscriptions.size)
        subscriptions.update {
            realm.query<FlexParentObject>().subscribe()
        }
        assertEquals(1, subscriptions.size)
        subscriptions.update {
            removeAll()
        }
        assertEquals(0, subscriptions.size)
        subscriptions.close()
    }

    @Test
    fun errorMessage() = runBlocking {
        val subscriptions = realm.subscriptions
        assertNull(subscriptions.errorMessage)
        subscriptions.update {
            realm.query<FlexParentObject>("age > 42 LIMIT(1)").subscribe()
        }
        assertFailsWith<BadFlexibleSyncQueryException> {
            subscriptions.waitForSynchronization()
        }
        assertTrue(subscriptions.errorMessage!!.contains("Client provided query with bad syntax"))
        subscriptions.update {
            removeAll()
        }
        subscriptions.waitForSynchronization()
        assertNull(subscriptions.errorMessage)
        subscriptions.close()
    }

    @Test
    fun iterator_zeroSize() {
        val subscriptions = realm.subscriptions
        val iterator: Iterator<Subscription> = subscriptions.iterator()
        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.next() }
        subscriptions.close()
    }

    @Test
    fun iterator() = runBlocking {
        val subscriptions = realm.subscriptions
        subscriptions.update {
            realm.query<FlexParentObject>().subscribe("sub1")
        }
        val iterator: Iterator<Subscription> = subscriptions.iterator()
        assertTrue(iterator.hasNext())
        assertEquals("sub1", iterator.next().name)
        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.next() }
        subscriptions.close()
        Unit
    }

    @Test
    fun waitForSynchronizationInitialSubscriptions() = runBlocking {
        val subscriptions = realm.subscriptions
        assertTrue(subscriptions.waitForSynchronization())
        assertEquals(SubscriptionSetState.COMPLETE, subscriptions.state)
        assertEquals(0, subscriptions.size)
        subscriptions.close()
    }

    @Test
    fun waitForSynchronizationInitialEmptySubscriptionSet() = runBlocking {
        val subscriptions = realm.subscriptions
        subscriptions.update { /* Do nothing */ }
        assertTrue(subscriptions.waitForSynchronization())
        assertEquals(SubscriptionSetState.COMPLETE, subscriptions.state)
        assertEquals(0, subscriptions.size)
        subscriptions.close()
    }

    @Test
    fun waitForSynchronization_success() = runBlocking {
        val subscriptions = realm.subscriptions
        val updatedSubs = subscriptions.update {
            realm.query<FlexParentObject>().subscribe("test")
        }
        assertNotEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
        assertTrue(updatedSubs.waitForSynchronization())
        assertEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
        subscriptions.close()
    }

    @Test
    fun waitForSynchronization_error() = runBlocking {
        val subscriptions = realm.subscriptions
        val updatedSubs = subscriptions.update {
            realm.query<FlexParentObject>("age > 42 LIMIT(1)").subscribe("test")
        }
        assertFailsWith<BadFlexibleSyncQueryException> {
            updatedSubs.waitForSynchronization()
        }
        assertEquals(SubscriptionSetState.ERROR, updatedSubs.state)
        assertTrue(updatedSubs.errorMessage!!.contains("Client provided query with bad syntax"))
        subscriptions.close()
    }

    // Test case for https://github.com/realm/realm-core/issues/5504
    @Test
    fun waitForSynchronization_errorOnDescriptors() = runBlocking {
        val subscriptions = realm.subscriptions
        val updatedSubs = subscriptions.update {
            realm.query<FlexParentObject>().limit(1).subscribe("test")
        }
        assertFailsWith<BadFlexibleSyncQueryException> {
            updatedSubs.waitForSynchronization()
        }
        assertEquals(SubscriptionSetState.ERROR, updatedSubs.state)
        assertEquals("TRUEPREDICATE and TRUEPREDICATE LIMIT(1)", updatedSubs.first().queryDescription)
        assertTrue(updatedSubs.errorMessage!!.contains("Client provided query with bad syntax"))
        subscriptions.close()
    }

    @Test
    fun waitForSynchronization_timeOut() = runBlocking {
        val subscriptions = realm.subscriptions
        val updatedSubs = subscriptions.update {
            realm.query<FlexParentObject>().subscribe()
        }
        assertTrue(updatedSubs.waitForSynchronization(1.minutes))
        subscriptions.close()
    }

    @Test
    fun waitForSynchronization_timeOutFails() = runBlocking {
        val subscriptions = realm.subscriptions
        val updatedSubs = subscriptions.update {
            realm.query<FlexParentObject>().subscribe()
        }
        assertFalse(updatedSubs.waitForSynchronization(1.nanoseconds))
        subscriptions.close()
    }

    @Test
    fun methodsOnClosedRealm() = runBlocking {
        // SubscriptionSets own their own DB resources, which is disconnected from the
        // user facing Realm. This means that the subscription set technically can still
        // be modified after the Realm is closed, but since this would produce awkward interactions
        // with other API's that work on the Realm file, we should disallow modifying the
        // SubscriptionSet if the Realm is closed. Just accessing data should be fine.
        val subscriptions = realm.subscriptions
        val subs = subscriptions.update {
            realm.query<FlexParentObject>().subscribe("sub")
        }.also {
            it.waitForSynchronization()
        }
        realm.close()

        // Valid methods
        assertEquals(1, subs.size)
        assertEquals(SubscriptionSetState.COMPLETE, subs.state)
        assertNull(subs.errorMessage)
        assertNotNull(subs.findByName("sub"))
        // `findByQuery` does not work as queries will throw on closed Realms.
        val iter = subs.iterator()
        assertTrue(iter.hasNext())
        assertNotNull(iter.next())

        // These methods will throw
        assertFailsWith<IllegalStateException> {
            subs.refresh()
        }
        assertFailsWith<IllegalStateException> {
            subs.waitForSynchronization()
        }
        assertFailsWith<IllegalStateException> {
            subs.update { /* Do nothing */ }
        }

        // Reading subscription data will also work
        assertEquals("sub", subs.first().name)
        subscriptions.close()
        subs.close()
    }
}
