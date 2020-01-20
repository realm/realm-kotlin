package io.realm

import io.realm.annotations.ModifyWithRealmCompilerPlugin
import io.realm.internal.BaseRealm
import io.realm.internal.RealmProxy
import io.realm.internal.Row


interface RealmObject  {

    // A compiler plugin will modify model classes implementing this interface so they also
    // include all properties defined by RealmProxy. But the RealmProxy interface itself is not
    // used.
    // It will also potentially replace all the `proxy` references with `this` to avoid
    // unnecessary casting.
    @Suppress("CAST_NEVER_SUCCEEDS")
    private val proxy: RealmProxy
        @ModifyWithRealmCompilerPlugin
        get() = (this as RealmProxy)

    fun isManaged(): Boolean {
        return proxy.realm != null
    }

    fun isFrozen(): Boolean {
        TODO()
    }

    fun isValid(): Boolean {
        TODO()
    }

    fun onManaged() {
        /* Called when the object changes state, override in model classes. Copied from Realm .NET */
    }
}