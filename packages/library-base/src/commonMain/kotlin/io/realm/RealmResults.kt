package io.realm

import kotlinx.coroutines.flow.Flow

/**
 * A _Realm Result_ holds the results of querying the Realm.
 *
 * @see Realm.objects
 * @see MutableRealm.objects
 */
interface RealmResults<T : RealmObject> : List<T>, Queryable<T>, Versioned {

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
    @Suppress("SpreadOperator")
    override fun query(query: String, vararg args: Any?): RealmResults<T>

    /**
     * Observe changes to the RealmResult. If there is any change to objects represented by the query
     * backing the RealmResult, the flow will emit the updated RealmResult. The flow will continue
     * running indefinitely until canceled.
     *
     * The change calculations will on on the thread represented by [RealmConfiguration.notificationDispatcher].
     *
     * @return a flow representing changes to the RealmResults.
     */
    fun observe(): Flow<RealmResults<T>>

    /**
     * Delete all objects from this result from the realm.
     */
    fun delete()
}
