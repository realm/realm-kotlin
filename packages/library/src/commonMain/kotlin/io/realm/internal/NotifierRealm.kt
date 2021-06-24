package io.realm.internal

import io.realm.BaseRealm
import io.realm.RealmConfiguration
import io.realm.interop.RealmInterop
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Live Realm on which notifications can be registered.
 */
internal class NotifierRealm : BaseRealm {

    internal constructor(configuration: RealmConfiguration, dispatcher: CoroutineDispatcher) :
        super(configuration, RealmInterop.realm_open(configuration.nativeConfig, dispatcher))
}
