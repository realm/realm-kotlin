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
package io.realm.test.mongodb.shared

import io.realm.Realm
import io.realm.entities.sync.ChildPk
import io.realm.entities.sync.ParentPk
import io.realm.internal.platform.runBlocking
import io.realm.mongodb.User
import io.realm.mongodb.subscriptions
import io.realm.mongodb.sync.Subscription
import io.realm.mongodb.sync.SubscriptionSetState
import io.realm.mongodb.sync.SyncConfiguration
import io.realm.query
import io.realm.query.RealmQuery
import io.realm.test.mongodb.TEST_APP_1
import io.realm.test.mongodb.TEST_APP_FLEX
import io.realm.test.mongodb.TestApp
import io.realm.test.mongodb.createUserAndLogIn
import io.realm.test.util.TestHelper
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
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
        // ServerAdmin(app).enableFlexibleSync() // Currrently required because importing doesn't work
        val (email, password) = TestHelper.randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
        val config = SyncConfiguration.Builder(
            user,
            schema = setOf(ParentPk::class, ChildPk::class)
        )
            .build()
        realm = Realm.open(config)
    }

    @AfterTest
    fun tearDown() {
        realm.close()
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
    }

    @Test
    @Ignore
    fun subscriptions_failOnNonFlexibleSyncRealms() {
        val app = TestApp(appName = TEST_APP_1)
        val (email, password) = TestHelper.randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
        val config = SyncConfiguration.with(
            user,
            TestHelper.randomPartitionValue(),
            setOf(ParentPk::class, ChildPk::class)
        )
        val realm = Realm.open(config)
        try {
            assertFailsWith<IllegalStateException> { realm.subscriptions }
        } finally {
            realm.close()
            app.close()
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
        assertEquals(SubscriptionSetState.PENDING, subscriptions.state)
    }

    @Test
    fun findByQuery() = runBlocking {
        val query: RealmQuery<ParentPk> = realm.query<ParentPk>()
        val subscriptions = realm.subscriptions
        assertNull(subscriptions.findByQuery(query))
        subscriptions.update { add(query) }
        val sub: Subscription = subscriptions.findByQuery(query)!!
        assertNotNull(sub)
        assertEquals("ParentPk", sub.objectType)
    }

    @Test
    fun findByName() = runBlocking {
        val subscriptions = realm.subscriptions
        assertNull(subscriptions.findByName("foo"))
        subscriptions.update {
            realm.query<ParentPk>().subscribe("foo")
        }
        val sub: Subscription = subscriptions.findByName("foo")!!
        assertNotNull(sub)
        assertEquals("foo", sub.name)
    }

    @Test
    fun state() = runBlocking {
        val subscriptions = realm.subscriptions
        assertEquals(SubscriptionSetState.PENDING, subscriptions.state)
        subscriptions.update {
            realm.query<ParentPk>().subscribe("test1")
        }
        assertEquals(SubscriptionSetState.PENDING, subscriptions.state)
        subscriptions.waitForSynchronization()
        assertEquals(SubscriptionSetState.COMPLETE, subscriptions.state)
        subscriptions.update {
            realm.query<ParentPk>().limit(1).subscribe("test2")
        }
        subscriptions.waitForSynchronization()
        assertEquals(SubscriptionSetState.ERROR, subscriptions.state)
    }

    @Test
    fun size() = runBlocking {
        val subscriptions = realm.subscriptions
        assertEquals(0, subscriptions.size)
        subscriptions.update {
            realm.query<ParentPk>().subscribe()
        }
        assertEquals(1, subscriptions.size)
        subscriptions.update {
            removeAll()
        }
        assertEquals(0, subscriptions.size)
    }

    @Test
    fun errorMessage() = runBlocking {
        val subscriptions = realm.subscriptions
        assertNull(subscriptions.errorMessage)
        subscriptions.update {
            realm.query<ParentPk>().limit(1).subscribe()
        }
        subscriptions.waitForSynchronization()
        assertTrue(subscriptions.errorMessage!!.contains("Client provided query with bad syntax"))
        subscriptions.update {
            removeAll() // Removing all queries seems to provoke an error on the server, so create new valid query.
            realm.query<ParentPk>().subscribe()
        }
        subscriptions.waitForSynchronization()
        assertNull(subscriptions.errorMessage)
    }

    @Test
    fun iterator_zeroSize() {
        val subscriptions = realm.subscriptions
        val iterator: Iterator<Subscription> = subscriptions.iterator()
        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.next() }
    }

    @Test
    fun iterator() = runBlocking {
        val subscriptions = realm.subscriptions
        subscriptions.update {
            realm.query<ParentPk>().subscribe("sub1")
        }
        val iterator: Iterator<Subscription> = subscriptions.iterator()
        assertTrue(iterator.hasNext())
        assertEquals("sub1", iterator.next().name)
        assertFalse(iterator.hasNext())
        assertFailsWith<NoSuchElementException> { iterator.next() }
        Unit
    }

    @Test
    fun subscriptions_accessAfterRealmClosed() = runBlocking {
        val subscriptions = realm.subscriptions
        realm.close()
        // FIXME: Results in native crash. Must check if Realm is closed.
        subscriptions.update {
            realm.query<ParentPk>().subscribe()
        }
        subscriptions.waitForSynchronization()
        assertEquals(SubscriptionSetState.COMPLETE, subscriptions.state)
    }

    @Test
    fun waitForSynchronizationInitialSubscriptions() = runBlocking {
        val subscriptions = realm.subscriptions
        assertTrue(subscriptions.waitForSynchronization())
        assertEquals(SubscriptionSetState.COMPLETE, subscriptions.state)
        assertEquals(0, subscriptions.size)
    }

    @Test
    fun waitForSynchronizationInitialEmptySubscriptionSet() = runBlocking {
        val subscriptions = realm.subscriptions
        subscriptions.update { /* Do nothing */ }
        assertTrue(subscriptions.waitForSynchronization())
        assertEquals(SubscriptionSetState.COMPLETE, subscriptions.state)
        assertEquals(0, subscriptions.size)
    }

    @Test
    fun waitForSynchronizationAfterInsert() = runBlocking {
        val updatedSubs = realm.subscriptions.update {
            realm.query<ParentPk>().subscribe("test")
        }
        assertNotEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
        assertTrue(updatedSubs.waitForSynchronization())
        assertEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
    }

    @Test
    fun waitForSynchronizationError() = runBlocking {
        val updatedSubs = realm.subscriptions.update {
            realm.query<ParentPk>().limit(1).subscribe("test")
        }
        assertFalse(updatedSubs.waitForSynchronization())
        assertEquals(SubscriptionSetState.ERROR, updatedSubs.state)
        assertTrue(updatedSubs.errorMessage!!.contains("Client provided query with bad syntax"))
    }

    @Test
    fun waitForSynchronization_timeOut() = runBlocking {
        val updatedSubs = realm.subscriptions.update {
            realm.query<ParentPk>().subscribe()
        }
        assertTrue(updatedSubs.waitForSynchronization(1.minutes))
    }

    @Test
    fun waitForSynchronization_timeOutThrows() = runBlocking {
        val updatedSubs = realm.subscriptions.update {
            realm.query<ParentPk>().subscribe()
        }
        assertTrue(updatedSubs.waitForSynchronization(1.nanoseconds))
    }
}
