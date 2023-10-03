package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.Realm
import io.realm.kotlin.annotations.ExperimentalGeoSpatialApi
import io.realm.kotlin.entities.Location
import io.realm.kotlin.entities.sync.SyncRestaurant
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.syncSession
import io.realm.kotlin.test.mongodb.TEST_APP_FLEX
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.use
import io.realm.kotlin.types.geo.Distance
import io.realm.kotlin.types.geo.GeoBox
import io.realm.kotlin.types.geo.GeoCircle
import io.realm.kotlin.types.geo.GeoPoint
import io.realm.kotlin.types.geo.GeoPolygon
import org.mongodb.kbson.ObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

private val GEO_SCHEMA = setOf(SyncRestaurant::class, Location::class)

@OptIn(ExperimentalGeoSpatialApi::class)
class GeoSpatialTests {
    private lateinit var app: TestApp

    @BeforeTest
    fun setup() {
        app = TestApp(this::class.simpleName, appName = TEST_APP_FLEX)
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    private suspend fun createRandomUser(): User =
        app.createUserAndLogIn(
            email = TestHelper.randomEmail(),
            password = "password1234"
        )

    @Test
    fun write() {
        runBlocking {
            val user = createRandomUser()

            val config =
                SyncConfiguration.Builder(
                    user = user,
                    schema = GEO_SCHEMA
                ).initialSubscriptions {
                    add(it.query<SyncRestaurant>())
                }.build()

            Realm.open(config).use { realm ->
                realm.write {
                    copyToRealm(SyncRestaurant())
                }
            }
        }
    }

    @Test
    fun write_outsideSubscriptionsFail() {
        runBlocking {
            val user = createRandomUser()

            val config =
                SyncConfiguration.Builder(
                    user = user,
                    schema = GEO_SCHEMA
                ).build()

            Realm.open(config).use { realm ->
                realm.write {
                    assertFailsWith<IllegalArgumentException>(
                        message = "[RLM_ERR_NO_SUBSCRIPTION_FOR_WRITE]: Cannot write to class SyncRestaurant when no flexible sync subscription has been created."
                    ) {
                        copyToRealm(SyncRestaurant())
                    }
                }
            }
        }
    }

    @Test
    fun geoBox_tests() {
        runBlocking {
            generic_geo_test(
                bounds = GeoBox.create(
                    top = 1.0, left = 1.0,
                    bottom = -1.0, right = -1.0,
                ),
                validLocation = Location(0.0, 0.0),
                invalidLocation = Location(100.0, 100.0),
            )
        }
    }

    @Test
    fun geoCircle_tests() {
        runBlocking {
            generic_geo_test(
                bounds = GeoCircle.create(
                    GeoPoint.create(0.0, 0.0), Distance.fromKilometers(.01)
                ),
                validLocation = Location(0.0, 0.0),
                invalidLocation = Location(101.0, 101.0),
            )
        }
    }

    @Test
    fun geoPolygon_tests() {
        runBlocking {
            generic_geo_test(
                bounds = GeoPolygon.create(
                    outerRing = listOf(
                        GeoPoint.create(-5.0, -5.0),
                        GeoPoint.create(5.0, -5.0),
                        GeoPoint.create(5.0, 5.0),
                        GeoPoint.create(-5.0, 5.0),
                        GeoPoint.create(-5.0, -5.0)
                    ),
                    holes = arrayOf(
                        listOf(
                            GeoPoint.create(-4.0, -4.0),
                            GeoPoint.create(4.0, -4.0),
                            GeoPoint.create(4.0, 4.0),
                            GeoPoint.create(-4.0, 4.0),
                            GeoPoint.create(-4.0, -4.0)
                        )
                    )
                ),
                validLocation = Location(4.5, 4.5), // Outside the hole and withing the ring
                invalidLocation = Location(0.0, 0.0), // Inside the hole
            )
        }
    }

    private suspend fun generic_geo_test(
        bounds: Any,
        validLocation: Location,
        invalidLocation: Location,
    ) {
        val section = ObjectId()

        // User #1 will try to write some data and assert some failure conditions.
        val user1 = createRandomUser()
        val config =
            SyncConfiguration.Builder(
                user = user1,
                schema = GEO_SCHEMA
            ).initialSubscriptions {
                add(
                    it.query<SyncRestaurant>(
                        "section = $0 AND location GEOWITHIN $1",
                        section,
                        bounds
                    )
                )
            }.waitForInitialRemoteData().build()

        Realm.open(config).use { realm ->
            val restaurant = realm.write {
                // Fail: write outside subscription bounds, compensating write
                copyToRealm(
                    SyncRestaurant().apply {
                        this.section = section
                        this.location = invalidLocation
                    }
                )

                // Ok: Write within subscription bounds
                copyToRealm(
                    SyncRestaurant().apply {
                        this.section = section
                        this.location = validLocation
                    }
                )
            }

            realm.syncSession.uploadAllLocalChanges(timeout = 30.seconds)

            realm.write {
                // Fail: We should not be able to update outside of subscription bounds, compensating write
                findLatest(restaurant)!!.location = invalidLocation
            }

            realm.syncSession.uploadAllLocalChanges(timeout = 30.seconds)
        }

        // Download data on user #2
        val user2 = createRandomUser()
        val config2 =
            SyncConfiguration.Builder(
                user = user2,
                schema = GEO_SCHEMA
            ).initialSubscriptions {
                add(
                    it.query<SyncRestaurant>(
                        "section = $0 AND location GEOWITHIN $1",
                        section,
                        bounds
                    )
                )
            }.waitForInitialRemoteData(
                timeout = 30.seconds
            ).build()

        Realm.open(config2).use { realm ->
            assertEquals(1, realm.query<SyncRestaurant>().count().find())
        }
    }
}
