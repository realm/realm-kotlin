package io.realm.kotlin.entities.sync

import io.realm.kotlin.entities.Location
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import org.mongodb.kbson.ObjectId

class SyncRestaurant : RealmObject {
    @PrimaryKey
    var _id = ObjectId()
    var section: ObjectId? = null
    var location: Location? = null
}
