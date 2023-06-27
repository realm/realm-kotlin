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
package io.realm.kotlin.types

/**
 * This class represents a rectangle on the surface of a sphere. It cannot be persisted - i.e you
 * can't declare a Realm property that is of type [GeoBox]. It can only be used as a query argument
 * for a geospatial query like this:
 *
 * ```
 * val bottomLeft = GeoPoint(latitude = 0.0, longitude = 0.0)
 * val topRight = GeoPoint(latitude = 0.0, longitude = 0.0)
 * val searchArea = GeoBox(bottomLeft, topRight)
 * val restaurants = realm.query<Restaurant>("location GEOWITHIN $searchArea").find()
 * ```
 */
public data class GeoBox(
    /**
     * The bottom left corner of the rectangle.
     */
    public val bottomLeft: GeoPoint,
    /**
     * The top right corner of the rectangle.
     */
    public val topRight: GeoPoint
) {
    /**
     * Returns the textual representation of the [GeoBox], this is also formatting it in a way
     * that makes it usable in queries, e.g.:
     *
     * ```
     * val box = GeoBox(bottomLeft = GeoPoint(0.0, 0.0), topRight = GeoPoint(10.0, 10.0))
     * val results = realm.query<Restaurant>("location GEOWITHIN $box").find()
     * ```
     */
    public override fun toString(): String {
        return "geoBox([${bottomLeft.longitude}, ${bottomLeft.latitude}], [${topRight.longitude}, ${topRight.latitude}])"
    }
}
