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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.kotlin.types

/**
 * This class represents a polygon on the surface of the earth. It cannot be persisted - i.e you
 * can't declare a Realm property that is of type [GeoPolygon]. It can be only used as a query
 * argument for a geospatial query.
 *
 * A [GeoPolygon] describes a shape compromised of 3 or more line segments. It comprises of one
 * outer ring and 0 or more rings representing holes with the following restrictions:
 *
 * - Each ring must consist of at least 3 distinct points (vertices). The first and the last point
 *   must be the same to indicate a closed ring (meaning you need at least 4 points to define the
 *   polygon).
 * - Rings may not cross, i.e. the boundary of a ring may not intersect both the interior and
 *   exterior of any other ring.
 * - Rings may not share edges, i.e. if a ring contains an edge AB, then no other ring may contain
 *   AB or BA.
 * - Rings may share vertices, however no vertex may appear twice in a single ring.
 * - No ring may be empty.
 *
 * Holes may be nested inside each other, in which case a location will be considered "inside" the
 * polygon if it is included in an odd number of rings. For example, a polygon representing a square
 * with side 10 centered at (0,0) with holes representing squares with sides 5 and 2, centered at
 * (0,0) will  include the location (1, 1) because it is contained in 3 rings, but not (3, 3),
 * because it is contained in 2.
 *
 * Using it can look something like this:
 *
 * ```
 * val newYork = GeoPoint(latitude = 40.730610, longitude = -73.935242)
 * val searchArea = GeoCircle(center = newYork, radius = Distance.fromMiles(2.0))
 * val restaurants = realm.query<Restaurant>("location GEOWITHIN $searchArea").find()
 * ```
 */
public data class GeoPolygon(

    /**
     * A list of [GeoPoint]s representing the outer ring of the polygon.
     *
     * The outer ring must contain at least 3 unique points. The first and the last point may
     * be identical, but no other duplicates are allowed. Each subsequent pair of points represents
     * an edge in the polygon with the first and the last point being implicitly connected.
     */
    public val outerRing: List<GeoPoint>,

    /**
     * A list of "inner" rings. Each of these rings has the same requirements as the [outerRing].
     *
     * A point is considered "inside" the polygon if it is contained by an odd number of rings
     * and "outside" if it's contained by an even number of rings.
     */
    public val holes: Iterable<List<GeoPoint>> = emptyList()
) {

    /**
     * Construct a polygon with a single inner ring. This will create a polygon that only matches
     * locations between these two rings.
     */
    public constructor(outerRing: List<GeoPoint>, hole: List<GeoPoint>) : this(outerRing, listOf(hole))

    init {
        // Do basic input validation. Core will validate the rest when the query is run.
        // Better input validation will be available later.
        @Suppress("MagicNumber")
        val minSize = 3
        if (outerRing.size < minSize) {
            throw IllegalArgumentException("The outer ring requires at least 3 points: ${outerRing.size}")
        }
        holes.forEachIndexed { i, ring ->
            if (ring.size < minSize) {
                throw IllegalArgumentException("The inner at index $i requires at least 3 points: ${ring.size}")
            }
        }
    }

    /**
     * Returns the textual representation of a [GeoPolygon], this is also formatting in a a way
     * that makes it usable in queries, e.g.:
     *
     * ```
     * val outerRing = listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0), GeoPoint(1.0, 1.0), GeoPoint(0.0, 1.0))
     * val hole = listOf
     *
     * val sphere = GeoPolygon(outerRing = listOf(GeoPoint(0.0, 0.0), radius = Distance.fromKilometers(10.0))
     * val results = realm.query<Restaurant>("location GEOWITHIN $sphere").find()
     */
    public override fun toString(): String {
        val outerRingString = polygonToQueryString(outerRing)
        val innerRingString = holes.joinToString(separator = ", ") { hole: List<GeoPoint> ->
            polygonToQueryString(hole)
        }
        if (innerRingString.isNotEmpty()) {
            return "geoPolygon($outerRingString, $innerRingString)"
        } else {
            return "geoPolygon($outerRingString)"
        }
    }

    private fun polygonToQueryString(points: List<GeoPoint>): String {
        return points.joinToString(prefix = "{", postfix = "}") { "[${it.longitude}, ${it.latitude}]" }
    }
}
