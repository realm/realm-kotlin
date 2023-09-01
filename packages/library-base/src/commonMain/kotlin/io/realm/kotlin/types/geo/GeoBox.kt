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
package io.realm.kotlin.types.geo

import io.realm.kotlin.annotations.ExperimentalGeoSpatialApi
import io.realm.kotlin.internal.geo.UnmanagedGeoBox

/**
 * This class represents a rectangle on the surface of a the earth. It cannot be persisted - i.e you
 * can't declare a Realm property that is of type [GeoBox]. It can only be used as a query argument
 * for a geospatial query like this:
 *
 * ```
 * val bottomLeft = GeoPoint.create(latitude = 5.0, longitude = 5.0)
 * val topRight = GeoPoint.create(latitude = 10.0, longitude = 10.0)
 * val searchArea = GeoBox.create(bottomLeft, topRight)
 * val restaurants = realm.query<Restaurant>("location GEOWITHIN $0", searchArea).find()
 * ```
 */
@ExperimentalGeoSpatialApi
public interface GeoBox {

    /**
     * The bottom left corner of the rectangle.
     */
    public val bottomLeft: GeoPoint

    /**
     * The top right corner of the rectangle.
     */
    public val topRight: GeoPoint

    /**
     * Returns the textual representation of the [GeoBox], this is also formatting it in a way
     * that makes it usable in queries, e.g.:
     *
     * ```
     * val box = GeoBox.create(bottomLeft = GeoPoint.create(0.0, 0.0), topRight = GeoPoint.create(10.0, 10.0))
     * val results = realm.query<Restaurant>("location GEOWITHIN $box").find()
     * ```
     */
    override fun toString(): String

    public companion object {

        /**
         * Create a [GeoBox] instance.
         *
         * @param bottomLeft The bottom left corner of the rectangle.
         * @param topRight The top right corner of the rectangle.
         */
        public fun create(bottomLeft: GeoPoint, topRight: GeoPoint): GeoBox {
            return UnmanagedGeoBox(bottomLeft, topRight)
        }

        /**
         * Create a [GeoBox] instance.
         *
         * @param top Longitude of the top boundary of the rectangle.
         * @param left Latitude of the left boundary of the rectangle.
         * @param bottom Longitude of the bottom boundary of the rectangle.
         * @param right Latitude of the right boundary of the rectangle.
         */
        public fun create(top: Double, left: Double, bottom: Double, right: Double): GeoBox =
            create(GeoPoint.create(left, bottom), GeoPoint.create(right, top))
    }
}
