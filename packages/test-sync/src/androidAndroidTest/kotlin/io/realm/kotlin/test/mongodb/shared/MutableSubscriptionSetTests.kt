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

package io.realm.kotlin.test.mongodb.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.entities.sync.flx.FlexChildObject
import io.realm.kotlin.entities.sync.flx.FlexEmbeddedObject
import io.realm.kotlin.entities.sync.flx.FlexParentObject
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.ext.subscribe
import io.realm.kotlin.mongodb.subscriptions
import io.realm.kotlin.mongodb.sync.Subscription
import io.realm.kotlin.mongodb.sync.SubscriptionSet
import io.realm.kotlin.mongodb.sync.SubscriptionSetState
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.WaitForSync
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.toRealmInstant
import io.realm.kotlin.test.util.use
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Class wrapping tests for modifying a subscription set.
 */
class MutableSubscriptionSetTests {

    private lateinit var app: TestApp
    private lateinit var realm: Realm
    private lateinit var config: SyncConfiguration

    @BeforeTest
    fun setup() {
        app = TestApp(appName = TEST_APP_FLEX)
        val (email, password) = TestHelper.randomEmail() to "password1234"
        val user = runBlocking {
            app.createUserAndLogIn(email, password)
        }
        config = SyncConfiguration.Builder(
            user,
            schema = setOf(FlexParentObject::class, FlexChildObject::class, FlexEmbeddedObject::class)
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

    @Test
    fun initialSubscriptions() = runBlocking {
        realm.subscriptions.update {
            assertEquals(0, size)
            assertEquals(SubscriptionSetState.UNCOMMITTED, state)
        }
        Unit
    }

    @Test
    fun addNamedSubscription() = runBlocking {
        val now = Clock.System.now().toRealmInstant()
        // On macOS, Core and Kotlin apparently doesn't agree on the exact timing, sometimes
        // resulting in Core setting an earlier timestamp than "now". To prevent flaky tests
        // we thus wait a little before letting Core write the timestamp.
        // See https://github.com/realm/realm-kotlin/issues/846
        delay(1000)
        val updatedSubs = realm.subscriptions.update { realmRef: Realm ->
            add(realmRef.query<FlexParentObject>(), "test")
        }
        assertEquals(1, updatedSubs.size)
        assertEquals(SubscriptionSetState.PENDING, updatedSubs.state)
        val sub: Subscription = updatedSubs.first()
        assertEquals("test", sub.name)
        assertEquals("TRUEPREDICATE ", sub.queryDescription)
        assertEquals("FlexParentObject", sub.objectType)
        assertTrue(now <= sub.createdAt, "Was: $now <= ${sub.createdAt}")
        assertEquals(sub.updatedAt, sub.createdAt)
    }

    @Test
    fun addAnonymousSubscription() = runBlocking {
        val now = Clock.System.now().toRealmInstant()
        // on macOS Core and Kotlin apparently doesn't agree on the exact timing, sometimes
        // resulting in Core setting an earlier timestamp than "now". To prevent flaky tests
        // we thus wait a little before letting Core write the timestamp.
        delay(1000)
        val updatedSubs = realm.subscriptions.update { realmRef: Realm ->
            add(realmRef.query<FlexParentObject>())
        }
        assertEquals(1, updatedSubs.size)
        assertEquals(SubscriptionSetState.PENDING, updatedSubs.state)
        val sub: Subscription = updatedSubs.first()
        assertNull(sub.name)
        assertEquals("TRUEPREDICATE ", sub.queryDescription)
        assertEquals("FlexParentObject", sub.objectType)
        assertTrue(now <= sub.createdAt, "Was: $now <= ${sub.createdAt}")
        assertEquals(sub.updatedAt, sub.createdAt)
    }

    @Test
    fun add_multiple_anonymous() = runBlocking {
        realm.subscriptions.update { realmRef: Realm ->
            assertEquals(0, size)
            add(realmRef.query<FlexParentObject>())
            add(realmRef.query<FlexParentObject>("section = $0", 10L))
            add(realmRef.query<FlexParentObject>("section = $0 ", 5L))
            add(realmRef.query<FlexParentObject>("section = $0", 1L))
            assertEquals(4, size)
        }
        Unit
    }

    @Test
    fun addExistingAnonymous_returnsAlreadyPersisted() = runBlocking {
        realm.subscriptions.update { realmRef: Realm ->
            val sub1 = add(realmRef.query<FlexParentObject>())
            val sub2 = add(realmRef.query<FlexParentObject>())
            assertEquals(sub1, sub2)
        }
        Unit
    }

    @Test
    fun addExistingNamed_returnsAlreadyPersisted() = runBlocking {
        realm.subscriptions.update { realmRef: Realm ->
            val sub1 = add(realmRef.query<FlexParentObject>(), "sub1")
            val sub2 = add(realmRef.query<FlexParentObject>(), "sub1")
            assertEquals(sub1, sub2)
        }
        Unit
    }

    @Test
    fun add_conflictingNamesThrows() = runBlocking {
        realm.subscriptions.update { realmRef: Realm ->
            add(realmRef.query<FlexParentObject>(), "sub1")
            assertFailsWith<IllegalStateException> {
                add(realmRef.query<FlexParentObject>("name = $0", "foo"), "sub1")
            }
        }
        Unit
    }

    @Test
    fun update() = runBlocking {
        val subs = realm.subscriptions
        subs.update { realmRef: Realm ->
            realmRef.query<FlexParentObject>().subscribe("sub1")
        }
        val createdAt = subs.first().createdAt
        subs.update { realmRef: Realm ->
            realmRef.query<FlexParentObject>("name = $0", "red").subscribe("sub1", updateExisting = true)
        }
        val sub = subs.first()
        assertEquals("sub1", sub.name)
        assertEquals("FlexParentObject", sub.objectType)
        assertEquals("name == \"red\" ", sub.queryDescription)
        assertTrue(sub.createdAt < sub.updatedAt)
        assertEquals(createdAt, sub.createdAt)
    }

    @Test
    fun removeNamed() = runBlocking {
        var updatedSubs = realm.subscriptions.update { realmRef: Realm ->
            realmRef.query<FlexParentObject>().subscribe("test")
        }
        assertEquals(1, updatedSubs.size)
        updatedSubs = updatedSubs.update {
            assertTrue(remove("test"))
            assertEquals(0, size)
        }
        assertEquals(0, updatedSubs.size)
    }

    @Test
    fun removeNamed_fails() = runBlocking {
        realm.subscriptions.update {
            assertFalse(remove("dont-exists"))
        }
        Unit
    }

    @Test
    fun removeSubscription_success() = runBlocking {
        var updatedSubs = realm.subscriptions.update { realmRef: Realm ->
            realmRef.query<FlexParentObject>().subscribe("test")
        }
        assertEquals(1, updatedSubs.size)
        updatedSubs = updatedSubs.update {
            assertTrue(remove(first()))
            assertEquals(0, size)
        }
        assertEquals(0, updatedSubs.size)
    }

    @Test
    fun removeSubscription_fails() = runBlocking {
        realm.subscriptions.update { realmRef: Realm ->
            val managedSub = add(realmRef.query<FlexParentObject>())
            assertTrue(remove(managedSub))
            assertFalse(remove(managedSub))
        }
        Unit
    }

    @Ignore
    @Test
    fun removeAllStringTyped() = runBlocking {
        var updatedSubs: SubscriptionSet<Realm> = realm.subscriptions.update { realmRef: Realm ->
            add(realmRef.query<FlexParentObject>())
            realmRef.query<FlexParentObject>().subscribe(name = "foo", updateExisting = true)
            removeAll("FlexParentObject")
        }
        assertEquals(1, updatedSubs.size)
        updatedSubs = updatedSubs.update {
            assertTrue(removeAll("FlexParentObject"))
            assertEquals(0, size)
        }
        assertEquals(0, updatedSubs.size)
    }

    @Test
    fun removeAllStringTyped_fails() = runBlocking {
        // Not part of schema
        realm.subscriptions.update {
            assertFailsWith<IllegalArgumentException> {
                removeAll("DontExists")
            }
        }

        // part of schema
        realm.subscriptions.update {
            assertFalse(removeAll("FlexParentObject"))
        }
        Unit
    }

    @Test
    fun removeAllClassTyped() = runBlocking {
        var updatedSubs = realm.subscriptions.update { realmRef: Realm ->
            add(realmRef.query<FlexParentObject>())
        }
        assertEquals(1, updatedSubs.size)
        updatedSubs = updatedSubs.update {
            assertTrue(removeAll(FlexParentObject::class))
            assertEquals(0, size)
        }
        assertEquals(0, updatedSubs.size)
    }

    @Test
    fun removeAllClassTyped_fails() = runBlocking {
        // Not part of schema
        realm.subscriptions.update {
            assertFailsWith<IllegalArgumentException> {
                removeAll(io.realm.kotlin.entities.sync.ParentPk::class)
            }
        }

        // part of schema
        realm.subscriptions.update {
            assertFalse(removeAll(FlexParentObject::class))
        }
        Unit
    }

    @Test
    fun removeAll() = runBlocking {
        var updatedSubs = realm.subscriptions.update { realmRef: Realm ->
            realmRef.query<FlexParentObject>().subscribe("test")
            realmRef.query<FlexParentObject>().subscribe("test2")
        }
        assertEquals(2, updatedSubs.size)
        updatedSubs = updatedSubs.update {
            assertTrue(removeAll())
            assertEquals(0, size)
        }
        assertEquals(0, updatedSubs.size)
    }

    @Test
    fun removeAll_fails() = runBlocking {
        realm.subscriptions.update {
            assertFalse(removeAll())
        }
        Unit
    }

    // Ensure that all resources are correctly torn down when an error happens inside a
    // MutableSubscriptionSet
    @Ignore // Require support for deleting synchronized Realms
    @Test
    @Suppress("TooGenericExceptionThrown")
    fun deleteFile_exceptionInsideMutableRealm() = runBlocking {
        try {
            realm.subscriptions.update {
                throw RuntimeException("Boom!")
            }
        } catch (ex: RuntimeException) {
            if (ex.message == "Boom!") {
                realm.close()
                Realm.deleteRealm(config)
            }
        }
        Unit
    }

    @Test
    fun iterator_duringWrite() = runBlocking {
        realm.subscriptions.update {
            assertFalse(iterator().hasNext())
            add(realm.query<FlexParentObject>(), name = "sub")
            var iterator = iterator()
            assertTrue(iterator.hasNext())
            val sub = iterator.next()
            assertEquals("sub", sub.name)
            assertFalse(iterator.hasNext())
            removeAll()
            iterator = iterator()
            assertFalse(iterator.hasNext())
        }
        Unit
    }

    @Test
    fun subscribe_realmQuery_waitFirstTime() = runBlocking<Unit> {
        // Anonymous query
        realm.query<FlexParentObject>().subscribe() // Default value is WaitForSync.FIRST_TIME
        var updatedSubs = realm.subscriptions
        assertEquals(1, updatedSubs.size)
        assertEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
        var sub: Subscription = updatedSubs.first()
        assertNull(sub.name)
        assertEquals("TRUEPREDICATE ", sub.queryDescription)
        assertEquals("FlexParentObject", sub.objectType)

        // Named query
        realm.query<FlexParentObject>().subscribe("my-name") // Default value is WaitForSync.FIRST_TIME
        updatedSubs = realm.subscriptions
        assertEquals(2, updatedSubs.size)
        assertEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
        sub = updatedSubs.last()
        assertEquals("my-name", sub.name)
        assertEquals("TRUEPREDICATE ", sub.queryDescription)
        assertEquals("FlexParentObject", sub.objectType)
    }

    @Test
    fun subscribe_realmQuery_waitNever() = runBlocking {
        // Anonymous query
        realm.query<FlexParentObject>().subscribe(mode = WaitForSync.NEVER)
        var updatedSubs = realm.subscriptions
        assertEquals(1, updatedSubs.size)
        // Updating the subscription will happen in the background, but
        // hopefully hasn't reached COMPLETE yet.
        assertNotEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)

        // Named query
        realm.query<FlexParentObject>().subscribe(name = "my-name", mode = WaitForSync.NEVER)
        updatedSubs = realm.subscriptions
        assertEquals(2, updatedSubs.size)
        // Updating the subscription will happen in the background, but
        // hopefully hasn't reached COMPLETE yet.
        assertNotEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
    }

    @Test
    fun subscribe_realmQuery_waitAlways() = runBlocking {
        val sectionId = Random.nextInt()
        val results1 = realm.query<FlexParentObject>("section = $0", sectionId).subscribe() // Default value is WaitForSync.FIRST_TIME
        assertEquals(0, results1.size)
        uploadServerData(sectionId, 5)
        // Since the subscription is already present, we cannot control if the data is downloaded
        // before creating the next subscription. Instead we pause the syncSession and verify
        // that WaitForSync.ALWAYS timeout during network failures and resuming the session should
        // then work
        realm.syncSession.pause()
        assertFailsWith<TimeoutCancellationException> {
            realm.query<FlexParentObject>("section = $0", sectionId).subscribe(timeout = 3.seconds, mode = WaitForSync.ALWAYS)
        }
        realm.syncSession.resume()
        val results2 = realm.query<FlexParentObject>("section = $0", sectionId).subscribe(mode = WaitForSync.ALWAYS)
        assertEquals(5, results2.size)
    }

    @Test
    fun subscribe_realmQuery_timeOut_fails() = runBlocking<Unit> {
        assertFailsWith<TimeoutCancellationException> {
            realm.query<FlexParentObject>().subscribe(timeout = 1.nanoseconds)
        }
    }

    @Test
    fun subscribe_realmQuery_throwsInsideWrite() = realm.writeBlocking<Unit> {
        // `subscribe()` being a suspend function make in hard to call
        // subscribe inside a write, but we should still detect it.
        runBlocking {
            assertFailsWith<IllegalStateException> {
                query<FlexParentObject>().subscribe()
            }
            assertFailsWith<IllegalStateException> {
                query<FlexParentObject>().subscribe(name = "my-name")
            }
        }
    }

    @Test
    fun subscribe_realmResults_waitFirstTime() = runBlocking<Unit> {
        // Unnamed
        realm.query<FlexParentObject>().find().subscribe() // Default value is WaitForSync.FIRST_TIME
        var updatedSubs = realm.subscriptions
        assertEquals(1, updatedSubs.size)
        assertEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
        var sub: Subscription = updatedSubs.first()
        assertNull(sub.name)
        assertEquals("TRUEPREDICATE ", sub.queryDescription)
        assertEquals("FlexParentObject", sub.objectType)

        // Named
        realm.query<FlexParentObject>().find().subscribe("my-name") // Default value is WaitForSync.FIRST_TIME
        updatedSubs = realm.subscriptions
        assertEquals(2, updatedSubs.size)
        assertEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
        sub = updatedSubs.last()
        assertEquals("my-name", sub.name)
        assertEquals("TRUEPREDICATE ", sub.queryDescription)
        assertEquals("FlexParentObject", sub.objectType)
    }

    @Test
    fun subscribe_realmResults_waitOnNever() = runBlocking {
        // Un-named
        realm.query<FlexParentObject>().find().subscribe(mode = WaitForSync.NEVER)
        var updatedSubs = realm.subscriptions
        assertEquals(1, updatedSubs.size)
        // Updating the subscription will happen in the background, but
        // hopefully hasn't reached COMPLETE yet.
        assertNotEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)

        // Named
        realm.query<FlexParentObject>().find().subscribe(name = "my-name", mode = WaitForSync.NEVER)
        updatedSubs = realm.subscriptions
        assertEquals(2, updatedSubs.size)
        // Updating the subscription will happen in the background, but
        // hopefully hasn't reached COMPLETE yet.
        assertNotEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
    }

    @Test
    fun subscribe_realmResults_waitAlways() = runBlocking<Unit> {
        val sectionId = Random.nextInt()
        val results1 = realm.query<FlexParentObject>("section = $0", sectionId).find().subscribe() // Default value is WaitForSync.FIRST_TIME
        assertEquals(0, results1.size)
        uploadServerData(sectionId, 5)
        // Since the subscription is already present, we cannot control if the data is downloaded
        // before creating the next subscription. Instead we pause the syncSession and verify
        // that WaitForSync.ALWAYS timeout during network failures and resuming the session should
        // then work
        realm.syncSession.pause()
        assertFailsWith<TimeoutCancellationException> {
            realm.query<FlexParentObject>("section = $0", sectionId).find().subscribe(timeout = 3.seconds, mode = WaitForSync.ALWAYS)
        }
        realm.syncSession.resume()
        val results2 = realm.query<FlexParentObject>("section = $0", sectionId).find().subscribe(mode = WaitForSync.ALWAYS)
        assertEquals(5, results2.size)
    }

    @Test
    fun subscribe_realmResults_subquery() = runBlocking<Unit> {
        val topQueryResult: RealmResults<FlexParentObject> = realm.query<FlexParentObject>("section = 42").find()
        val subQueryResult: RealmResults<FlexParentObject> = topQueryResult.query("name == $0", "Jane").find()
        subQueryResult.subscribe()
        val subs = realm.subscriptions
        assertEquals(1, subs.size)
        assertEquals("section == 42 and name == \"Jane\" ", subs.first().queryDescription)
        subQueryResult.subscribe("my-name")
        assertEquals(2, subs.size)
        val lastSub = subs.last()
        assertEquals("my-name", lastSub.name)
        assertEquals("section == 42 and name == \"Jane\" ", lastSub.queryDescription)
    }

    @Test
    fun subscribe_realmResults_timeOut_fails() = runBlocking<Unit> {
        assertFailsWith<TimeoutCancellationException> {
            realm.query<FlexParentObject>().find().subscribe(timeout = 1.nanoseconds)
        }
    }

    @Test
    fun subscribe_realmResults_throwsInsideWrite() = runBlocking<Unit> {
        realm.writeBlocking {
            // `subscribe()` being a suspend function make in hard to call
            // subscribe inside a write, but we should still detect it.
            runBlocking {
                assertFailsWith<IllegalStateException> {
                    query<FlexParentObject>().find().subscribe()
                }
                assertFailsWith<IllegalStateException> {
                    query<FlexParentObject>().find().subscribe(name = "my-name")
                }
            }
        }
    }

    @Test
    fun anonymousSubscriptionsOverlap() = runBlocking<Unit> {
        realm.query<FlexParentObject>("section == 42").subscribe()
        realm.query<FlexParentObject>("section == 42").find().subscribe()

        assertEquals(1, realm.subscriptions.size)
        val sub = realm.subscriptions.first()
        assertNull(sub.name)
        assertEquals("section == 42 ", sub.queryDescription)
        assertEquals("FlexParentObject", sub.objectType)
    }

    private suspend fun uploadServerData(sectionId: Int, noOfObjects: Int) {
        val user = app.createUserAndLogin()
        val config = SyncConfiguration.Builder(user, setOf(FlexParentObject::class, FlexChildObject::class, FlexEmbeddedObject::class))
            .initialSubscriptions {
                it.query<FlexParentObject>().subscribe()
            }
            .waitForInitialRemoteData()
            .build()

        Realm.open(config).use { realm ->
            realm.writeBlocking {
                repeat(noOfObjects) {
                    copyToRealm(FlexParentObject(sectionId))
                }
            }
            realm.syncSession.uploadAllLocalChanges()
        }
    }
}
