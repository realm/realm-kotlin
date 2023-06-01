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
import io.realm.kotlin.mongodb.sync.SubscriptionSetState
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.WaitForSync
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.use
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Class for testing the various extension methods we have for bridging the gap between Subscriptions
 * and RealmQuery/RealmResults.
 */
class SubscriptionExtensionsTests {

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

    @Test
    fun realmQuery_subscribe_anonymous() = runBlocking {
        val subs = realm.subscriptions
        assertEquals(0, subs.size)
        val results: RealmResults<FlexParentObject> = realm.query<FlexParentObject>().subscribe()
        assertEquals(SubscriptionSetState.COMPLETE, subs.state)
        assertEquals(1, subs.size)
        val sub: Subscription = subs.first()
        assertNull(sub.name)
        assertEquals("TRUEPREDICATE ", sub.queryDescription)
        assertEquals(FlexParentObject::class.simpleName, sub.objectType)
    }

    // Check that subscribing twice to a query will result in the same subscription
    @Test
    fun realmQuery_subscribe_anonymousTwice() = runBlocking {
        val subs = realm.subscriptions
        assertEquals(0, subs.size)
        realm.query<FlexParentObject>().subscribe()
        realm.query<FlexParentObject>().subscribe()
        assertEquals(SubscriptionSetState.COMPLETE, subs.state)
        assertEquals(1, subs.size)
        val sub: Subscription = subs.first()
        assertNull(sub.name)
        assertEquals("TRUEPREDICATE ", sub.queryDescription)
        assertEquals(FlexParentObject::class.simpleName, sub.objectType)
    }

    // Check that anonymous RealmQuery and RealmResults .subscribe calls result in the same sub.
    @Test
    fun anonymousSubscriptionsOverlap() = runBlocking {
        val subs = realm.subscriptions
        assertEquals(0, subs.size)
        realm.query<FlexParentObject>().subscribe()
        realm.query<FlexParentObject>().find().subscribe()
        assertEquals(SubscriptionSetState.COMPLETE, subs.state)
        assertEquals(1, subs.size)
        val sub: Subscription = subs.first()
        assertNull(sub.name)
        assertEquals("TRUEPREDICATE ", sub.queryDescription)
        assertEquals(FlexParentObject::class.simpleName, sub.objectType)
    }

    // Verify that the realm query doesn't run against a frozen version previous to the Realm
    // being updated from `waitForSynchronization`.
    @Test
    fun realmQuery_subscribe_queryResultIsLatestVersion() = runBlocking {
        // Write data to a server Realm
        val section = Random.nextInt()
        val (email, password) = TestHelper.randomEmail() to "password1234"
        val user1 = app.createUserAndLogIn(email, password)
        val config = SyncConfiguration.Builder(
            user1,
            schema = setOf(FlexParentObject::class, FlexChildObject::class, FlexEmbeddedObject::class)
        ).initialSubscriptions { realm: Realm ->
            realm.query<FlexParentObject>("section = $0", section).subscribe()
        }.build()

        Realm.open(config).use { realmFromAnotherDevice ->
            realmFromAnotherDevice.writeBlocking {
                copyToRealm(FlexParentObject(section))
            }
            realmFromAnotherDevice.syncSession.uploadAllLocalChanges(30.seconds)
        }

        // Data still hasn't reached this device
        assertEquals(0, realm.query<FlexParentObject>().count().find())
        // Check that subscribing to a query, will run the query on the data downloaded from
        // the server and not just local data, due to WaitForSync.FIRST_TIME being the default.
        val result = realm.query<FlexParentObject>("section = $0", section).subscribe()
        assertEquals(1, result.size)
        assertEquals(section, result.first().section)
    }

    @Test
    fun realmQuery_subscribe_waitFirstTime() = runBlocking<Unit> {
        // Unnamed
        realm.query<FlexParentObject>().subscribe() // Default value is WaitForSync.FIRST_TIME
        var updatedSubs = realm.subscriptions
        assertEquals(1, updatedSubs.size)
        assertEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
        var sub: Subscription = updatedSubs.first()
        assertNull(sub.name)
        assertEquals("TRUEPREDICATE ", sub.queryDescription)
        assertEquals("FlexParentObject", sub.objectType)

        // Named
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
    fun realmQuery_subscribe_waitNever() = runBlocking {
        // Un-named
        realm.query<FlexParentObject>().subscribe(mode = WaitForSync.NEVER)
        var updatedSubs = realm.subscriptions
        assertEquals(1, updatedSubs.size)
        // Updating the subscription will happen in the background, but
        // hopefully hasn't reached COMPLETE yet.
        assertNotEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)

        // Named
        realm.query<FlexParentObject>().subscribe(name = "my-name", mode = WaitForSync.NEVER)
        updatedSubs = realm.subscriptions
        assertEquals(2, updatedSubs.size)
        // Updating the subscription will happen in the background, but
        // hopefully hasn't reached COMPLETE yet.
        assertNotEquals(SubscriptionSetState.COMPLETE, updatedSubs.state)
    }

    @Test
    fun realmQuery_subscribe_waitAlways() = runBlocking {
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
    fun realmQuery_subscribe_timeOut_fails() = runBlocking<Unit> {
        assertFailsWith<TimeoutCancellationException> {
            realm.query<FlexParentObject>().subscribe(timeout = 1.nanoseconds)
        }
    }

    @Test
    fun realmQuery_subscribe_throwsInsideWrite() {
        realm.writeBlocking {
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
    }

    @Test
    fun realmResults_subscribe_waitFirstTime() = runBlocking {
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
    fun realmResults_subscribe_waitOnNever() = runBlocking {
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
    fun realmResults_subscribe_waitAlways() = runBlocking {
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
    fun realmResults_subscribe_subquery() = runBlocking {
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
    fun realmResults_subscribe_timeOut_fails() = runBlocking<Unit> {
        assertFailsWith<TimeoutCancellationException> {
            realm.query<FlexParentObject>().find().subscribe(timeout = 1.nanoseconds)
        }
    }

    @Test
    fun realmResults_subscribe_throwsInsideWrite() = runBlocking<Unit> {
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
