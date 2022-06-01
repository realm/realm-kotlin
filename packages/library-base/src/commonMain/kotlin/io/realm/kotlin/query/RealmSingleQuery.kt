package io.realm.kotlin.query

import io.realm.kotlin.Deleteable
import io.realm.kotlin.notifications.SingleQueryChange
import io.realm.kotlin.types.BaseRealmObject
import kotlinx.coroutines.flow.Flow

/**
 * Query returning a single [RealmObject] or [EmbeddedRealmObject].
 */
public interface RealmSingleQuery<T : BaseRealmObject> : Deleteable {

    /**
     * Finds the first object that fulfills the query conditions and returns it in a blocking
     * fashion.
     *
     * It is not recommended launching heavy queries from the UI thread as it may result in a drop
     * of frames or even ANRs. Use [asFlow] to obtain results of such queries asynchroneously instead.
     *
     * @return a [RealmObject] or [EmbeddedRealmObject] instance or `null` if no object matches the condition.
     */
    public fun find(): T?

    /**
     * Observes changes to the first object that fulfills the query conditions. The flow will emit
     * [SingleQueryChange] events on any changes to the first object represented by the query. The flow
     * will continue running indefinitely until cancelled.
     *
     * If subscribed on an empty query the flow will emit a [PendingObject] event to signal the query
     * is empty, it would then yield an [InitialObject] event for the first element. On a non-empty
     * list it would start emitting an [InitialObject] event for its first element.
     *
     * Once subscribed and the [InitialObject] event is observed, sequential [UpdatedObject] instances
     * would be observed if the first element is modified. If the element is deleted a [DeletedObject]
     * would be yield.
     *
     * If the first element is replaced with a new value, an [InitialObject] would be yield for the new
     * head, and would be follow with [UpdatedObject] on all its changes.
     *
     * ```
     *               ┌───────┐
     *         ┌─────┤ Start ├───┐
     *         │     └───────┘   ├────┐──────────┬─────┐
     * ┌───────▼───────┐ ┌───────▼────┴──┐ ┌─────┴─────▼───┐
     * │ PendingObject ├─► InitialObject │ │ UpdatedObject │
     * └───────────────┘ └───────▲───────┘ └───────────┬───┘
     *                           │  ┌───────────────┐  │
     *                           └──► DeletedObject ◄──┘
     *                              └───────────────┘
     * ```
     * The change calculations will run on the thread represented by
     * [RealmConfiguration.Builder.notificationDispatcher].
     *
     * @return a flow representing changes to the [RealmObject] or [EmbeddedRealmObject] resulting from
     * running this query.
     */
    public fun asFlow(): Flow<SingleQueryChange<T>>
}
