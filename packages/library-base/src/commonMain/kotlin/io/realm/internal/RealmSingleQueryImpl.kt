package io.realm.internal

import io.realm.RealmObject
import io.realm.RealmSingleQuery
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlin.reflect.KClass

/**
 * TODO : query
 */
internal class RealmSingleQueryImpl<E : RealmObject>(
    private val realmReference: RealmReference,
    private val queryPointer: NativePointer,
    private val clazz: KClass<E>,
    private val mediator: Mediator
) : RealmSingleQuery<E>, Thawable<BaseResults<E>> {

    override fun find(): E? = RealmInterop.realm_query_find_first(queryPointer)
        ?.toRealmObject(clazz, mediator, realmReference)

    override fun asFlow(): Flow<E?> {
        return realmReference.owner
            .registerObserver(this)
            .onStart { realmReference.checkClosed() }
            .map { it.first() }
    }

    override fun thaw(liveRealm: RealmReference): Observable<BaseResults<E>>? {
        val resultsPointer = RealmInterop.realm_query_find_all(queryPointer)
        val liveResults = RealmInterop.realm_results_resolve_in(resultsPointer, liveRealm.dbPointer)
        return ElementResults(liveRealm, liveResults, clazz, mediator)
    }
}
