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
package io.realm.kotlin.types.geo

import io.realm.kotlin.annotations.ExperimentalGeoSpatialApi
import io.realm.kotlin.internal.geo.UnmanagedGeoPolygon

/**
 * This class represents a polygon on the surface of the earth. It cannot be persisted - i.e you
 * can't declare a Realm property that is of type [GeoPolygon]. It can be only used as a query
 * argument for a geospatial query.
 *
 * A [GeoPolygon] describes a shape represented by 3 or more line segments. It comprises of one
 * outer ring and 0 or more rings representing holes with the following restrictions:
 *
 * - Each ring must consist of at least 3 vertices. The first and the last point must be the same
 *   to indicate a closed ring (meaning you need at least 4 points to define the
 *   polygon).
 * - Rings may not cross, i.e. the boundary of a ring may not intersect both the interior and
 *   exterior of any other ring.
 * - Rings may not share edges, i.e. if a ring contains an edge AB, then no other ring may contain
 *   AB or BA.
 * - Rings may share vertices, however no vertex may appear twice in a single ring.
 * - No ring may be empty.
 *
 * Holes may be nested inside each other, in which case a location will be considered "inside" the
 * polygon if it is included in an odd number of rings, counting the outer ring as 1. For example,
 * a polygon representing a square with side 10 centered at (0,0) with holes representing squares
 * with sides 5 and 2, centered at (0,0) will  include the location (1, 1) because it is contained
 * in 3 rings, but not (3, 3), because it is contained in 2.
 *
 * Using it can look something like this:
 *
 * ```
 * val searchArea = GeoPolygon.create(
 *     outerRing = listOf(
 *         GeoPoint.create(0.0, 0.0),
 *         GeoPoint.create(0.0, 5.0),
 *         GeoPoint.create(5.0, 5.0),
 *         GeoPoint.create(5.0, 0.0),
 *         GeoPoint.create(0.0, 0.0),
 *     ),
 *     holes = arrayOf(listOf(
 *         GeoPoint.create(1.0, 1.0),
 *         GeoPoint.create(1.0, 4.0),
 *         GeoPoint.create(4.0, 4.0),
 *         GeoPoint.create(4.0, 1.0)
 *     ))
 * )
 * val restaurants = realm.query<Restaurant>("location GEOWITHIN $searchArea").find()
 * * ```
 */
@ExperimentalGeoSpatialApi
public interface GeoPolygon {
    /**
     * A list of [GeoPoint]s representing the outer ring of the polygon.
     */
    public val outerRing: List<GeoPoint>

    /**
     * A list of inner rings, each ring is inside the next.
     */
    public val holes: List<List<GeoPoint>>

    /**
     * Returns the textual representation of the [GeoPolygon], this is also formatting it in a way
     * that makes it usable in queries, e.g.:
     *
     * ```
     * val outerRing = listOf(
     *     GeoPoint.create(0.0, 0.0),
     *     GeoPoint.create(10.0, 0.0),
     *     GeoPoint.create(10.0, 10.0),
     *     GeoPoint.create(0.0, 10.0),
     *     GeoPoint.create(0.0, 0.0)
     * )
     * val searchArea = GeoPolygon.create(outerRing)
     * val results = realm.query<Restaurant>("location GEOWITHIN searchArea").find()
     */
    override fun toString(): String

    public companion object {

        /**
         * Create a [GeoPolygon] instance.
         *
         * @param outerRing A list of [GeoPoint]s representing the outer ring of the polygon. The
         * outer ring must contain at least 3 unique points. The first and the last point may
         * be identical, but no other duplicates are allowed. Each subsequent pair of points represents
         * an edge in the polygon with the first and the last point being implicitly connected.
         * @param holes A list of "inner" rings. Each of these rings has the same requirements as
         * the [outerRing]. A point is considered "inside" the polygon if it is contained by an odd
         * number of rings and "outside" if it's contained by an even number of rings.
         */
        public fun create(outerRing: List<GeoPoint>, vararg holes: List<GeoPoint>): GeoPolygon =
            create(outerRing, holes.toList())

        /**
         * Create a [GeoPolygon] instance.
         *
         * @param outerRing A list of [GeoPoint]s representing the outer ring of the polygon. The
         * outer ring must contain at least 3 unique points. The first and the last point may
         * be identical, but no other duplicates are allowed. Each subsequent pair of points represents
         * an edge in the polygon with the first and the last point being implicitly connected.
         * @param holes A list of "inner" rings. Each of these rings has the same requirements as
         * the [outerRing]. A point is considered "inside" the polygon if it is contained by an odd
         * number of rings and "outside" if it's contained by an even number of rings.
         */
        public fun create(outerRing: List<GeoPoint>, holes: List<List<GeoPoint>>): GeoPolygon {
            return UnmanagedGeoPolygon(outerRing, holes)
        }
    }
}
