package io.realm.kotlin.internal

import android.content.Context
import com.getkeepsafe.relinker.ReLinker

/**
 * Manually load the Android native libs. Must be called before any methods on RealmInterop is
 * called. This is done as part of the `RealmInitializer` class that is controlled by Jetpack
 * Startup library.
 *
 * On JVM and Native, this will happen automatically when first loading the RealmInterop class.
 */
fun loadAndroidNativeLibs(context: Context, version: String) {
    ReLinker.loadLibrary(context, "realmc", version)
}