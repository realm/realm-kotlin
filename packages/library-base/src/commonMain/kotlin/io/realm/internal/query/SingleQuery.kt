package io.realm.internal.query

import io.realm.RealmObject
import io.realm.asFlow
import io.realm.hasSameObjectKey
import io.realm.internal.Mediator
import io.realm.internal.Observable
import io.realm.internal.RealmReference
import io.realm.internal.RealmResultsImpl
import io.realm.internal.Thawable
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.link
import io.realm.notifications.Cancellable
import io.realm.notifications.InitialResults
import io.realm.notifications.PendingObjectImpl
import io.realm.notifications.QueryObjectChange
import io.realm.notifications.ResultsChange
import io.realm.notifications.UpdatedResults
import io.realm.query.RealmSingleQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlin.reflect.KClass

internal class SingleQuery<E : RealmObject> constructor(
    private val realmReference: RealmReference,
    private val queryPointer: NativePointer,
    private val clazz: KClass<E>,
    private val mediator: Mediator
) : RealmSingleQuery<E>, Thawable<Observable<RealmResultsImpl<E>, ResultsChange<E>>> {

    override fun find(): E? {
        val link = RealmInterop.realm_query_find_first(queryPointer) ?: return null
        val model = mediator.createInstanceOf(clazz)
        model.link(realmReference, mediator, clazz, link)
        @Suppress("UNCHECKED_CAST")
        return model as E
    }

    /**
     * Because Core does not support subscribing to the head element of a query this feature
     * must be shimmed.
     *
     * This [QueryObjectChange] flow is achieved by flat mapping and tracking the flow of the head element.
     *
     * If the head element is replaced by a new one, then we cancel the previous flow and subscribe to the new.
     * If the head element is deleted, the flow does not need to be cancelled but we subscribe to the
     * new head if any.
     * If there is an update, we ignore it, as the object flow would automatically emit the event.
     */
    override fun asFlow(): Flow<QueryObjectChange<E>> {
        realmReference.checkClosed()

        var head: E? = null
        var headFlow: Cancellable? = null

        return realmReference.owner.registerObserver(this)
            .filter { resultsChange: ResultsChange<E> ->
                // This filter prevents flat mapping an object flow if the object is the same.
                val newHead: E? = resultsChange.list.firstOrNull()

                val isSameObject = newHead != null && !newHead.hasSameObjectKey(head)
                val pendingObject = resultsChange is InitialResults<E> && resultsChange.list.isEmpty()

                (isSameObject || pendingObject).also {
                    head = newHead
                }
            }.flatMapMerge { resultsChange ->
                // Don't close the flow if the head was removed
                if (resultsChange !is UpdatedResults<*> || !resultsChange.deletions.contains(0))
                    headFlow?.cancel()

                if (resultsChange is InitialResults<E> && resultsChange.list.isEmpty()) {
                    flowOf(PendingObjectImpl())
                } else {
                    resultsChange.list
                        .first()
                        .asFlow()
                        .also {
                            headFlow = it as Cancellable
                        }
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
