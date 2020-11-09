package io.realm.internal

import android.content.Context
import androidx.startup.Initializer

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
