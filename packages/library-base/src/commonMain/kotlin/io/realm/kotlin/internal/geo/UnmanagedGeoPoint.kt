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

@OptIn(ExperimentalGeoSpatialApi::class)
internal data class UnmanagedGeoPoint(
    override val latitude: Double,
    override val longitude: Double
) : GeoPoint {
    private companion object {
        const val MIN_LATITUDE = -90.0
        const val MAX_LATITUDE = 90.0
        const val MIN_LONGITUDE = -180.0
        const val MAX_LONGITUDE = 180.0
    }
    init {
        if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
            throw IllegalArgumentException("Latitude is outside the valid range -90 <= lat <= 90: $latitude")
        }
        if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            throw IllegalArgumentException("Longitude is outside the valid range -180 <= lat <= 180: $longitude")
        }
    }
}
