package io.realm.internal

import android.content.Context
import androidx.startup.Initializer

class RealmInitializer : Initializer<Context> {

    companion object {
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
