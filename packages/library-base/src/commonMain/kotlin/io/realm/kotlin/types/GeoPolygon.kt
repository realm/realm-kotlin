package io.realm.kotlin.types

/**
 * TODO
 */
public data class GeoPolygon(
    /**
     * TODO
     */
    public val outerRing: List<GeoPoint>,
    /**
     * TODO
     */
    public val holes: Iterable<List<GeoPoint>> = emptyList()
) {

    /**
     * TODO
     */
    public constructor(outerRing: List<GeoPoint>, hole: List<GeoPoint>): this(outerRing, listOf(hole))

    /**
     * Returns the textual representation of a GeoSphere, this is also formatting in a a way
     * that makes it usable in queries, e.g.:
     *
     * ```
     * val outerRing = listOf(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0), GeoPoint(1.0, 1.0), GeoPoint(0.0, 1.0))
     * val hole = listOf
     *
     * val sphere = GeoPolygon(outerRing = listOf(GeoPoint(0.0, 0.0), radius = Distance.fromKilometers(10.0))
     * val results = realm.query<Restaurant>("location GEOWITHIN $sphere").find()
     */
    public override fun toString(): String {
        return "geoPolygon(${polygonToQueryString(outerRing)}, ${holes.joinToString(separator = ", ") { hole: List<GeoPoint> ->
            polygonToQueryString(hole)
        }})"
    }

    private fun polygonToQueryString(points: List<GeoPoint>): String {
        return points.joinToString(prefix = "{", postfix = "}") { "[${it.longitude}, ${it.latitude}]" }
    }
}

