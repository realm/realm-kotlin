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
import io.realm.kotlin.internal.geo.UnmanagedGeoPoint

/**
 * This class represent a point on earth in geographical coordinates: latitude and longitude.
 * - Latitude ranges between -90 and 90 degrees, inclusive. Values above or below this range will
 *   throw an [IllegalArgumentException].
 * - Longitude ranges between -180 and 180 degrees, inclusive. Values above or below this range
 *   will throw an [IllegalArgumentException].
 *
 * This class cannot be persisted - i.e you can't declare a Realm property that is of type
 * [GeoPoint]. It is only used as a building block for other geospatial shapes such as [GeoBox],
 * [GeoPolygon] and [GeoCircle].
 *
 * Storing geo points in a model class is currently done using duck-typing, which means that any
 * model class with a specific "shape" can be queried as though it contained a geographical
 * location.
 *
 * The following is required:
 * - A String property with the value of "Point", i.e `var type: String = "Point"`
 * - A List containing a Longitude/Latitude pair: `var coordinates: RealmList<Double> = realmListOf()`
 *
 * The recommended approach is encapsulating this inside its own [EmbeddedRealmObject], like this
 *
 * ```
 * public class Location: EmbeddedRealmObject {
 *    public constructor() // Empty constructor required by Realm. Should not be used.
 *    public constructor(latitude: Double, longitude: Double) {
 *        coordinates.apply {
 *            add(longitude)
 *            add(latitude)
 *        }
 *    }
 *
 *    // Name and type required by Realm
 *    private var coordinates: RealmList<Double> = realmListOf()
 *
 *    // Name and type required by Realm
 *    private var type: String = "Point"
 *
 *    @Ignore
 *    public var latitude: Double = coordinates[1]
 *
 *    @Ignore
 *    public var longitude: Double = coordinates[0]
 * }
 * ```
 *
 * This can then be used like this:
 *
 * ```
 * class Restaurant: RealmObject {
 *   var name: String = ""
 *   var location: Location? = null
 * }
 *
 * realm.write {
 *   copyToRealm(Restaurant().apply {
 *       name = "McBurger"
 *       location = Location(latitude = 40.730625, longitude = -73.93609)
 *   }
 * }
 *
 * val newYork = GeoPoint.create(latitude = 40.730610, longitude = -73.935242)
 * val searchArea = GeoCircle.create(center = newYork, radius = Distance.fromMiles(2.0))
 * val restaurants = realm.query<Restaurant>("location GEOWITHIN $searchArea").find()
 * ```
 *
 * A proper persistable GeoPoint class will be implemented in an upcoming release.
 */
@ExperimentalGeoSpatialApi
public interface GeoPoint {

    /**
     * Latitude in degrees.
     */
    public val latitude: Double

    /**
     * Longitude in degrees.
     */
    public val longitude: Double

    public companion object {

        /**
         * Creates a point on the earths surface.
         *
         * @param latitude Latitude in degrees. Must be between -90.0 and 90.0.
         * @param longitude Longitude in degrees. Must be between -180.0 and 180.0.
         * @throws IllegalArgumentException if [latitude] or [longitude] falls outside the allowed
         * values.
         */
        public fun create(latitude: Double, longitude: Double): GeoPoint {
            return UnmanagedGeoPoint(latitude, longitude)
        }
    }
}
