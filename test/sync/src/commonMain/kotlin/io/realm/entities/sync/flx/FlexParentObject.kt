package io.realm.entities.sync.flx

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import kotlin.random.Random

/**
 * Object used when testing Flexible Sync
 */
class FlexParentObject() : RealmObject {
    constructor(section: String) : this() {
        this.section = section
    }
    @PrimaryKey
    var _id: String = "id-${Random.nextInt()}"
    var section: String = ""
    var name: String = ""
}
