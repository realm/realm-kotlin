package io.realm.kotlin.mongodb.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import io.realm.kotlin.internal.RealmInitializer
import io.realm.kotlin.mongodb.sync.Sync
import org.slf4j.helpers.Util

internal actual fun registerSystemNetworkObserver() {
    // Registering network state listeners are done in io.realm.kotlin.mongodb.RealmSyncInitializer
    // so we do not have to store the Android Context.
}