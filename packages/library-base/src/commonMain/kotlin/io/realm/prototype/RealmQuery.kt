package io.realm.prototype

import io.realm.MutableRealm
import io.realm.RealmObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.reflect.KClass

//<editor-fold desc="Public Query API">

/**
 * Open Questions:
 *  - We should strive for the same method across classes to enter "query-mode". `filter` is the
 *    standard in other SDK's and used by Kotlin stdlib. Is it a problem if we overload it or
 *    should we find another name? "query", "where", "something else"?
 */

interface Realm {
    // QUESTION Similar to Cocoa, JS and Kotlin stdlib. Not a problem on Realm, but on Collections
    // people might accidentally hit the wrong override. Is that a problem?
    fun <T : RealmObject>filter(clazz: KClass<T>, query: String = "TRUEPREDICATE"): RealmQuery<T>

    suspend fun <R> write(block: MutableRealm.() -> R): R
}
inline fun <reified T: RealmObject> Realm.filter(query: String = "TRUEPREDICATE"): RealmQuery<T> { TODO() }

interface MutableRealm {
    // QUESTION Similar to Cocoa, JS and Kotlin stdlib. Not a problem on Realm, but on Collections
    // people might accidentally hit the wrong override. Is that a problem?
    fun <T : RealmObject> filter(clazz: KClass<T>, query: String = "TRUEPREDICATE"): RealmQuery<T>
}
inline fun <reified T: RealmObject> MutableRealm.filter(query: String = "TRUEPREDICATE"): RealmQuery<T> { TODO() }



interface RealmResults<E>: List<E> {
    // QUESTION Similar to Cocoa, JS and Kotlin stdlib, but people might hit accidental override.
    // Is that a problem?
    fun filter(query: String = "TRUEPREDICATE"): RealmQuery<E>

    // QUESTION Do we want to expose aggregate methods on Results. They are slower than doing it
    // as a query, but also a lot faster than using the stdlib `List.maxOf { }`?
    // If we expose them here. Lists of primitiveds might be annoying as they need a special
    // syntax ("$") instead of a property name.
    fun <E: Any> min(property: String, type: KClass<E>): E
    fun <E: Any> max(property: String, type: KClass<E>): E
    fun <E: Any> sum(property: String, type: KClass<E>): E
    fun <E: Any> average(property: String, type: KClass<E>): E
}

interface RealmList<E>: MutableList<E> {
    // QUESTION Similar to Cocoa, JS and Kotlin stdlib, but people might hit accidental override.
    // Is that a problem?
    fun filter(query: String = "TRUEPREDICATE"): RealmQuery<E>
}

enum class Sort {
    ASCENDING,
    DESCENDING
}

// QUESTION: Should fieldNames be KProperty or Strings? If KProperty, we need a different class for queries
// on DynamicRealm
// TODO We also need to support queries on primitive types, e.g. `RealmList<String>`
interface RealmQuery<E>: RealmElementQuery<E> {
    fun filter(filter: String, vararg arguments: Any?): RealmQuery<E>

    fun sort(field: String, sortOrder: Sort = Sort.ASCENDING): RealmQuery<E>
    fun sort(fieldName1: String?, sortOrder1: Sort, fieldName2: String, sortOrder2: Sort): RealmQuery<E>
    fun sort(fieldNames: Array<String>, sortOrders: Array<Sort>): RealmQuery<E>
    fun distinct(field: String): RealmQuery<E>

    // QUESTION In stdlib these are called `minOf`, `maxOf`. Do we want to adopt same naming?
    // Probably the answer will depend on what we think of `filter` vs. something else.
    // Instead of adding `minDate/maxDate` etc. I'm adding the output class to these. This is particular
    // helpful for dates where we don't have a single date type like in Java with `minDate`
    fun <E: Any> min(property: String, type: KClass<E>): RealmScalarQuery<E>
    fun <E: Any> max(property: String, type: KClass<E>): RealmScalarQuery<E>
    fun <E: Any> sum(property: String, type: KClass<E>): RealmScalarQuery<E>
    fun <E: Any> average(property: String, type: KClass<E>): RealmScalarQuery<E>
    fun count(): RealmScalarQuery<Long>
}

inline fun <reified T> RealmQuery<out Any>.min(property: String): RealmScalarQuery<T> = TODO()
inline fun <reified T> RealmQuery<out Any>.max(property: String): RealmScalarQuery<T> = TODO()
inline fun <reified T> RealmQuery<out Any>.average(property: String): RealmScalarQuery<T> = TODO()

/**
 * Queries that return elements in the collection being queried. This needs to support both
 * RealmObjects and primitive types.
 */
interface RealmElementQuery<E> {
    fun findAll(): RealmResults<E>
    fun asFlow(): Flow<RealmResults<E>>
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
        realm.filter<Person>("age > 42").asFlow()
            .collect { it: RealmResults<Person> ->
                updateUI(it)
            }
    }

    // Use case 2: Observe objects using an advanced query for the UI
    viewModelScope.launch {
        realm.filter<Person>("age > 42")
            .filter("name BEGINSWITH 'John'")
            .distinct("age")
            .sort("name")
            .asFlow()
            .collect { it: RealmResults<Person> ->
                updateUI(it)
            }
    }

    // Use case 3a: Observe scalar values for the UI. Fast version (part of the query)
    viewModelScope.launch {
        realm.filter<Person>("name BEGINSWITH 'John'")
            .max<Int>("age")
            .asFlow()
            .collect { it: Int ->
                updateUI(it)
            }
    }

    // Use case 3b: Observe scalar values for the UI. Slower version (Realm code, but part of the mapping)
    viewModelScope.launch {
        realm.filter<Person>("name BEGINSWITH 'John'")
            .asFlow()
            .map { it: RealmResults<Person> ->
                withContext(Dispatchers.Default) {
                    it.max("age", Int::class)
                }
            }
            .collect { it: Int ->
                updateUI(it)
            }
    }

    // Use case 3c: Observe scalar values for the UI. Slowest version using Kotlin stdlib
    viewModelScope.launch {
        realm.filter<Person>("name BEGINSWITH 'John'")
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
        val flow1: Flow<RealmResults<Person>> = realm.filter<Person>("name BEGINSWITH 'Jane'").asFlow()
        val flow2: Flow<Float> = realm.filter<Person>().average("age", Float::class).asFlow()

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
        val results: RealmResults<Person> = realm.filter<Person>().filter("age > 42").findAll()
        val count: Long = realm.filter<Person>().count().find()
        updateUI(results, count)
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
            val results1: RealmResults<Person> = filter<Person>("age > 42").findAll()

            // Use case 2: More advanced object queries
            val results2: RealmResults<Person> = filter<Person>("age > 42")
                .filter("name BEGINSWITH 'John'")
                .sort("name")
                .findAll()

            // Use case 3: Selecting first match
            val person: Person? = filter<Person>("age > 42")
                .filter("name BEGINSWITH 'John'")
                .sort("name")
                .findAll()
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


