package io.realm.internal.query

import io.realm.RealmObject
import io.realm.internal.ElementResults
import io.realm.internal.Mediator
import io.realm.internal.RealmReference
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import kotlin.reflect.KClass

internal fun <T : RealmObject> thawResults(
    liveRealm: RealmReference,
    resultsPointer: NativePointer,
    clazz: KClass<T>,
    mediator: Mediator
): ElementResults<T> {
    val liveResultPtr = RealmInterop.realm_results_resolve_in(resultsPointer, liveRealm.dbPointer)
    return ElementResults(liveRealm, liveResultPtr, clazz, mediator)
}
