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
package io.realm.kotlin.internal.geo

import io.realm.kotlin.annotations.ExperimentalGeoSpatialApi
import io.realm.kotlin.types.geo.GeoPoint
import io.realm.kotlin.types.geo.GeoPolygon

@OptIn(ExperimentalGeoSpatialApi::class)
public class UnmanagedGeoPolygon(
    public override val outerRing: List<GeoPoint>,
    public override val holes: List<List<GeoPoint>> = emptyList()
) : GeoPolygon {

    private companion object {
        private const val MIN_RING_SIZE = 3
    }

    init {
        // Do basic input validation. Core will validate the rest when the query is run.
        // Better input validation will be available later.
        if (outerRing.size < MIN_RING_SIZE) {
            throw IllegalArgumentException("The outer ring requires at least $MIN_RING_SIZE points: ${outerRing.size}")
        }
        if (outerRing.first() != outerRing.last()) {
            throw IllegalArgumentException("First and last point in the outer ring must be the same: ${outerRing.first()} vs. ${outerRing.last()} ")
        }
        holes.forEachIndexed { i, ring ->
            if (ring.size < MIN_RING_SIZE) {
                throw IllegalArgumentException("The inner ring at index $i requires at least $MIN_RING_SIZE points: ${ring.size}")
            }
            if (ring.first() != ring.last()) {
                throw IllegalArgumentException("First and last point in the inner ring at index $i must be the same: ${outerRing.first()} vs. ${outerRing.last()} ")
            }
        }
    }

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
