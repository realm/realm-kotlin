package io.realm.kotlin.internal.query

import io.realm.kotlin.internal.ChangeBuilder
import io.realm.kotlin.internal.CoreNotifiable
import io.realm.kotlin.internal.LiveRealm
import io.realm.kotlin.internal.Mediator
import io.realm.kotlin.internal.Notifiable
import io.realm.kotlin.internal.RealmResultsImpl
import io.realm.kotlin.internal.ResultChangeBuilder
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.interop.NativePointer
import io.realm.kotlin.internal.interop.RealmResultsT
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.types.BaseRealmObject
import kotlin.reflect.KClass

internal class QueryResultNotifiable<E : BaseRealmObject>(
    val results: NativePointer<RealmResultsT>,
    val classKey: ClassKey,
    val clazz: KClass<E>,
    val mediator: Mediator
) : Notifiable<RealmResultsImpl<E>, ResultsChange<E>> {
    override fun coreObservable(liveRealm: LiveRealm): CoreNotifiable<RealmResultsImpl<E>, ResultsChange<E>>? {
        return thawResults(liveRealm.realmReference, results, classKey, clazz, mediator)
    }

    override fun changeBuilder(): ChangeBuilder<RealmResultsImpl<E>, ResultsChange<E>> {
        return ResultChangeBuilder()
    }
}
