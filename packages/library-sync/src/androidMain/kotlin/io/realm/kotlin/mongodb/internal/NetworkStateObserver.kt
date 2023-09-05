package io.realm.kotlin.mongodb.internal

internal actual fun registerSystemNetworkObserver() {
    // Registering network state listeners are done in io.realm.kotlin.mongodb.RealmSyncInitializer
    // so we do not have to store the Android Context.
}
