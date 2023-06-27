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
 * This class represents a circle on the surface of a sphere. It cannot be persisted - i.e you
 * can't declare a Realm property that is of type [GeoCircle]. It can be only used as a query
 * argument for a geospatial query like this:
 *
 * ```
 * val newYork = GeoPoint(latitude = 40.730610, longitude = -73.935242)
 * val searchArea = GeoCircle(center = newYork, radius = Distance.fromMiles(2.0))
 * val restaurants = realm.query<Restaurant>("location GEOWITHIN $searchArea").find()
 * ```
 */
public data class GeoCircle(
    /**
     * Center of the circle.
     */
    public val center: GeoPoint,
    /**
     * Radius of the circle. Distance cannot be a negative number
     */
    public val radius: Distance
) {
    init {
        if (radius.inRadians() < 0) {
            throw IllegalArgumentException("A negative radius is not allowed: $radius")
        }
    }

    /**
     * Returns the textual representation of the [GeoCircle], this is also formatting it in a way
     * that makes it usable in queries, e.g.:
     *
     * ```
     * val circle = GeoCircle(center = GeoPoint(0.0, 0.0), radius = Distance.fromKilometers(10.0))
     * val results = realm.query<Restaurant>("location GEOWITHIN $circle").find()
     */
    public override fun toString(): String {
        return "geoCircle([${center.longitude}, ${center.latitude}], ${radius.inRadians()})"
    }
}
