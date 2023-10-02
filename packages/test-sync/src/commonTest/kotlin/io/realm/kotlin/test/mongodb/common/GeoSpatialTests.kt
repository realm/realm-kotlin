package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.Realm
import io.realm.kotlin.annotations.ExperimentalGeoSpatialApi
import io.realm.kotlin.entities.Location
import io.realm.kotlin.entities.Restaurant
import io.realm.kotlin.entities.sync.SyncRestaurant
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.subscriptions
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
import org.mongodb.kbson.ObjectId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.time.Duration.Companion.seconds

private val GEO_SCHEMA = setOf(SyncRestaurant::class, Location::class)

@OptIn(ExperimentalGeoSpatialApi::class)
class GeoSpatialTests {
    private lateinit var app: TestApp

    @BeforeTest
    fun setup() {
        app = TestApp(this::class.simpleName, appName = TEST_APP_FLEX, logLevel = LogLevel.ALL)
    }

    @AfterTest
    fun tearDown() {
        if (this::app.isInitialized) {
            app.close()
        }
    }

    private fun createRandomUser(): User =
        runBlocking {
            app.createUserAndLogIn(
                email = TestHelper.randomEmail(),
                password = "password1234"
            )
        }

    @Test
    fun write() {
        val user = createRandomUser()

        val config =
            SyncConfiguration.Builder(
                user = user,
                schema = GEO_SCHEMA
            ).initialSubscriptions(rerunOnOpen = true) {
                add(it.query<SyncRestaurant>())
            }.build()

        Realm.open(config).use {
            it.writeBlocking {
                copyToRealm(SyncRestaurant())
            }
        }
    }

    @Test
    fun write_outsideSubscriptionsFail() {
        val user = createRandomUser()

        val config =
            SyncConfiguration.Builder(
                user = user,
                schema = GEO_SCHEMA
            ).initialSubscriptions(rerunOnOpen = true) {
            }.build()

        Realm.open(config).use {
            it.writeBlocking {
                assertFails {
                    copyToRealm(SyncRestaurant())
                }
            }
        }
    }

    @Test
    fun box_subscription() {
        val section = ObjectId()

        val box = GeoBox.create(
            -1.0, -1.0,
            1.0, 1.0,
        )

        val user1 = createRandomUser()
        val config =
            SyncConfiguration.Builder(
                user = user1,
                schema = GEO_SCHEMA
            ).initialSubscriptions(rerunOnOpen = true) {
                add(
                    it.query<SyncRestaurant>("section = $0 AND location GEOWITHIN $box", section)
                )
            }.waitForInitialRemoteData().build()

        Realm.open(config).use { realm ->
            runBlocking {
                realm.subscriptions.waitForSynchronization(timeout = 30.seconds)
            }
            realm.writeBlocking {
                // Fail: write outside subscription bounds
                copyToRealm(
                    SyncRestaurant().apply {
                        this.section = section
                        this.location = Location(100.0, 100.0)
                    }
                )

                // Ok: Write within subscription bounds
                val restaurant = copyToRealm(
                    SyncRestaurant().apply {
                        this.section = section
                    }
                )
                // Fail: We should not be able to update outside of subscription bounds
                // todo this should fail
//                assertFails {
//                    restaurant.location = Location(100.0, 100.0)
//                }
            }
        }
//        val user2 = createRandomUser()
//        val config2 =
//            SyncConfiguration.Builder(
//                user = user2,
//                schema = GEO_SCHEMA
//            ).initialSubscriptions(rerunOnOpen = true) {
//                add(
//                    it.query<SyncRestaurant>("section = $0 AND location GEOWITHIN $1", section, box)
//                )
//            }.waitForInitialRemoteData(
//                timeout = 30.seconds
//            ).build()
//
//        Realm.open(config2).use {
//            assertEquals(1, it.query<Restaurant>().count().find())
//        }
    }


//    @Test
//    fun circle_subscription() {
//        val circle = GeoCircle.create(
//            GeoPoint.create(0.0, 0.0), Distance.fromKilometers(100.0)
//        )
//
//        val config =
//            SyncConfiguration.Builder(
//                app.currentUser!!,
//                GEO_SCHEMA
//            ).initialSubscriptions(rerunOnOpen = true) {
//                add(it.query<SyncRestaurant>("location GEOWITHIN $circle"))
//            }.build()
//
//        Realm.open(config).use {
//            it.writeBlocking {
//                copyToRealm(SyncRestaurant())
//            }
//        }
//    }


//    @Test
//    fun geoPoint() {
//        val validPoints = mapOf(
//            0.0 to 0.0,
//
//            // Latitude
//            Double.MIN_VALUE to 0.0,
//            -90.0 to 0.0,
//            -89.999 to 0.0,
//            90.0 to 0.0,
//            89.999 to 0.0,
//
//            // Longitude
//            Double.MIN_VALUE to 0.0,
//            0.0 to -180.0,
//            0.0 to -179.999,
//            0.0 to 180.0,
//            0.0 to -179.999
//        )
//        validPoints.forEach {
//            val p = GeoPoint.create(latitude = it.key, longitude = it.value)
//            assertEquals(it.key, p.latitude)
//            assertEquals(it.value, p.longitude)
//        }
//    }
//
//    @Test
//    fun geoPoint_invalidArgsThrows() {
//        val invalidPoints = mapOf(
//            // Latitude
//            -90.1 to 0.0,
//            Double.NEGATIVE_INFINITY to 0.0,
//            90.1 to 0.0,
//            Double.POSITIVE_INFINITY to 0.0,
//
//            // Longitude
//            0.0 to -180.1,
//            0.0 to Double.NEGATIVE_INFINITY,
//            0.0 to 180.1,
//            0.0 to Double.POSITIVE_INFINITY
//        )
//        invalidPoints.forEach {
//            try {
//                GeoPoint.create(latitude = it.key, longitude = it.value)
//                fail("${it.key}, ${it.value} failed")
//            } catch (ex: IllegalArgumentException) {
//                // Ignore
//            }
//        }
//    }
//
//    @Test
//    fun geoCircle() {
//        val validCircles = mapOf(
//            GeoPoint.create(0.0, 0.0) to Distance.fromRadians(0.0),
//            GeoPoint.create(0.0, 0.0) to Distance.fromRadians(0.1),
//            GeoPoint.create(0.0, 0.0) to Distance.fromRadians(Double.MAX_VALUE)
//        )
//        validCircles.forEach {
//            GeoCircle.create(center = it.key, radius = it.value)
//        }
//    }
//
//    @Test
//    fun geoCircle_invalidArgsThrows() {
//        // Currently it isn't possible to provide invalid args to a GeoCircle since both GeoPoint
//        // and Distance will throw.
//        // GeoCircle.create(center = p1, distance = dist)
//    }
//
//    @Test
//    fun geoCircle_toString() {
//        val sphere = GeoCircle.create(
//            center = GeoPoint.create(0.12, 0.23),
//            radius = Distance.fromKilometers(10.0)
//        )
//        assertEquals("geoCircle([0.23, 0.12], 0.0015696123057604772)", sphere.toString())
//    }
//
//    @Test
//    fun geoCircle_within() {
//        realm.writeBlocking {
//            copyToRealm(
//                Restaurant().apply {
//                    location = Location(latitude = 20.0, longitude = 20.0)
//                }
//            )
//            copyToRealm(
//                Restaurant().apply {
//                    location = Location(latitude = 5.0, longitude = 5.0)
//                }
//            )
//            copyToRealm(
//                Restaurant().apply {
//                    location = Location(latitude = -5.0, longitude = -5.0)
//                }
//            )
//            copyToRealm(
//                Restaurant().apply {
//                    location = Location(latitude = 0.0, longitude = 0.0)
//                }
//            )
//        }
//        var sphere = GeoCircle.create(
//            center = GeoPoint.create(0.0, 0.0),
//            radius = Distance.fromKilometers(0.0)
//        )
//        assertEquals(1, realm.query<Restaurant>("location GEOWITHIN $sphere").count().find())
//
//        sphere = GeoCircle.create(
//            center = GeoPoint.create(0.0, 0.0),
//            radius = Distance.fromKilometers(1000.0)
//        )
//        assertEquals(3, realm.query<Restaurant>("location GEOWITHIN $sphere").count().find())
//    }
//
//    @Test
//    fun geoBox() {
//        val bottomLeft = GeoPoint.create(0.0, 0.0)
//        val topRight = GeoPoint.create(5.0, 5.0)
//        GeoBox.create(bottomLeft, topRight)
//    }
//
//    @Test
//    fun geoBox_toString() {
//        val box = GeoBox.create(
//            bottomLeft = GeoPoint.create(1.1, 2.2),
//            topRight = GeoPoint.create(3.3, 4.4)
//        )
//        assertEquals("geoBox([2.2, 1.1], [4.4, 3.3])", box.toString())
//    }
//
//    @Test
//    fun geoBox_within() {
//        realm.writeBlocking {
//            copyToRealm(
//                Restaurant().apply {
//                    location = Location(latitude = 20.0, longitude = 20.0)
//                }
//            )
//            copyToRealm(
//                Restaurant().apply {
//                    location = Location(latitude = 5.0, longitude = 5.0)
//                }
//            )
//            copyToRealm(
//                Restaurant().apply {
//                    location = Location(latitude = -5.0, longitude = -5.0)
//                }
//            )
//            copyToRealm(
//                Restaurant().apply {
//                    location = Location(latitude = 0.0, longitude = 0.0)
//                }
//            )
//        }
//        var box = GeoBox.create(
//            bottomLeft = GeoPoint.create(-1.0, -1.0),
//            topRight = GeoPoint.create(1.0, 1.0)
//        )
//        assertEquals(1, realm.query<Restaurant>("location GEOWITHIN $box").count().find())
//
//        // (-5.0, -5.0) is not included due to how the geospatial library in Core works.
//        box = GeoBox.create(
//            bottomLeft = GeoPoint.create(-5.0, -5.0),
//            topRight = GeoPoint.create(5.0, 5.0)
//        )
//        assertEquals(2, realm.query<Restaurant>("location GEOWITHIN $box").count().find())
//    }
//
//    @Test
//    fun geoPolygon_toString() {
//        val outer = listOf(
//            GeoPoint.create(0.0, 0.0),
//            GeoPoint.create(5.0, 0.0),
//            GeoPoint.create(5.0, 5.0),
//            GeoPoint.create(0.0, 5.0),
//            GeoPoint.create(0.0, 0.0),
//        )
//        val firstInner = listOf(
//            GeoPoint.create(1.0, 1.0),
//            GeoPoint.create(4.0, 1.0),
//            GeoPoint.create(4.0, 4.0),
//            GeoPoint.create(1.0, 4.0),
//            GeoPoint.create(1.0, 1.0),
//        )
//        val secondInner = listOf(
//            GeoPoint.create(2.0, 2.0),
//            GeoPoint.create(3.0, 2.0),
//            GeoPoint.create(3.0, 3.0),
//            GeoPoint.create(2.0, 3.0),
//            GeoPoint.create(2.0, 2.0),
//        )
//
//        val simplePolygon = GeoPolygon.create(outer)
//        assertEquals("geoPolygon({[0.0, 0.0], [0.0, 5.0], [5.0, 5.0], [5.0, 0.0], [0.0, 0.0]})", simplePolygon.toString())
//
//        val polygonWithOneInnerRing = GeoPolygon.create(outer, firstInner)
//        assertEquals("geoPolygon({[0.0, 0.0], [0.0, 5.0], [5.0, 5.0], [5.0, 0.0], [0.0, 0.0]}, {[1.0, 1.0], [1.0, 4.0], [4.0, 4.0], [4.0, 1.0], [1.0, 1.0]})", polygonWithOneInnerRing.toString())
//
//        val polygonWithTwoInnerRings = GeoPolygon.create(outer, listOf(firstInner, secondInner))
//        assertEquals("geoPolygon({[0.0, 0.0], [0.0, 5.0], [5.0, 5.0], [5.0, 0.0], [0.0, 0.0]}, {[1.0, 1.0], [1.0, 4.0], [4.0, 4.0], [4.0, 1.0], [1.0, 1.0]}, {[2.0, 2.0], [2.0, 3.0], [3.0, 3.0], [3.0, 2.0], [2.0, 2.0]})", polygonWithTwoInnerRings.toString())
//    }
//
//    @Test
//    fun geoPolygon_within() {
//        realm.writeBlocking {
//            copyToRealm(
//                Restaurant().apply {
//                    location = Location(latitude = 20.0, longitude = 20.0)
//                }
//            )
//            copyToRealm(
//                Restaurant().apply {
//                    location = Location(latitude = 5.0, longitude = 5.0)
//                }
//            )
//            copyToRealm(
//                Restaurant().apply {
//                    location = Location(latitude = -5.0, longitude = -5.0)
//                }
//            )
//            copyToRealm(
//                Restaurant().apply {
//                    location = Location(latitude = 0.0, longitude = 0.0)
//                }
//            )
//        }
//
//        val onlyOuterRing = GeoPolygon.create(
//            outerRing = listOf(
//                GeoPoint.create(-5.0, -5.0),
//                GeoPoint.create(5.0, -5.0),
//                GeoPoint.create(5.0, 5.0),
//                GeoPoint.create(-5.0, 5.0),
//                GeoPoint.create(-5.0, -5.0)
//            )
//        )
//        assertEquals(2, realm.query<Restaurant>("location GEOWITHIN $onlyOuterRing").count().find())
//
//        val polygonWithHole = GeoPolygon.create(
//            outerRing = listOf(
//                GeoPoint.create(-5.0, -5.0),
//                GeoPoint.create(5.0, -5.0),
//                GeoPoint.create(5.0, 5.0),
//                GeoPoint.create(-5.0, 5.0),
//                GeoPoint.create(-5.0, -5.0)
//            ),
//            holes = arrayOf(
//                listOf(
//                    GeoPoint.create(-4.0, -4.0),
//                    GeoPoint.create(4.0, -4.0),
//                    GeoPoint.create(4.0, 4.0),
//                    GeoPoint.create(-4.0, 4.0),
//                    GeoPoint.create(-4.0, -4.0)
//                )
//            )
//        )
//        assertEquals(1, realm.query<Restaurant>("location GEOWITHIN $polygonWithHole").count().find())
//    }
//
//    @Test
//    fun geoPolygon_invalidShapesThrows() {
//        // First and last point is not the same
//        assertFailsWith<IllegalArgumentException>("First and last point in the outer ring must be the same") {
//            val shape = GeoPolygon.create(
//                outerRing = listOf(
//                    GeoPoint.create(0.0, 0.0),
//                    GeoPoint.create(0.0, 5.0),
//                    GeoPoint.create(5.0, 5.0),
//                    GeoPoint.create(5.0, 0.0)
//                )
//            )
//        }
//
//        // Same rules for inner holes
//        assertFailsWith<IllegalArgumentException>("First and last point in the outer ring must be the same") {
//            GeoPolygon.create(
//                listOf(
//                    GeoPoint.create(0.0, 0.0),
//                    GeoPoint.create(0.0, 5.0),
//                    GeoPoint.create(5.0, 5.0),
//                    GeoPoint.create(5.0, 0.0),
//                    GeoPoint.create(0.0, 0.0),
//                ),
//                listOf(
//                    GeoPoint.create(0.0, 0.0),
//                    GeoPoint.create(0.0, 5.0),
//                    GeoPoint.create(5.0, 5.0),
//                    GeoPoint.create(5.0, 0.0)
//                )
//            )
//        }
//
//        // We need 3 vertices, which requires 4 geo points. 3 geo points will throw.
//        // First and last point is not the same
//        assertFailsWithMessage<IllegalArgumentException>("") {
//            val triangle = GeoPolygon.create(
//                outerRing = listOf(
//                    GeoPoint.create(0.0, 0.0),
//                    GeoPoint.create(2.5, 5.0),
//                    GeoPoint.create(5.0, 0.0),
//                )
//            )
//        }
//
//        // Holes also require 3 vertices
//        assertFailsWithMessage<IllegalArgumentException>("") {
//            val triangle = GeoPolygon.create(
//                outerRing = listOf(
//                    GeoPoint.create(0.0, 0.0),
//                    GeoPoint.create(2.5, 5.0),
//                    GeoPoint.create(5.0, 0.0),
//                    GeoPoint.create(0.0, 0.0)
//                ),
//                holes = arrayOf(
//                    listOf(
//                        GeoPoint.create(1.0, 1.0),
//                        GeoPoint.create(2.5, 4.0),
//                        GeoPoint.create(4.0, 1.0)
//                    )
//                )
//            )
//        }
//    }
//
//    @Test
//    fun distance_radians() {
//        val validDists = listOf(0.0, 0.1, Double.MIN_VALUE, Double.MAX_VALUE, Double.POSITIVE_INFINITY)
//        validDists.forEach { d: Double ->
//            val dist1 = Distance.fromRadians(d)
//            val dist2 = d.radians
//            assertEquals(d, dist1.inRadians)
//            assertEquals(d, dist2.inRadians)
//        }
//    }
//
//    @Test
//    fun distance_degrees() {
//        val validDists = mapOf(
//            0.0 to 0.0,
//            0.1 to 0.1,
//            Double.MIN_VALUE to 0.0,
//            Double.MAX_VALUE to 1.7976931348623155E308,
//            Double.POSITIVE_INFINITY to Double.POSITIVE_INFINITY
//        )
//        validDists.forEach { (input, output) ->
//            val dist1 = Distance.fromDegrees(input)
//            val dist2 = input.degrees
//            assertEquals(output, dist1.inDegrees, "$input failed.")
//            assertEquals(output, dist2.inDegrees, "$input failed.")
//        }
//    }
//
//    @Test
//    fun distance_kilometer() {
//        val validDists = mapOf(
//            0.0 to 0.0,
//            0.1 to 0.1,
//            Double.MIN_VALUE to 0.0,
//            Double.MAX_VALUE to Double.MAX_VALUE,
//            Double.POSITIVE_INFINITY to Double.POSITIVE_INFINITY
//        )
//        validDists.forEach { (input, output) ->
//            val dist1 = Distance.fromKilometers(input)
//            val dist2 = input.km
//            assertEquals(output, dist1.inKilometers, "$input failed.")
//            assertEquals(output, dist2.inKilometers, "$input failed.")
//        }
//    }
//
//    @Test
//    fun distance_miles() {
//        val validDists = mapOf(
//            0.0 to 0.0,
//            0.1 to 0.10000000000000002,
//            Double.MIN_VALUE to 0.0,
//            Double.MAX_VALUE to Double.POSITIVE_INFINITY,
//            Double.POSITIVE_INFINITY to Double.POSITIVE_INFINITY
//        )
//        validDists.forEach { (input, output) ->
//            val dist1 = Distance.fromMiles(input)
//            val dist2 = input.miles
//            assertEquals(output, dist1.inMiles, "$input failed.")
//            assertEquals(output, dist2.inMiles, "$input failed.")
//        }
//    }
//
//    @Test
//    fun distance_conversions() {
//        val d1 = Distance.fromKilometers(100.0)
//        assertEquals(62.13711922373339, d1.inMiles)
//        assertEquals(100.0, d1.inKilometers)
//        assertEquals(0.01569612305760477, d1.inRadians)
//        assertEquals(0.8993216059187306, d1.inDegrees)
//
//        val d2 = Distance.fromRadians(1.0)
//        assertEquals(57.29577951308232, d2.inDegrees)
//    }
//
//    @Test
//    fun distance_invalidValueThrows() {
//        val invalidDists = listOf(-0.1, Double.NEGATIVE_INFINITY)
//        invalidDists.forEach { d: Double ->
//            assertFailsWith<IllegalArgumentException> {
//                Distance.fromRadians(d)
//            }
//            assertFailsWith<IllegalArgumentException> {
//                Distance.fromKilometers(d)
//            }
//            assertFailsWith<IllegalArgumentException> {
//                Distance.fromMiles(d)
//            }
//        }
//    }
//
//    // Verify that geo objects can be passed directly as query arguments
//    // Kotlin will do implicit conversion to strings until native type support is added
//    @Test
//    fun asQueryArguments() {
//        realm.writeBlocking {
//            copyToRealm(
//                Restaurant().apply {
//                    location = Location(latitude = 20.0, longitude = 20.0)
//                }
//            )
//            copyToRealm(
//                Restaurant().apply {
//                    location = Location(latitude = 5.0, longitude = 5.0)
//                }
//            )
//            copyToRealm(
//                Restaurant().apply {
//                    location = Location(latitude = -5.0, longitude = -5.0)
//                }
//            )
//            copyToRealm(
//                Restaurant().apply {
//                    location = Location(latitude = 0.0, longitude = 0.0)
//                }
//            )
//        }
//
//        var sphere = GeoCircle.create(
//            center = GeoPoint.create(0.0, 0.0),
//            radius = Distance.fromKilometers(0.0)
//        )
//        assertEquals(1, realm.query<Restaurant>("location GEOWITHIN $0", sphere).count().find())
//
//        var box = GeoBox.create(
//            bottomLeft = GeoPoint.create(-1.0, -1.0),
//            topRight = GeoPoint.create(1.0, 1.0)
//        )
//        assertEquals(1, realm.query<Restaurant>("location GEOWITHIN $0", box).count().find())
//
//        val onlyOuterRing = GeoPolygon.create(
//            outerRing = listOf(
//                GeoPoint.create(-5.0, -5.0),
//                GeoPoint.create(5.0, -5.0),
//                GeoPoint.create(5.0, 5.0),
//                GeoPoint.create(-5.0, 5.0),
//                GeoPoint.create(-5.0, -5.0)
//            )
//        )
//        assertEquals(2, realm.query<Restaurant>("location GEOWITHIN $0", onlyOuterRing).count().find())
//
//        val polygonWithHole = GeoPolygon.create(
//            outerRing = listOf(
//                GeoPoint.create(-5.0, -5.0),
//                GeoPoint.create(5.0, -5.0),
//                GeoPoint.create(5.0, 5.0),
//                GeoPoint.create(-5.0, 5.0),
//                GeoPoint.create(-5.0, -5.0)
//            ),
//            holes = arrayOf(
//                listOf(
//                    GeoPoint.create(-4.0, -4.0),
//                    GeoPoint.create(4.0, -4.0),
//                    GeoPoint.create(4.0, 4.0),
//                    GeoPoint.create(-4.0, 4.0),
//                    GeoPoint.create(-4.0, -4.0)
//                )
//            )
//        )
//        assertEquals(1, realm.query<Restaurant>("location GEOWITHIN $0", polygonWithHole).count().find())
//    }
}
