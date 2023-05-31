package io.realm.kotlin.types

/**
 * TODO
 */
public data class GeoBox(
    /**
     * TODO
     */
    public val bottomLeftCorner: GeoPoint,
    /**
     * TODO
     */
    public val topRightCorner: GeoPoint
) {
    /**
     * Returns the textual representation of a GeoBox, this is also formatting in a a way
     * that makes it usable in queries, e.g.:
     *
     * ```
     * val box = GeoBox(bottomLeftCorner = GeoPoint(0.0, 0.0), topRightCorner = GeoPoint(10.0, 10.0))
     * val results = realm.query<Restaurant>("location GEOWITHIN $box").find()
     * ```
     */
    public override fun toString(): String {
        return "geoBox([${bottomLeftCorner.longitude}, ${bottomLeftCorner.latitude}], [${topRightCorner.longitude}, ${topRightCorner.latitude}])"
    }

}

