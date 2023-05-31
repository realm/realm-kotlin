package io.realm.kotlin.types

/**
 * TODO
 */
public data class GeoSphere(
    /**
     * TODO
     */
    public val center: GeoPoint,
    /**
     * TODO
     */
    public val radius: Distance
) {

    /**
     * Returns the textual representation of a GeoSphere, this is also formatting in a a way
     * that makes it usable in queries, e.g.:
     *
     * ```
     * val sphere = GeoSphere(center = GeoPoint(0.0, 0.0), radius = Distance.fromKilometers(10.0))
     * val results = realm.query<Restaurant>("location GEOWITHIN $sphere").find()
     */
    public override fun toString(): String {
        return "geoSphere([${center.longitude}, ${center.latitude}], ${radius.radians})"
    }
}
