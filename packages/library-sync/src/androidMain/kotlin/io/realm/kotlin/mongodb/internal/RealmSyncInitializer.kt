/*
 * Copyright 2021 Realm Inc.
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
package io.realm.kotlin.mongodb.internal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.NetworkRequest
import android.os.Build
import androidx.startup.Initializer
import io.realm.kotlin.internal.RealmInitializer
import io.realm.kotlin.log.RealmLog

/**
 * An **initializer** for Sync specific functionality that does not fit into the `RealmInitializer`
 * in cinterop.o allow Realm to access context properties.
 */
class RealmSyncInitializer : Initializer<Context> {

    companion object {
        @Suppress("DEPRECATION") // Should only be called below API 21
        fun isConnected(cm: ConnectivityManager?): Boolean {
            return cm?.let {
                val networkInfo: NetworkInfo? = cm.activeNetworkInfo
                networkInfo != null && networkInfo.isConnectedOrConnecting || isEmulator()
            } ?: true
        }

        // Credit: http://stackoverflow.com/questions/2799097/how-can-i-detect-when-an-android-application-is-running-in-the-emulator
        fun isEmulator(): Boolean {
            return Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                "google_sdk" == Build.PRODUCT
        }
    }

    @Suppress("invisible_member", "invisible_reference")
    override fun create(context: Context): Context {
        val result: Int = context.checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
        if (result == PackageManager.PERMISSION_GRANTED) {
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
                // There has been a fair amount of changes and deprecations with regard to how to listen
                // to the network status. ConnectivityManager#CONNECTIVITY_ACTION was deprecated in API 28
                // but ConnectivityManager.NetworkCallback became available a lot sooner in API 21, so
                // we default to this as soon as possible.
                //
                // On later versions of Android (need reference), these callbacks will also only trigger
                // if the app is in the foreground.
                //
                // The current implementation is a best-effort in detecting when the network is available
                // again.
                //
                // See https://developer.android.com/training/basics/network-ops/reading-network-state
                // See https://developer.android.com/reference/android/net/ConnectivityManager#CONNECTIVITY_ACTION
                // See https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP /* 21 */) {
                    val request = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP /* 23 */) {
                        request.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    }
                    RealmLog.info("Register ConnectivityManager network callbacks")
                    connectivityManager?.registerNetworkCallback(
                        request.build(),
                        object : NetworkCallback() {
                            override fun onAvailable(network: Network) {
                                NetworkStateObserver.notifyConnectionChange(true)
                            }

                            override fun onUnavailable() {
                                NetworkStateObserver.notifyConnectionChange(false)
                            }
                        }
                    )
                } else {
                    RealmLog.info("Register BroadcastReceiver connectivity callbacks")
                    @Suppress("DEPRECATION")
                    context.registerReceiver(
                        object : BroadcastReceiver() {
                            override fun onReceive(context: Context?, intent: Intent?) {
                                val isConnected: Boolean = isConnected(connectivityManager)
                                NetworkStateObserver.notifyConnectionChange(isConnected)
                            }
                        },
                        IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
                    )
                }
            } catch (ex: Exception) {
                RealmLog.warn("Something went wrong trying to register a network state listener: $ex")
            }
        } else {
            RealmLog.warn("It was not possible to register a network state listener. " +
                    "ACCESS_NETWORK_STATE was not granted.")
        }
        return context
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> {
        return mutableListOf(RealmInitializer::class.java)
    }
}
