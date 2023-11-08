package io.realm.kotlin.internal.query

import io.realm.kotlin.ext.asFlow
import io.realm.kotlin.internal.InternalDeleteable
import io.realm.kotlin.internal.Mediator
import io.realm.kotlin.internal.Notifiable
import io.realm.kotlin.internal.Observable
import io.realm.kotlin.internal.RealmReference
import io.realm.kotlin.internal.RealmResultsImpl
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.interop.Link
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmQueryPointer
import io.realm.kotlin.internal.realmObjectReference
import io.realm.kotlin.internal.runIfManaged
import io.realm.kotlin.internal.toRealmObject
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.notifications.internal.DeletedObjectImpl
import io.realm.kotlin.notifications.internal.PendingObjectImpl
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.types.BaseRealmObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlin.reflect.KClass

internal class SingleQuery<E : BaseRealmObject> constructor(
    private val realmReference: RealmReference,
    private val queryPointer: RealmQueryPointer,
    private val classKey: ClassKey,
    private val clazz: KClass<E>,
    private val mediator: Mediator
) : RealmSingleQuery<E>, InternalDeleteable, Observable<RealmResultsImpl<E>, ResultsChange<E>> {

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
    override fun asFlow(keyPaths: List<String>?): Flow<SingleQueryChange<E>> {
        var oldHead: E? = null
        val keyPathInfo = keyPaths?.let {
            Pair(classKey, it)
        }
        return realmReference.owner.registerObserver(this, keyPathInfo)
            // Convert into flow of result head
            .map { resultChange: ResultsChange<E> -> resultChange.list.firstOrNull() }
            // Only react when head is changed
            .distinctUntilChangedBy { head: BaseRealmObject? ->
                head?.runIfManaged { RealmInterop.realm_object_get_key(this.objectPointer) }
            }
            // When head is changed issue flow that emits object changes
            .flatMapLatest { newHead ->
                val oldHeadDeleted =
                    oldHead != null && (
                        newHead == null ||
                            RealmInterop.realm_object_resolve_in(
                            oldHead!!.realmObjectReference!!.objectPointer,
                            newHead.realmObjectReference!!.owner.dbPointer
                        ) == null
                        )
                if (newHead == null) {
                    if (!oldHeadDeleted) {
                        flowOf(PendingObjectImpl())
                    } else {
                        flowOf(DeletedObjectImpl())
                    }
                } else {
                    oldHead = newHead
                    if (!oldHeadDeleted) {
                        newHead.asFlow()
                    } else {
                        newHead.asFlow().onStart { emit(DeletedObjectImpl()) }
                    }
                }
            }
    }

    /**
     * Thaw the frozen query result, turning it back into a live, thread-confined RealmResults.
     * The results object is then used to fetch the object with index 0, which can be `null`.
     */
    override fun notifiable(): Notifiable<RealmResultsImpl<E>, ResultsChange<E>> = QueryResultNotifiable(
        RealmInterop.realm_query_find_all(queryPointer),
        classKey,
        clazz,
        mediator
    )

    override fun delete() {
        // TODO C-API doesn't implement realm_query_delete_all so just fetch the result and delete
        //  that
        find()?.runIfManaged { delete() } // We can never have an unmanaged object as result of a query
    }
}
