package io.realm.internal

import io.realm.RealmObject

/**
 * Interface mimicking what is being added to model classes by the Compiler plugin
 * This is just here to make it easier to develop against the API.
 * All casts to this will be redirected towards the model class instead.
 */
internal interface RealmProxy {
    var realm: BaseRealm?
    var row: Row?
}