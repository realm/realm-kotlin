package io.realm.internal

import android.content.Context
import androidx.startup.Initializer
import java.io.File

/**
 * An **initializer** to allow Realm to access context properties.
 *
 * It is highly discouraged to do blocking operation as part of the app's startup sequence, but if
 * Realm is used from an [Initializer], that initializer needs to depend on the [RealmInitializer].
 */
class RealmInitializer : Initializer<Context> {

    companion object {
        // Watch out for storing things here. Could conflict with live refresh in Android Studio
        lateinit var filesDir: File
    }

    override fun create(context: Context): Context {
        filesDir = context.filesDir
        return context
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> {
        return mutableListOf()
    }
}