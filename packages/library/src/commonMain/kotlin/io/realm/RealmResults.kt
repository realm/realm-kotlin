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

//------------------------------------------------------------------
// Results playground
//------------------------------------------------------------------

//interface Queriable<T : RealmObject> {
//    fun query(query: String = "TRUEPREDICATE", vararg args: Any): Results<T>
//}
//
//internal interface ResultsApi<T: RealmObject> : List<T>,
//    Queriable<T>, Observable<Results<T>>, Freezable<Results<T>>
//
//class ResultsDelegate<T: RealmObject>(
//    private val realm: RealmReference,
//    private val results: NativePointer,
//    private val clazz: KClass<T>,
//    private val schema: Mediator
//) : ResultsApi<T>, AbstractList<T>() {
//
//    override val size: Int
//        get() = RealmInterop.realm_results_count(results).toInt()
//
//    override fun get(index: Int): T {
//        val link: Link = RealmInterop.realm_results_get<T>(results, index.toLong())
//        val model = schema.createInstanceOf(clazz) as RealmObjectInternal
//        model.link(realm, schema, clazz, link)
//        @Suppress("UNCHECKED_CAST")
//        return model as T
//    }
//
//    override fun query(query: String, vararg args: Any): Results<T> {
//        return Results.fromQuery(
//            realm,
//            RealmInterop.realm_query_parse(results, clazz.simpleName!!, query, *args),
//            clazz,
//            schema,
//        )
//    }
//
//    override fun observe(): Flow<Results<T>> {
//        TODO("Not yet implemented")
//    }
//
//    override fun freeze(frozenRealm: RealmReference): Results<T> {
//        TODO("Not yet implemented")
//    }
//
//    override fun thaw(liveRealm: RealmReference): Results<T> {
//        TODO("Not yet implemented")
//    }
//}
//
//class Results<T: RealmObject> private constructor(
//    private val delegate: ResultsApi<T>
//) : Observable<Results<T>> by delegate,
//    List<T> by delegate {
//
//    internal companion object {
//        internal fun <T : RealmObject> fromQuery(
//            realm: RealmReference,
//            query: NativePointer,
//            clazz: KClass<T>,
//            schema: Mediator
//        ): Results<T> {
//            val delegate =
//                ResultsDelegate(realm, RealmInterop.realm_query_find_all(query), clazz, schema)
//            return Results(delegate)
//        }
//
//        internal fun <T : RealmObject> fromResults(
//            realm: RealmReference,
//            results: NativePointer,
//            clazz: KClass<T>,
//            schema: Mediator
//        ): Results<T> {
//            val delegate = ResultsDelegate(realm, results, clazz, schema)
//            return Results(delegate)
//        }
//    }
//
//    internal fun freeze(realm: RealmReference): Results<T> {
//        return delegate.freeze(realm)
//    }
//
//    internal fun thaw(realm: RealmReference): Results<T> {
//        return delegate.thaw(realm)
//    }
//}
