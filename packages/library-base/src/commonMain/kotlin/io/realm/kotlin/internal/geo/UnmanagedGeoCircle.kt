package io.realm.kotlin.internal.geo

import io.realm.kotlin.annotations.ExperimentalGeoSpatialApi
import io.realm.kotlin.types.geo.Distance
import io.realm.kotlin.types.geo.GeoCircle
import io.realm.kotlin.types.geo.GeoPoint

@OptIn(ExperimentalGeoSpatialApi::class)
public data class UnmanagedGeoCircle(
    public override val center: GeoPoint,
    public override val radius: Distance
) : GeoCircle {
    init {
        if (radius.inRadians < 0) {
            // Currently `Distance` does not allow negative values, but since a UDouble doesn't
            // exists, we also validate the input here, just in case.
            throw IllegalArgumentException("A negative radius is not allowed: $radius")
        }
    }

    override fun toString(): String {
        return "geoCircle([${center.longitude}, ${center.latitude}], ${radius.inRadians})"
    }
}
