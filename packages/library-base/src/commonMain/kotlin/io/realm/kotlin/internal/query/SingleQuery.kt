package io.realm.kotlin.internal.query

import io.realm.kotlin.BaseRealmObject
import io.realm.kotlin.asFlow
import io.realm.kotlin.internal.InternalDeleteable
import io.realm.kotlin.internal.Mediator
import io.realm.kotlin.internal.Observable
import io.realm.kotlin.internal.RealmReference
import io.realm.kotlin.internal.RealmResultsImpl
import io.realm.kotlin.internal.Thawable
import io.realm.kotlin.internal.hasSameObjectKey
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.interop.Link
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmQueryPointer
import io.realm.kotlin.internal.runIfManaged
import io.realm.kotlin.internal.toRealmObject
import io.realm.kotlin.notifications.InitialResults
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.notifications.UpdatedResults
import io.realm.kotlin.notifications.internal.Cancellable
import io.realm.kotlin.notifications.internal.PendingObjectImpl
import io.realm.kotlin.query.RealmSingleQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlin.reflect.KClass

internal class SingleQuery<E : BaseRealmObject> constructor(
    private val realmReference: RealmReference,
    private val queryPointer: RealmQueryPointer,
    private val classKey: ClassKey,
    private val clazz: KClass<E>,
    private val mediator: Mediator
) : RealmSingleQuery<E>, InternalDeleteable, Thawable<Observable<RealmResultsImpl<E>, ResultsChange<E>>> {

    override fun find(): E? {
        val link: Link = RealmInterop.realm_query_find_first(queryPointer) ?: return null
        return link.toRealmObject(
            clazz = clazz,
            mediator = mediator,
            realm = realmReference
        )
    }

    /**
     * Because Core does not support subscribing to the head element of a query this feature
     * must be shimmed.
     *
     * This [SingleQueryChange] flow is achieved by flat mapping and tracking the flow of the head element.
     *
     * If the head element is replaced by a new one, then we cancel the previous flow and subscribe to the new.
     * If the head element is deleted, the flow does not need to be cancelled but we subscribe to the
     * new head if any.
     * If there is an update, we ignore it, as the object flow would automatically emit the event.
     */
    override fun asFlow(): Flow<SingleQueryChange<E>> {
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
                // Head was changed, cancel any active flow unless the head was deleted. In the case
                // the head was deleted the flow would emit a [DeletedObject] and terminate.
                if (resultsChange is UpdatedResults<*> && !resultsChange.deletions.contains(0))
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
        thawResults(liveRealm, RealmInterop.realm_query_find_all(queryPointer), classKey, clazz, mediator)

    override fun delete() {
        // TODO C-API doesn't implement realm_query_delete_all so just fetch the result and delete
        //  that
        find()?.runIfManaged { delete() } // We can never have an unmanaged object as result of a query
    }
}
