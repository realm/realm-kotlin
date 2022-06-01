package io.realm.kotlin

import io.realm.kotlin.notifications.InitialResults
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.notifications.UpdatedResults
import kotlinx.coroutines.flow.Flow

/**
 * A _Realm Result_ holds the results of querying the Realm.
 *
 * @see Realm.objects
 * @see MutableRealm.objects
 */
public interface RealmResults<T : BaseRealmObject> : List<T>, Deleteable, Queryable<T>, Versioned {

    /**
     * Perform a query on the objects of this result using the Realm Query Language.
     *
     * See [these docs](https://docs.mongodb.com/realm-sdks/java/latest/io/realm/RealmQuery.html#rawPredicate-java.lang.String-java.lang.Object...-)
     * for a description of the equivalent realm-java API and
     * [these docs](https://docs.mongodb.com/realm-sdks/js/latest/tutorial-query-language.html)
     * for a more detailed description of the actual Realm Query Language.
     *
     * Ex.:
     *  `'color = "tan" AND name BEGINSWITH "B" SORT(name DESC) LIMIT(5)`
     *
     * @param query The query string to use for filtering and sort.
     * @param args The query parameters.
     * @return new result according to the query and query arguments.
     *
     * @throws IllegalArgumentException on invalid queries.
     */
    override fun query(query: String, vararg args: Any?): RealmResults<T>

    /**
     * Observe changes to the RealmResult. Once subscribed the flow will emit a [InitialResults]
     * event and then a [UpdatedResults] on any change to the objects represented by the query backing
     * the RealmResults. The flow will continue running indefinitely until canceled.
     *
     * The change calculations will on on the thread represented by
     * [Configuration.SharedBuilder.notificationDispatcher].
     *
     * @return a flow representing changes to the RealmResults.
     */
    public fun asFlow(): Flow<ResultsChange<T>>
}
