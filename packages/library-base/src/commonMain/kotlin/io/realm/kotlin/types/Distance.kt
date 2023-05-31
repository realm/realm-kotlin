package io.realm.kotlin.types

/**
 * TODO
 */
public data class Distance private constructor(
    /**
     * TODO
     */
    public val radians: Double
) {
    public companion object {
        /**
         * TODO
         */
        public const val EARTH_RADIUS_KM: Double = 6.371

        /**
         * TODO
         */
        public const val KM_PR_MILE: Double = 1.609344

        /**
         * TODO
         */
        public fun fromKilometers(km: Double): Distance { return Distance(km / EARTH_RADIUS_KM) }
        /**
         * TODO
         */
        public fun fromMiles(miles: Double): Distance { return Distance((miles * KM_PR_MILE) / EARTH_RADIUS_KM) }
        /**
         * TODO
         */
        public fun fromRadians(radians: Double): Distance { return Distance(radians) }
    }
    /**
     * TODO
     */
    public fun asKilometers(): Double = radians * EARTH_RADIUS_KM
    /**
     * TODO
     */
    public fun asMiles(): Double = radians * EARTH_RADIUS_KM / KM_PR_MILE
}