package io.realm.entities.sync.flx

import io.realm.RealmObject

/**
 * Object used when testing Flexible Sync
 */
class FlexChildObject : RealmObject {
    var section: Int = 0
    var name: String = ""
}
