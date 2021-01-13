package io.realm

import android.content.Context

// Injected setup method specifically available on Android
// We inject it as we cannot use expect/actual for single methods in a class
// This is most likely not needed on other platform, and given that we use
// the AndroidX Startup library, calling this method should be considered
// an advanced use case
fun Realm.Companion.init(context: Context) {
    TODO("Read from properties and convert to RealmContext")
}
