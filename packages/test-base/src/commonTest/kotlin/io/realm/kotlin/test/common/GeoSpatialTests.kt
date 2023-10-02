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
import io.realm.kotlin.annotations.ExperimentalGeoSpatialApi
import io.realm.kotlin.entities.Location
import io.realm.kotlin.entities.Restaurant
import io.realm.kotlin.ext.degrees
import io.realm.kotlin.ext.km
import io.realm.kotlin.ext.miles
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.radians
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.test.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.geo.Distance
import io.realm.kotlin.types.geo.GeoBox
import io.realm.kotlin.types.geo.GeoCircle
import io.realm.kotlin.types.geo.GeoPoint
import io.realm.kotlin.types.geo.GeoPolygon
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

@OptIn(ExperimentalGeoSpatialApi::class)
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
            val p = GeoPoint.create(latitude = it.key, longitude = it.value)
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
                GeoPoint.create(latitude = it.key, longitude = it.value)
                fail("${it.key}, ${it.value} failed")
            } catch (ex: IllegalArgumentException) {
                // Ignore
            }
        }
    }

    @Test
    fun geoCircle() {
        val validCircles = mapOf(
            GeoPoint.create(0.0, 0.0) to Distance.fromRadians(0.0),
            GeoPoint.create(0.0, 0.0) to Distance.fromRadians(0.1),
            GeoPoint.create(0.0, 0.0) to Distance.fromRadians(Double.MAX_VALUE)
        )
        validCircles.forEach {
            GeoCircle.create(center = it.key, radius = it.value)
        }
    }

    @Test
    fun geoCircle_invalidArgsThrows() {
        // Currently it isn't possible to provide invalid args to a GeoCircle since both GeoPoint
        // and Distance will throw.
        // GeoCircle.create(center = p1, distance = dist)
    }

    @Test
    fun geoCircle_toString() {
        val sphere = GeoCircle.create(
            center = GeoPoint.create(0.12, 0.23),
            radius = Distance.fromKilometers(10.0)
        )
        assertEquals("geoCircle([0.23, 0.12], 0.0015696123057604772)", sphere.toString())
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
        var sphere = GeoCircle.create(
            center = GeoPoint.create(0.0, 0.0),
            radius = Distance.fromKilometers(0.0)
        )
        assertEquals(1, realm.query<Restaurant>("location GEOWITHIN $sphere").count().find())

        sphere = GeoCircle.create(
            center = GeoPoint.create(0.0, 0.0),
            radius = Distance.fromKilometers(1000.0)
        )
        assertEquals(3, realm.query<Restaurant>("location GEOWITHIN $sphere").count().find())
    }

    @Test
    fun geoBox() {
        val bottomLeft = GeoPoint.create(0.0, 0.0)
        val topRight = GeoPoint.create(5.0, 5.0)
        GeoBox.create(bottomLeft, topRight)
    }

    @Test
    fun geoBox_toString() {
        val box = GeoBox.create(
            bottomLeft = GeoPoint.create(1.1, 2.2),
            topRight = GeoPoint.create(3.3, 4.4)
        )
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
        var box = GeoBox.create(
            bottomLeft = GeoPoint.create(-1.0, -1.0),
            topRight = GeoPoint.create(1.0, 1.0)
        )
        assertEquals(1, realm.query<Restaurant>("location GEOWITHIN $box").count().find())

        // (-5.0, -5.0) is not included due to how the geospatial library in Core works.
        box = GeoBox.create(
            bottomLeft = GeoPoint.create(-5.0, -5.0),
            topRight = GeoPoint.create(5.0, 5.0)
        )
        assertEquals(2, realm.query<Restaurant>("location GEOWITHIN $box").count().find())
    }

    @Test
    fun geoPolygon_toString() {
        val outer = listOf(
            GeoPoint.create(0.0, 0.0),
            GeoPoint.create(5.0, 0.0),
            GeoPoint.create(5.0, 5.0),
            GeoPoint.create(0.0, 5.0),
            GeoPoint.create(0.0, 0.0),
        )
        val firstInner = listOf(
            GeoPoint.create(1.0, 1.0),
            GeoPoint.create(4.0, 1.0),
            GeoPoint.create(4.0, 4.0),
            GeoPoint.create(1.0, 4.0),
            GeoPoint.create(1.0, 1.0),
        )
        val secondInner = listOf(
            GeoPoint.create(2.0, 2.0),
            GeoPoint.create(3.0, 2.0),
            GeoPoint.create(3.0, 3.0),
            GeoPoint.create(2.0, 3.0),
            GeoPoint.create(2.0, 2.0),
        )

        val simplePolygon = GeoPolygon.create(outer)
        assertEquals("geoPolygon({[0.0, 0.0], [0.0, 5.0], [5.0, 5.0], [5.0, 0.0], [0.0, 0.0]})", simplePolygon.toString())

        val polygonWithOneInnerRing = GeoPolygon.create(outer, firstInner)
        assertEquals("geoPolygon({[0.0, 0.0], [0.0, 5.0], [5.0, 5.0], [5.0, 0.0], [0.0, 0.0]}, {[1.0, 1.0], [1.0, 4.0], [4.0, 4.0], [4.0, 1.0], [1.0, 1.0]})", polygonWithOneInnerRing.toString())

        val polygonWithTwoInnerRings = GeoPolygon.create(outer, listOf(firstInner, secondInner))
        assertEquals("geoPolygon({[0.0, 0.0], [0.0, 5.0], [5.0, 5.0], [5.0, 0.0], [0.0, 0.0]}, {[1.0, 1.0], [1.0, 4.0], [4.0, 4.0], [4.0, 1.0], [1.0, 1.0]}, {[2.0, 2.0], [2.0, 3.0], [3.0, 3.0], [3.0, 2.0], [2.0, 2.0]})", polygonWithTwoInnerRings.toString())
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

        val onlyOuterRing = GeoPolygon.create(
            outerRing = listOf(
                GeoPoint.create(-5.0, -5.0),
                GeoPoint.create(5.0, -5.0),
                GeoPoint.create(5.0, 5.0),
                GeoPoint.create(-5.0, 5.0),
                GeoPoint.create(-5.0, -5.0)
            )
        )
        assertEquals(2, realm.query<Restaurant>("location GEOWITHIN $onlyOuterRing").count().find())

        val polygonWithHole = GeoPolygon.create(
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
        )
        assertEquals(1, realm.query<Restaurant>("location GEOWITHIN $polygonWithHole").count().find())
    }

    @Test
    fun geoPolygon_invalidShapesThrows() {
        // First and last point is not the same
        assertFailsWith<IllegalArgumentException>("First and last point in the outer ring must be the same") {
            val shape = GeoPolygon.create(
                outerRing = listOf(
                    GeoPoint.create(0.0, 0.0),
                    GeoPoint.create(0.0, 5.0),
                    GeoPoint.create(5.0, 5.0),
                    GeoPoint.create(5.0, 0.0)
                )
            )
        }

        // Same rules for inner holes
        assertFailsWith<IllegalArgumentException>("First and last point in the outer ring must be the same") {
            GeoPolygon.create(
                listOf(
                    GeoPoint.create(0.0, 0.0),
                    GeoPoint.create(0.0, 5.0),
                    GeoPoint.create(5.0, 5.0),
                    GeoPoint.create(5.0, 0.0),
                    GeoPoint.create(0.0, 0.0),
                ),
                listOf(
                    GeoPoint.create(0.0, 0.0),
                    GeoPoint.create(0.0, 5.0),
                    GeoPoint.create(5.0, 5.0),
                    GeoPoint.create(5.0, 0.0)
                )
            )
        }

        // We need 3 vertices, which requires 4 geo points. 3 geo points will throw.
        // First and last point is not the same
        assertFailsWithMessage<IllegalArgumentException>("") {
            val triangle = GeoPolygon.create(
                outerRing = listOf(
                    GeoPoint.create(0.0, 0.0),
                    GeoPoint.create(2.5, 5.0),
                    GeoPoint.create(5.0, 0.0),
                )
            )
        }

        // Holes also require 3 vertices
        assertFailsWithMessage<IllegalArgumentException>("") {
            val triangle = GeoPolygon.create(
                outerRing = listOf(
                    GeoPoint.create(0.0, 0.0),
                    GeoPoint.create(2.5, 5.0),
                    GeoPoint.create(5.0, 0.0),
                    GeoPoint.create(0.0, 0.0)
                ),
                holes = arrayOf(
                    listOf(
                        GeoPoint.create(1.0, 1.0),
                        GeoPoint.create(2.5, 4.0),
                        GeoPoint.create(4.0, 1.0)
                    )
                )
            )
        }
    }

    @Test
    fun distance_radians() {
        val validDists = listOf(0.0, 0.1, Double.MIN_VALUE, Double.MAX_VALUE, Double.POSITIVE_INFINITY)
        validDists.forEach { d: Double ->
            val dist1 = Distance.fromRadians(d)
            val dist2 = d.radians
            assertEquals(d, dist1.inRadians)
            assertEquals(d, dist2.inRadians)
        }
    }

    @Test
    fun distance_degrees() {
        val validDists = mapOf(
            0.0 to 0.0,
            0.1 to 0.1,
            Double.MIN_VALUE to 0.0,
            Double.MAX_VALUE to 1.7976931348623155E308,
            Double.POSITIVE_INFINITY to Double.POSITIVE_INFINITY
        )
        validDists.forEach { (input, output) ->
            val dist1 = Distance.fromDegrees(input)
            val dist2 = input.degrees
            assertEquals(output, dist1.inDegrees, "$input failed.")
            assertEquals(output, dist2.inDegrees, "$input failed.")
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
            assertEquals(output, dist1.inKilometers, "$input failed.")
            assertEquals(output, dist2.inKilometers, "$input failed.")
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
            assertEquals(output, dist1.inMiles, "$input failed.")
            assertEquals(output, dist2.inMiles, "$input failed.")
        }
    }

    @Test
    fun distance_conversions() {
        val d1 = Distance.fromKilometers(100.0)
        assertEquals(62.13711922373339, d1.inMiles)
        assertEquals(100.0, d1.inKilometers)
        assertEquals(0.01569612305760477, d1.inRadians)
        assertEquals(0.8993216059187306, d1.inDegrees)

        val d2 = Distance.fromRadians(1.0)
        assertEquals(57.29577951308232, d2.inDegrees)
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

    // Verify that geo objects can be passed directly as query arguments
    // Kotlin will do implicit conversion to strings until native type support is added
    @Test
    fun asQueryArguments() {
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

        var sphere = GeoCircle.create(
            center = GeoPoint.create(0.0, 0.0),
            radius = Distance.fromKilometers(0.0)
        )
        assertEquals(1, realm.query<Restaurant>("location GEOWITHIN $0", sphere).count().find())

        var box = GeoBox.create(
            bottomLeft = GeoPoint.create(-1.0, -1.0),
            topRight = GeoPoint.create(1.0, 1.0)
        )
        assertEquals(1, realm.query<Restaurant>("location GEOWITHIN $0", box).count().find())

        val onlyOuterRing = GeoPolygon.create(
            outerRing = listOf(
                GeoPoint.create(-5.0, -5.0),
                GeoPoint.create(5.0, -5.0),
                GeoPoint.create(5.0, 5.0),
                GeoPoint.create(-5.0, 5.0),
                GeoPoint.create(-5.0, -5.0)
            )
        )
        assertEquals(2, realm.query<Restaurant>("location GEOWITHIN $0", onlyOuterRing).count().find())

        val polygonWithHole = GeoPolygon.create(
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
        )
        assertEquals(1, realm.query<Restaurant>("location GEOWITHIN $0", polygonWithHole).count().find())
    }
}
