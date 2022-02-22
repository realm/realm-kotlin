package io.realm.internal.query

import io.realm.RealmObject
import io.realm.internal.Mediator
import io.realm.internal.RealmReference
import io.realm.internal.RealmResultsImpl
import io.realm.internal.Thawable
import io.realm.internal.interop.ClassKey
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.link
import io.realm.query.RealmSingleQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.reflect.KClass

internal class SingleQuery<E : RealmObject> constructor(
    private val realmReference: RealmReference,
    private val queryPointer: NativePointer,
    private val classKey: ClassKey,
    private val clazz: KClass<E>,
    private val mediator: Mediator
) : RealmSingleQuery<E>, Thawable<RealmResultsImpl<E>> {

    override fun find(): E? {
        val link = RealmInterop.realm_query_find_first(queryPointer) ?: return null
        val model = mediator.createInstanceOf(clazz)
        model.link(realmReference, mediator, clazz, link)
        @Suppress("UNCHECKED_CAST")
        return model as E
    }

    override fun asFlow(): Flow<E?> {
        realmReference.checkClosed()
        return realmReference.owner.registerObserver(this)
            .map { results ->
                results.firstOrNull()
            }
    }

    /**
     * Thaw the frozen query result, turning it back into a live, thread-confined RealmResults.
     * The results object is then used to fetch the object with index 0, which can be `null`.
     */
    override fun thaw(liveRealm: RealmReference): RealmResultsImpl<E> =
        thawResults(liveRealm, RealmInterop.realm_query_find_all(queryPointer), classKey, clazz, mediator)
}
