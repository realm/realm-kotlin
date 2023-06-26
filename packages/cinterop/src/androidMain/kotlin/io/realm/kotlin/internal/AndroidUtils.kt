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
@Suppress("MagicNumber")
fun loadAndroidNativeLibs(context: Context, version: String) {
    // Only use Relinker below API 23, since all bugs it fixes are only present there.
    // Also, see if this might fix https://github.com/realm/realm-kotlin/issues/1202
    if (android.os.Build.VERSION.SDK_INT < 23) {
        ReLinker.loadLibrary(context, "realmc", version)
    } else {
        System.loadLibrary("realmc")
    }
}
