package io.realm.internal.query

import io.realm.RealmObject
import io.realm.internal.Mediator
import io.realm.internal.RealmReference
import io.realm.internal.RealmResultsImpl
import io.realm.internal.interop.ClassKey
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmResultsPointer
import kotlin.reflect.KClass

internal fun <T : RealmObject> thawResults(
    liveRealm: RealmReference,
    resultsPointer: RealmResultsPointer,
    classKey: ClassKey,
    clazz: KClass<T>,
    mediator: Mediator
): RealmResultsImpl<T> {
    val liveResultPtr = RealmInterop.realm_results_resolve_in(resultsPointer, liveRealm.dbPointer)
    return RealmResultsImpl(liveRealm, liveResultPtr, classKey, clazz, mediator)
}
