package io.realm.kotlin.query

import io.realm.kotlin.Deleteable
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.types.BaseRealmObject
import kotlinx.coroutines.flow.Flow

/**
 * Query returning [RealmResults].
 */
public interface RealmElementQuery<T : BaseRealmObject> : Deleteable {

    /**
     * Finds all objects that fulfill the query conditions and returns them in a blocking fashion.
     *
     * It is not recommended launching heavy queries from the UI thread as it may result in a drop
     * of frames or even ANRs. Use [asFlow] to obtain results of such queries asynchroneously instead.
     *
     * @return a [RealmResults] instance containing matching objects. If no objects match the
     * condition, an instance with zero objects is returned.
     */
    public fun find(): RealmResults<T>

    /**
     * Finds all objects that fulfill the query conditions and returns them asynchronously as a
     * [Flow].
     *
     * Once subscribed the flow will emit a [InitialResults] event and then a [UpdatedResults] on any
     * change to the objects represented by the query backing the [RealmResults]. The flow will continue
     * running indefinitely until canceled.
     *
     * The change calculations will run on the thread represented by
     * [RealmConfiguration.Builder.notificationDispatcher].
     *
     * **It is not allowed to call [asFlow] on queries generated from a [MutableRealm].**
     *
     * @return a flow representing changes to the [RealmResults] resulting from running this query.
     */
    public fun asFlow(): Flow<ResultsChange<T>>
}
