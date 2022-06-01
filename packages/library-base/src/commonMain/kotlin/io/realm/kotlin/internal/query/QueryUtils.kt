package io.realm.kotlin.internal.query

import io.realm.kotlin.BaseRealmObject
import io.realm.kotlin.internal.Mediator
import io.realm.kotlin.internal.RealmReference
import io.realm.kotlin.internal.RealmResultsImpl
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmResultsPointer
import kotlin.reflect.KClass

internal fun <T : BaseRealmObject> thawResults(
    liveRealm: RealmReference,
    resultsPointer: RealmResultsPointer,
    classKey: ClassKey,
    clazz: KClass<T>,
    mediator: Mediator
): RealmResultsImpl<T> {
    val liveResultPtr = RealmInterop.realm_results_resolve_in(resultsPointer, liveRealm.dbPointer)
    return RealmResultsImpl(liveRealm, liveResultPtr, classKey, clazz, mediator)
}
