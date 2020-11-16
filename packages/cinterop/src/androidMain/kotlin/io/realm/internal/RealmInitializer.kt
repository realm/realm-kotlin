package io.realm.internal

import android.content.Context
import androidx.startup.Initializer

/**
 * An **initializer** to allow Realm to access context properties.
 *
 * It is highly discouraged to do blocking operation as part of the app's startup sequence, but if Realm is used from
 * an [Initializer], that initializer needs to depend on the [RealmInitializer].
 */
class RealmInitializer : Initializer<Context> {

    companion object {
        // FIXME We should not store the context as it conflicts with live refresh in Android
        //  Studio. Just grab required fields instead and maybe store them directly in
        //  PlatformHelper.
        lateinit var context: Context
    }

    override fun create(context: Context): Context {
        RealmInitializer.context = context
        return context
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> {
        return mutableListOf()
    }
}
