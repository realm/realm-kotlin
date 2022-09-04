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
import io.realm.kotlin.entities.sync.ChildPk
import io.realm.kotlin.entities.sync.ParentPk
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.subscriptions
import io.realm.kotlin.mongodb.sync.Subscription
import io.realm.kotlin.mongodb.sync.SubscriptionSet
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.asQuery
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper.randomEmail
import io.realm.kotlin.test.util.toRealmInstant
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Class wrapping tests for Subscriptions
 * This class only covers the [Subscription] class. For creating, deleting or modifying
 * subscriptions, see [MutableSubscriptionSetTests].
 */
class SubscriptionTests {

    private lateinit var app: TestApp
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {

        app = TestApp(TEST_APP_FLEX)

        val (email, password) = randomEmail() to "password1234"
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

    @Test
    fun managedProperties() = runBlocking {
        val now: RealmInstant = Clock.System.now().toRealmInstant()
        // On macOS, Core and Kotlin apparently doesn't agree on the exact timing, sometimes
        // resulting in Core setting an earlier timestamp than "now". To prevent flaky tests
        // we thus wait a little before letting Core write the timestamp.
        // See https://github.com/realm/realm-kotlin/issues/846
        delay(1000)
        val namedSub: Subscription = realm.subscriptions.update { realm ->
            realm.query<ParentPk>().subscribe("mySub")
        }.first()

        assertEquals("mySub", namedSub.name)
        assertEquals("ParentPk", namedSub.objectType)
        assertEquals("TRUEPREDICATE ", namedSub.queryDescription)
        assertTrue(now <= namedSub.updatedAt, "$now <= ${namedSub.updatedAt}")
        assertTrue(now <= namedSub.createdAt, "$now <= ${namedSub.createdAt}")

        val anonSub = realm.subscriptions.update { realm ->
            removeAll()
            add(realm.query<ParentPk>())
        }.first()
        assertNull(anonSub.name)
        assertEquals("ParentPk", anonSub.objectType)
        assertEquals("TRUEPREDICATE ", anonSub.queryDescription)
        assertTrue(now <= namedSub.updatedAt, "$now <= ${namedSub.updatedAt}")
        assertTrue(now <= namedSub.createdAt, "$now <= ${namedSub.createdAt}")
    }

    @Test
    fun properties_areSnaphotValues() = runBlocking {
        val snapshotSub: Subscription = realm.subscriptions.update { realm ->
            add(realm.query<ParentPk>(), name = "mySub")
        }.first()

        // Delete all underlying subscriptions
        realm.subscriptions.update {
            removeAll()
        }

        // Check that properties still work even if subscription is deleted elsewhere
        assertEquals("mySub", snapshotSub.name)
        assertEquals("ParentPk", snapshotSub.objectType)
        assertEquals("TRUEPREDICATE ", snapshotSub.queryDescription)
        assertNotNull(snapshotSub.updatedAt)
        assertNotNull(snapshotSub.createdAt)
        Unit
    }

    @Test
    fun asQuery() = runBlocking {
        val sub: Subscription = realm.subscriptions.update { realm ->
            add(realm.query<ParentPk>("name = $0", "my-name"))
        }.first()

        realm.write {
            copyToRealm(
                ParentPk().apply {
                    name = "my-name"
                }
            )
        }
        val query: RealmQuery<ParentPk> = sub.asQuery<ParentPk>()
        assertEquals("name == \"my-name\"", query.description())
        assertEquals(1, query.count().find())
    }

    @Test
    fun asQuery_throwsOnWrongType() = runBlocking {
        val sub: Subscription = realm.subscriptions.update { realm ->
            add(realm.query<ParentPk>("name = $0", "my-name"))
        }.first()

        assertFailsWith<IllegalArgumentException> {
            sub.asQuery<ChildPk>()
        }
        Unit
    }

    @Test
    fun equals() = runBlocking {
        val subs: SubscriptionSet<Realm> = realm.subscriptions.update { realm ->
            add(realm.query<ParentPk>(), name = "mySub")
        }
        val sub1: Subscription = subs.first()
        val sub2: Subscription = subs.first()
        assertEquals(sub1, sub2)
    }

    @Test
    fun equals_falseForDifferentVersions() = runBlocking {
        var sub1 = realm.subscriptions.update { realm ->
            add(realm.query<ParentPk>(), name = "mySub")
        }.first()
        val sub2 = realm.subscriptions.update { realm ->
            /* Do nothing */
        }.first()
        assertNotEquals(sub1, sub2)
    }
}
