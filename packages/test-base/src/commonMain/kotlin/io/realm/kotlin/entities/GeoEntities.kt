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
    @Suppress("UnusedPrivateMember")
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
