package io.realm.prototype

import io.realm.MutableRealm
import io.realm.RealmObject
import io.realm.realmListOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

//<editor-fold desc="Public Query API">

/**
 * Add `query` method to Realm.
 * Add reified variant.
 */
interface Realm {
    fun <T : RealmObject> query(clazz: KClass<T>, query: String = "TRUEPREDICATE"): RealmQuery<T>

    suspend fun <R> write(block: MutableRealm.() -> R): R
}
inline fun <reified T: RealmObject> Realm.query(query: String = "TRUEPREDICATE"): RealmQuery<T> { TODO() }

/**
 * Add `query` method to MutableRealm
 * Add reified variant.
 */
interface MutableRealm {
    fun <T : RealmObject> query(clazz: KClass<T>, query: String = "TRUEPREDICATE"): RealmQuery<T>
}
inline fun <reified T: RealmObject> MutableRealm.filter(query: String = "TRUEPREDICATE"): RealmQuery<T> { TODO() }

/**
 * Add `query` method to RealmResults
 */
interface RealmResults<E>: List<E> {
    fun query(query: String = "TRUEPREDICATE"): RealmQuery<E>
}

/**
 * Add `query` method to RealmResults
 */
interface RealmList<E>: MutableList<E> {
    fun query(query: String = "TRUEPREDICATE"): RealmQuery<E>
}

enum class Sort {
    ASCENDING,
    DESCENDING
}

// TODO: Query must ONLY be evaluated in the terminal "execute" methods. We need to check Core implementation.
interface RealmQuery<E>: RealmElementQuery<E> {
    fun query(filter: String, vararg arguments: Any?): RealmQuery<E>
    fun sort(field: String, sortOrder: Sort = Sort.ASCENDING): RealmQuery<E>
    fun sort(fieldName1: String, sortOrder1: Sort, fieldName2: String, sortOrder2: Sort): RealmQuery<E>
    fun sort(fieldNames: Array<String>, sortOrders: Array<Sort>): RealmQuery<E>
    fun distinct(field: String): RealmQuery<E>
    // FIXME Is there a better way to force the constraints here. Restricting RealmQuery<E> to
    //  RealmObjects would help, but would prevent this class from being used by primitive queries.
    //  We need to investigate what other SDK's do here
    fun <E: RealmObject> first(): RealmSingleQuery<E>

    fun <T: Any> min(property: String, type: KClass<T>): RealmScalarQuery<T>
    fun <T: Any> max(property: String, type: KClass<T>): RealmScalarQuery<T>
    fun <T: Any> sum(property: String, type: KClass<T>): RealmScalarQuery<T>
    fun <T: Any> average(property: String, type: KClass<T>): RealmScalarQuery<T>
    fun count(): RealmScalarQuery<Long>
}

inline fun <T: RealmObject> RealmQuery<out T>.first(): RealmSingleQuery<T> = TODO()
inline fun <reified T> RealmQuery<out Any>.min(property: String): RealmScalarQuery<T> = TODO()
inline fun <reified T> RealmQuery<out Any>.max(property: String): RealmScalarQuery<T> = TODO()
inline fun <reified T> RealmQuery<out Any>.sum(property: String): RealmScalarQuery<T> = TODO()
inline fun <reified T> RealmQuery<out Any>.average(property: String): RealmScalarQuery<T> = TODO()

/**
 * Queries returning a RealmResults
 */
interface RealmElementQuery<E> {
    fun find(): RealmResults<E>
    fun asFlow(): Flow<RealmResults<E>>
    // When fine-grained notifications are merged
    // fun asFlow(): Flow<ListChange<RealmResults<E>>>

}

/**
 * Queries returning a single object.
 * NOTE: The interaction with primitive queries might be a bit akward
 */
interface RealmSingleQuery<E: RealmObject> {
    fun find(): E
    fun asFlow(): Flow<E>
    // When fine-grained notifications are merged
    // fun asFlow(): Flow<ObjectChange<E>>
}

/**
 * Queries that return scalar values. We cannot express in the type-system which scalar values
 * we support, so this checks must be done by the compiler plugin or at runtime.
 */
interface RealmScalarQuery<E> {
    fun find(): E
    // These will require a few hacks as Core changelisteners do not support these currently.
    // Easy work-around is just calling `Results.<aggregateFunction>()` inside the NotifierThread
    fun asFlow(): Flow<E>
}

//</editor-fold>

//<editor-fold desc="Examples">
fun updateUI(vararg args: Any?) { TODO() }
class Person: RealmObject {
    var name: String = ""
    var age: Int = 0
    var birthday: Instant = Clock.System.now()
}

fun getRealm(): Realm { TODO() }
fun viewModelExamples() {
    val realm: Realm = getRealm()
    val viewModelScope = CoroutineScope(CoroutineName("CustomScope") + Dispatchers.Main)

    // Use case 1: Observe objects using a simple query for the UI
    viewModelScope.launch {
        realm.query<Person>("age > 42").asFlow()
            .collect { it: RealmResults<Person> ->
                updateUI(it)
            }
    }

    // Use case 2: Observe objects using an advanced query for the UI
    viewModelScope.launch {
        realm.query<Person>("age > 42")
            .query("name BEGINSWITH 'John'")
            .distinct("age")
            .sort("name")
            .asFlow()
            .collect { it: RealmResults<Person> ->
                updateUI(it)
            }
    }

    // Use case 3a: Observe scalar values for the UI. Fast version (part of the query)
    viewModelScope.launch {
        realm.query<Person>("age BEGINSWITH 'John'")
            .max<Int>("age")
            .asFlow()
            .collect { it: Int ->
                updateUI(it)
            }
    }

    // Use case 3b: Observe scalar values for the UI. Slower version (Realm code, but part of the mapping)
    // NOTE: Not supported in initial API
//    viewModelScope.launch {
//        realm.query<Person>("name BEGINSWITH 'John'")
//            .asFlow()
//            .map { it: RealmResults<Person> ->
//                withContext(Dispatchers.Default) {
//                    it.max("age", Int::class)
//                }
//            }
//            .collect { it: Int ->
//                updateUI(it)
//            }
//    }

    // Use case 3c: Observe scalar values for the UI. Slowest version using Kotlin stdlib
    viewModelScope.launch {
        realm.query<Person>("name BEGINSWITH 'John'")
            .asFlow()
            .map { it: RealmResults<Person> ->
                it.maxOf { person -> person.age }
            }
            .collect { it: Int ->
                updateUI(it)
            }
    }

    // Use case 4: Combine Flows of Objects and Scalars
    viewModelScope.launch {
        val flow1: Flow<RealmResults<Person>> = realm.query<Person>("name BEGINSWITH 'Jane'").asFlow()
        val flow2: Flow<Float> = realm.query<Person>().average("age", Float::class).asFlow()

        // Combing the results into a different output where listOf("Name - Age (Average Age)")
        flow1.combine(flow2) { f1: RealmResults<Person>, f2: Float ->
            f1.map {
              "${it.name} - ${it.age} ($f2)"
            }
        }.collect { it: List<String> ->
            updateUI(it)
        }
    }

    // Use case 5: Running queries by accident on the UI thread.
    // There is no guard against this unless we introduce `RealmConfiguration.allowQueriesOnMainThread()`
    // We should probably do that.
    viewModelScope.launch {
        val results: RealmResults<Person> = realm.query<Person>("age > 42").find()
        val count: Long = realm.query<Person>().count().find()
        updateUI(results, count)
    }

    // Use case 6: Observing the first match to a query
    viewModelScope.launch {
        realm.query<Person>("id == 42")
            .first<Person>() // FIXME: See discussion in RealmQuery class
            .asFlow()
            .collect { it: Person ->
                updateUI(it)
            }
    }

}

fun writeExamples() {
    val realm: Realm = getRealm()
    val scope = CoroutineScope(CoroutineName("CustomScope"))

    scope.launch {
        // Queries in MutableRealm use the same API as reads. Only difference is that any method
        // that isn't a blocking method will throw an exception. There doesn't seem to be an easy
        // way to avoid this using the type system.
        realm.write {
            // Use case 1: Standard simple object queries
            val results1: RealmResults<Person> = filter<Person>("age > 42").find()

            // Use case 2: More advanced object queries
            val results2: RealmResults<Person> = filter<Person>("age > 42")
                .query("name BEGINSWITH 'John'")
                .sort("name")
                .find()

            // Use case 3: Selecting first match
            val person: Person? = filter<Person>("age > 42")
                .query("name BEGINSWITH 'John'")
                .sort("name")
                .find()
                .firstOrNull()

            // Use case 4: Scalar queries
            val maxAge: Long = filter<Person>().max("age", Long::class).find()

            // Use case 5: Scalar queries with type conversion. By asking users to provide the type
            // we should also do conversions for standard types, e.g. casting numbers, string/int
            // conversion, Date conversion
            val youngestBirthday: Long = filter<Person>().max<Long>("birthday").find()
            val maxAgeAsDouble: Double = filter<Person>().max<Double>("age").find()

            // Use case 6: Observing queries are not supported in writes.
            filter<Person>().asFlow() // Will throw exception
            filter<Person>().max<Long>("age").asFlow() // Will throw exception
        }
    }
}
//</editor-fold>


