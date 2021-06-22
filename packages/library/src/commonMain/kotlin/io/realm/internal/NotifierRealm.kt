package io.realm.internal

import io.realm.BaseRealm
import io.realm.Callback
import io.realm.Cancellable
import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.interop.RealmInterop
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/**
 * Live Realm on which notifications can be registered.
 */
internal class NotifierRealm : BaseRealm {

    val errorMessage = "Flows are not implemented for Notifier Realms, use the callback API instead."

    internal constructor(configuration: RealmConfiguration, dispatcher: CoroutineDispatcher) :
        super(configuration, RealmInterop.realm_open(configuration.nativeConfig, dispatcher))

    override fun <T : RealmObject> addResultsChangeListener(
        results: RealmResults<T>,
        callback: Callback<RealmResults<T>>
    ): Cancellable {
        throw UnsupportedOperationException(errorMessage)
    }

    override fun <T : RealmObject> addListChangeListener(list: List<T>, callback: Callback<List<T>>): Cancellable {
        throw UnsupportedOperationException(errorMessage)
    }

    override fun <T : RealmObject> addObjectChangeListener(obj: T, callback: Callback<T?>): Cancellable {
        throw UnsupportedOperationException(errorMessage)
    }

    override fun <T : RealmObject> observeResults(results: RealmResults<T>): Flow<RealmResults<T>> {
        throw UnsupportedOperationException(errorMessage)
    }

    override fun <T : RealmObject> observeList(list: List<T?>): Flow<List<T?>?> {
        throw UnsupportedOperationException(errorMessage)
    }

    override fun <T : RealmObject> observeObject(obj: T): Flow<T?> {
        throw UnsupportedOperationException(errorMessage)
    }
}
