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
 * This class represent a point on the globe in geographical coordinates: latitude and longitude.
 * - Latitude ranges between -90 and 90 degrees, inclusive. Values above or below this range will
 *   throw an [IllegalArgumentException].
 * - Longitude ranges between -180 and 180 degrees, inclusive. Values above or below this range
 *   will throw an [IllegalArgumentException].
 *
 * This class cannot be persisted - i.e you can't declare a Realm property that is of type
 * [GeoPoint]. It is only used as a building block for other geospatial shapes such as [GeoBox],
 * [GeoPolygon] and [GeoCircle].
 */
public data class GeoPoint(
    /**
     * Latitude in degrees. Must be between -90.0 and 90.0.
     */
    public val latitude: Double,
    /**
     * Longitude in degrees. Must be between -180.0 and 180.0.
     */
    public val longitude: Double
) {
    init {
        if (latitude < -90 || latitude > 90) {
            throw IllegalArgumentException("Latitude is outside the valid range -90 <= lat <= 90: $latitude")
        }
        if (longitude < -180 || longitude > 180) {
            throw IllegalArgumentException("Longitude is outside the valid range -180 <= lat <= 180: $longitude")
        }
    }
}
