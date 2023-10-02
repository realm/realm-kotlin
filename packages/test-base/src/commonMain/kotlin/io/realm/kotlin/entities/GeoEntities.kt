package io.realm.kotlin.entities

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
class Restaurant : RealmObject {
    var location: Location? = null
}

// Custom embedded model class for storing GeoPoints in Realm.
class Location : EmbeddedRealmObject {
    constructor(latitude: Double, longitude: Double) {
        coordinates.apply {
            add(longitude)
            add(latitude)
        }
    }
    constructor() : this(0.0, 0.0) // Empty constructor required by Realm. Should not be used.

    // Name and type required by Realm
    var coordinates: RealmList<Double> = realmListOf()

    // Name and type by Realm
    private var type: String = "Point"

    @Ignore
    var latitude: Double
        get() = coordinates[1]
        set(value) {
            coordinates[1] = value
        }

    @Ignore
    var longitude: Double
        get() = coordinates[0]
        set(value) {
            coordinates[0] = value
        }
}