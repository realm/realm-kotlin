package io.realm.internal.query

import io.realm.RealmObject
import io.realm.asFlow
import io.realm.equalsTo
import io.realm.internal.Mediator
import io.realm.internal.Observable
import io.realm.internal.RealmReference
import io.realm.internal.RealmResultsImpl
import io.realm.internal.Thawable
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.link
import io.realm.notifications.Cancellable
import io.realm.notifications.ListChange
import io.realm.notifications.ObjectChange
import io.realm.notifications.UpdatedList
import io.realm.query.RealmSingleQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlin.reflect.KClass

internal class SingleQuery<E : RealmObject> constructor(
    private val realmReference: RealmReference,
    private val queryPointer: NativePointer,
    private val clazz: KClass<E>,
    private val mediator: Mediator
) : RealmSingleQuery<E>, Thawable<Observable<RealmResultsImpl<E>, ListChange<RealmResultsImpl<E>>>> {

    override fun find(): E? {
        val link = RealmInterop.realm_query_find_first(queryPointer) ?: return null
        val model = mediator.createInstanceOf(clazz)
        model.link(realmReference, mediator, clazz, link)
        @Suppress("UNCHECKED_CAST")
        return model as E
    }

    override fun asFlow(): Flow<ObjectChange<E>> {
        realmReference.checkClosed()

        var head: E? = null
        var headFlow: Cancellable? = null

        return realmReference.owner.registerObserver(this)
            .filter { listChange: ListChange<RealmResultsImpl<E>> ->
                // Denotes when to subscribe to the next object:
                // A change on the head of the list, and it is not the same as the previous head
                val newHead: E? = listChange.list.firstOrNull()

                (newHead != null && !newHead.equalsTo(head)).also {
                    head = newHead
                }
            }.flatMapMerge { listChange ->
                // Don't close the flow if the head was removed
                if (listChange !is UpdatedList<*> || !listChange.deletions.contains(0))
                    headFlow?.cancel()

                listChange.list
                    .first()
                    .asFlow()
                    .also {
                        headFlow = it as Cancellable
                    }
            }
    }

    /**
     * Thaw the frozen query result, turning it back into a live, thread-confined RealmResults.
     * The results object is then used to fetch the object with index 0, which can be `null`.
     */
    override fun thaw(liveRealm: RealmReference): RealmResultsImpl<E> =
        thawResults(liveRealm, RealmInterop.realm_query_find_all(queryPointer), clazz, mediator)
}
