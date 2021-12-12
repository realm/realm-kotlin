package io.realm.internal.query

import io.realm.RealmObject
import io.realm.internal.Mediator
import io.realm.internal.RealmReference
import io.realm.internal.RealmResultsImpl
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import kotlin.reflect.KClass

internal fun <T : RealmObject> thawResults(
    liveRealm: RealmReference,
    resultsPointer: NativePointer,
    clazz: KClass<T>,
    mediator: Mediator
): RealmResultsImpl<T> {
    val liveResultPtr = RealmInterop.realm_results_resolve_in(resultsPointer, liveRealm.dbPointer)
    return RealmResultsImpl(liveRealm, liveResultPtr, clazz, mediator)
}
