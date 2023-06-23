package io.realm.kotlin.types

/**
 * This class represents a rectangle on the surface of a sphere. It cannot be persisted - i.e you
 * can't declare a Realm property that is of type [GeoBox]. It can only be used as a query argument
 * for a geospatial query like this:
 *
 * ```
 * val bottomleft = GeoPoint(latitude = 0.0, longitude = 0.0)
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
     * Returns the textual representation of a GeoBox, this is also formatting in a a way
     * that makes it usable in queries, e.g.:
     *
     * ```
     * val box = GeoBox(bottomLeftCorner = GeoPoint(0.0, 0.0), topRightCorner = GeoPoint(10.0, 10.0))
     * val results = realm.query<Restaurant>("location GEOWITHIN $box").find()
     * ```
     */
    public override fun toString(): String {
        return "geoBox([${bottomLeft.longitude}, ${bottomLeft.latitude}], [${topRight.longitude}, ${topRight.latitude}])"
    }

}

