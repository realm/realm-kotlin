// /*
// * Copyright 2023 Realm Inc.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
// package io.realm.kotlin.internal.geo
//
// import io.realm.kotlin.annotations.ExperimentalGeoSpatialApi
//
// /**
// * This class represents an equatorial distance that can be used in geospatial queries like those
// * represented by a [GeoCircle].
// */
// @ExperimentalGeoSpatialApi
// public data class Distance private constructor(
//    /**
//     * The angular distance.
//     */
//    private val radians: Double
// ) {
//
//    init {
//        if (radians < 0) {
//            throw IllegalArgumentException("Negative distance is now allowed: $radians")
//        }
//    }
//
//    public companion object {
//        /**
//         * An approximation of the radius of the earth. This is used to convert between radians
//         * and kilometers or miles.
//         *
//         * See https://en.wikipedia.org/wiki/Earth_radius for further information.
//         */
//        public const val EARTH_RADIUS_KM: Double = 6371.0
//
//        /**
//         * The constant used convert between kilometers and the internationale (or statute) mile.
//         */
//        public const val KM_PR_MILE: Double = 1.609344
//
//        /**
//         * Create a [Distance] object from kilometers.
//         */
//        public fun fromKilometers(km: Double): Distance {
//            return when (km) {
//                0.0 -> Distance(0.0)
//                else -> Distance(km / EARTH_RADIUS_KM)
//            }
//        }
//
//        /**
//         * Create a [Distance] object from miles.
//         */
//        public fun fromMiles(miles: Double): Distance {
//            return when (miles) {
//                0.0 -> Distance(0.0)
//                else -> Distance((miles * KM_PR_MILE) / EARTH_RADIUS_KM)
//            }
//        }
//
//        /**
//         * Create a [Distance] object from radians.
//         */
//        public fun fromRadians(radians: Double): Distance { return Distance(radians) }
//
//        /**
//         * Create a [Distance] object from degrees.
//         */
//        public fun fromDegrees(degrees: Double): Distance { return Distance(radians) }
//    }
//
//    /**
//     * Returns the distance in radians.
//     */
//    public val inRadians: Double
//        get() = radians
//
//    /**
//     * Returns the distance in kilometers.
//     */
//    public val inKilometers: Double
//        get() {
//            val result = radians * EARTH_RADIUS_KM
//            return when {
//                radians == 0.0 -> 0.0
//                result < radians -> Double.POSITIVE_INFINITY
//                else -> result
//            }
//        }
//
//    /**
//     * Returns the distance in miles.
//     */
//    public val inMiles: Double
//        get() {
//            val result = radians * EARTH_RADIUS_KM / KM_PR_MILE
//            return when {
//                radians == 0.0 -> 0.0
//                result < radians -> Double.POSITIVE_INFINITY
//                else -> result
//            }
//        }
// }
