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
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.use
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

        Realm.open(config).use { realm ->
            realm.writeBlocking {
                copyToRealm(FlexParentObject(section))
            }
            realm.syncSession.uploadAllLocalChanges(30.seconds)
        }

        // Check that subscribing to a query, will run the query on the data downloaded from
        // the server and not just local data.
        assertEquals(0, realm.query<FlexParentObject>().count().find())
        val result = realm.query<FlexParentObject>("section = $0", section).subscribe()
        assertEquals(1, result.size)
        assertEquals(section, result.first().section)
    }

    @Test
    fun realmQuery_subscribe_anonymous_synchronizeFirstTime() {
    }

    @Test
    fun realmQuery_subscribeToAnonymous_synchronizeAlways() {
    }

    @Test
    fun realmQuery_subscribeToAnonymous_synchronizeNever() {
    }

    @Test
    fun realmResults_unsubscribe_throwsInsideWrite() {
    }
}
