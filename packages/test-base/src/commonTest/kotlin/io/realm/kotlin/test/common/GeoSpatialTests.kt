/*
 * Copyright 2023 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.kotlin.test.common

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.km
import io.realm.kotlin.ext.miles
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.radians
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.test.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.Distance
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.GeoBox
import io.realm.kotlin.types.GeoCircle
import io.realm.kotlin.types.GeoPoint
import io.realm.kotlin.types.GeoPolygon
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

class Restaurant : RealmObject {
    var location: Location? = null
}

// Custom embedded model class for storing GeoPoints in Realm.
class Location : EmbeddedRealmObject {
    public constructor(latitude: Double, longitude: Double) {
        this.latitude = latitude
        this.longitude = longitude
        coordinates.apply {
            add(longitude)
            add(latitude)
        }
    }
    public constructor() : this(0.0, 0.0) // Empty constructor required by Realm. Should not be used.

    // Name and type required by Realm
    public var coordinates: RealmList<Double> = realmListOf()

    // Name and type by Realm
    private var type: String = "Point"

    @Ignore
    public val latitude: Double

    @Ignore
    public val longitude: Double
}

class GeoSpatialTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    private val configuration: RealmConfiguration by lazy {
        RealmConfiguration.Builder(schema = setOf(Restaurant::class, Location::class))
            .directory(tmpDir)
            .build()
    }

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
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
    fun geoPoint() {
        val validPoints = mapOf(
            0.0 to 0.0,

            // Latitude
            Double.MIN_VALUE to 0.0,
            -90.0 to 0.0,
            -89.999 to 0.0,
            90.0 to 0.0,
            89.999 to 0.0,

            // Longitude
            Double.MIN_VALUE to 0.0,
            0.0 to -180.0,
            0.0 to -179.999,
            0.0 to 180.0,
            0.0 to -179.999
        )
        validPoints.forEach {
            val p = GeoPoint(latitude = it.key, longitude = it.value)
            assertEquals(it.key, p.latitude)
            assertEquals(it.value, p.longitude)
        }
    }

    @Test
    fun geoPoint_invalidArgsThrows() {
        val invalidPoints = mapOf(
            // Latitude
            -90.1 to 0.0,
            Double.NEGATIVE_INFINITY to 0.0,
            90.1 to 0.0,
            Double.POSITIVE_INFINITY to 0.0,

            // Longitude
            0.0 to -180.1,
            0.0 to Double.NEGATIVE_INFINITY,
            0.0 to 180.1,
            0.0 to Double.POSITIVE_INFINITY
        )
        invalidPoints.forEach {
            try {
                GeoPoint(latitude = it.key, longitude = it.value)
                fail("${it.key}, ${it.value} failed")
            } catch (ex: IllegalArgumentException) {
                // Ignore
            }
        }
    }

    @Test
    fun geoCircle() {
        val validCircles = mapOf(
            GeoPoint(0.0, 0.0) to Distance.fromRadians(0.0),
            GeoPoint(0.0, 0.0) to Distance.fromRadians(0.1),
            GeoPoint(0.0, 0.0) to Distance.fromRadians(Double.MAX_VALUE)
        )
        validCircles.forEach {
            GeoCircle(center = it.key, radius = it.value)
        }
    }

    @Test
    fun geoCircle_invalidArgsThrows() {
        // Currently it isn't possible to provide invalid args to a GeoCircle since both GeoPoint
        // and Distance will throw.
        // GeoCircle(center = p1, distance = dist)
    }

    @Test
    fun geoCircle_toString() {
        val sphere = GeoCircle(center = GeoPoint(0.12, 0.23), radius = Distance.fromKilometers(10.0))
        assertEquals("geoCircle([0.23, 0.12], 1.569612305760477)", sphere.toString())
    }

    @Test
    fun geoCircle_within() {
        realm.writeBlocking {
            copyToRealm(
                Restaurant().apply {
                    location = Location(latitude = 20.0, longitude = 20.0)
                }
            )
            copyToRealm(
                Restaurant().apply {
                    location = Location(latitude = 5.0, longitude = 5.0)
                }
            )
            copyToRealm(
                Restaurant().apply {
                    location = Location(latitude = -5.0, longitude = -5.0)
                }
            )
            copyToRealm(
                Restaurant().apply {
                    location = Location(latitude = 0.0, longitude = 0.0)
                }
            )
        }
        var sphere = GeoCircle(center = GeoPoint(0.0, 0.0), radius = Distance.fromKilometers(0.0))
        assertEquals(1, realm.query<Restaurant>("location GEOWITHIN $sphere").count().find())

        sphere = GeoCircle(center = GeoPoint(0.0, 0.0), radius = Distance.fromKilometers(3.0))
        assertEquals(3, realm.query<Restaurant>("location GEOWITHIN $sphere").count().find())
    }

    @Test
    fun geoBox() {
        val bottomLeft = GeoPoint(0.0, 0.0)
        val topRight = GeoPoint(5.0, 5.0)
        GeoBox(bottomLeft, topRight)
    }

    @Test
    fun geoBox_toString() {
        val box = GeoBox(bottomLeft = GeoPoint(1.1, 2.2), topRight = GeoPoint(3.3, 4.4))
        assertEquals("geoBox([2.2, 1.1], [4.4, 3.3])", box.toString())
    }

    @Test
    fun geoBox_within() {
        realm.writeBlocking {
            copyToRealm(
                Restaurant().apply {
                    location = Location(latitude = 20.0, longitude = 20.0)
                }
            )
            copyToRealm(
                Restaurant().apply {
                    location = Location(latitude = 5.0, longitude = 5.0)
                }
            )
            copyToRealm(
                Restaurant().apply {
                    location = Location(latitude = -5.0, longitude = -5.0)
                }
            )
            copyToRealm(
                Restaurant().apply {
                    location = Location(latitude = 0.0, longitude = 0.0)
                }
            )
        }
        var box = GeoBox(bottomLeft = GeoPoint(0.0, 0.0), topRight = GeoPoint(0.0, 0.0))
        assertEquals(1, realm.query<Restaurant>("location GEOWITHIN $box").count().find())

        box = GeoBox(bottomLeft = GeoPoint(-5.0, -5.0), topRight = GeoPoint(5.0, 5.0))
        assertEquals(3, realm.query<Restaurant>("location GEOWITHIN $box").count().find())
    }

    @Test
    fun geoPolygon_toString() {
        val outer = listOf(
            GeoPoint(0.0, 0.0),
            GeoPoint(5.0, 0.0),
            GeoPoint(5.0, 5.0),
            GeoPoint(0.0, 5.0)
        )
        val firstInner = listOf(
            GeoPoint(1.0, 1.0),
            GeoPoint(4.0, 1.0),
            GeoPoint(4.0, 4.0),
            GeoPoint(1.0, 4.0)
        )
        val secondInner = listOf(
            GeoPoint(2.0, 2.0),
            GeoPoint(3.0, 2.0),
            GeoPoint(3.0, 3.0),
            GeoPoint(2.0, 3.0)
        )

        val simplePolygon = GeoPolygon(outer)
        assertEquals("geoPolygon({[0.0, 0.0], [0.0, 5.0], [5.0, 5.0], [5.0, 0.0]})", simplePolygon.toString())

        val polygonWithOneInnerRing = GeoPolygon(outer, firstInner)
        assertEquals("geoPolygon({[0.0, 0.0], [0.0, 5.0], [5.0, 5.0], [5.0, 0.0]}, {[1.0, 1.0], [1.0, 4.0], [4.0, 4.0], [4.0, 1.0]})", polygonWithOneInnerRing.toString())

        val polygonWithTwoInnerRings = GeoPolygon(outer, listOf(firstInner, secondInner))
        assertEquals("geoPolygon({[0.0, 0.0], [0.0, 5.0], [5.0, 5.0], [5.0, 0.0]}, {[1.0, 1.0], [1.0, 4.0], [4.0, 4.0], [4.0, 1.0]}, {[2.0, 2.0], [2.0, 3.0], [3.0, 3.0], [3.0, 2.0]})", polygonWithTwoInnerRings.toString())
    }

    @Test
    fun geoPolygon_within() {
        realm.writeBlocking {
            copyToRealm(
                Restaurant().apply {
                    location = Location(latitude = 20.0, longitude = 20.0)
                }
            )
            copyToRealm(
                Restaurant().apply {
                    location = Location(latitude = 5.0, longitude = 5.0)
                }
            )
            copyToRealm(
                Restaurant().apply {
                    location = Location(latitude = -5.0, longitude = -5.0)
                }
            )
            copyToRealm(
                Restaurant().apply {
                    location = Location(latitude = 0.0, longitude = 0.0)
                }
            )
        }

        val onlyOuterRing = GeoPolygon(
            outerRing = listOf(
                GeoPoint(-5.0, -5.0),
                GeoPoint(5.0, -5.0),
                GeoPoint(5.0, 5.0),
                GeoPoint(-5.0, 5.0),
                GeoPoint(-5.0, -5.0)
            )
        )
        assertEquals(2, realm.query<Restaurant>("location GEOWITHIN $onlyOuterRing").count().find())

        val polygonWithHole = GeoPolygon(
            outerRing = listOf(
                GeoPoint(-5.0, -5.0),
                GeoPoint(5.0, -5.0),
                GeoPoint(5.0, 5.0),
                GeoPoint(-5.0, 5.0),
                GeoPoint(-5.0, -5.0)
            ),
            hole = listOf(
                GeoPoint(-4.0, -4.0),
                GeoPoint(4.0, -4.0),
                GeoPoint(4.0, 4.0),
                GeoPoint(-4.0, 4.0),
                GeoPoint(-4.0, -4.0)
            )
        )
        assertEquals(1, realm.query<Restaurant>("location GEOWITHIN $polygonWithHole").count().find())
    }

    @Test
    fun geoPolygon_within_throwsOnNonClosedPolygon() {
        val polygon1 = GeoPolygon(
            outerRing = listOf(
                GeoPoint(-5.0, -5.0),
                GeoPoint(5.0, -5.0),
                GeoPoint(5.0, 5.0)
            )
        )
        assertFailsWithMessage<IllegalArgumentException>("Invalid region in GEOWITHIN query") {
            realm.query<Restaurant>("location GEOWITHIN $polygon1").count()
        }
    }

    @Test
    fun distance_radians() {
        val validDists = listOf(0.0, 0.1, Double.MIN_VALUE, Double.MAX_VALUE, Double.POSITIVE_INFINITY)
        validDists.forEach { d: Double ->
            val dist1 = Distance.fromRadians(d)
            val dist2 = d.radians
            assertEquals(d, dist1.inRadians())
            assertEquals(d, dist2.inRadians())
        }
    }

    @Test
    fun distance_kilometer() {
        val validDists = mapOf(
            0.0 to 0.0,
            0.1 to 0.1,
            Double.MIN_VALUE to 0.0,
            Double.MAX_VALUE to Double.MAX_VALUE,
            Double.POSITIVE_INFINITY to Double.POSITIVE_INFINITY
        )
        validDists.forEach { (input, output) ->
            val dist1 = Distance.fromKilometers(input)
            val dist2 = input.km
            assertEquals(output, dist1.inKilometers(), "$input failed.")
            assertEquals(output, dist2.inKilometers(), "$input failed.")
        }
    }

    @Test
    fun distance_miles() {
        val validDists = mapOf(
            0.0 to 0.0,
            0.1 to 0.10000000000000002,
            Double.MIN_VALUE to 0.0,
            Double.MAX_VALUE to Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY to Double.POSITIVE_INFINITY
        )
        validDists.forEach { (input, output) ->
            val dist1 = Distance.fromMiles(input)
            val dist2 = input.miles
            assertEquals(output, dist1.inMiles(), "$input failed.")
            assertEquals(output, dist2.inMiles(), "$input failed.")
        }
    }

    @Test
    fun distance_invalidValueThrows() {
        val invalidDists = listOf(-0.1, Double.NEGATIVE_INFINITY)
        invalidDists.forEach { d: Double ->
            assertFailsWith<IllegalArgumentException> {
                Distance.fromRadians(d)
            }
            assertFailsWith<IllegalArgumentException> {
                Distance.fromKilometers(d)
            }
            assertFailsWith<IllegalArgumentException> {
                Distance.fromMiles(d)
            }
        }
    }
}
