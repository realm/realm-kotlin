package io.realm

import io.realm.base.BaseRealmModel
import io.realm.example.Project
import io.realm.example.Task
import io.realm.schema.RealmSchema
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1

/**
 * [Realm] only allows access to immutable data. For that reason, a number of API's found on the
 * Realm class in Realm Java does not make sense there.
 *
 * In Realm Kotlin these API's are now available in this class. This makes the distinction
 * when you are working in a frozen context vs. a live one more clear.
 *
 * In the current API, this class functions as a wrapper for a write transaction, ie. the mutable
 * Realm is only used when a write is started. If we wanted to support both Live and Frozen Realms
 * we could choose to make a MutableRealm fully fledged allow to create independant instances of it.
 *
 *
 */
class MutableRealm { // TODO: This class does not extend Realm right now, unclear if that would make sense.

    // Copied from Realm. Annoying to have to keep these in sync?
    val configuration: RealmConfiguration = TODO()
    val schema: RealmSchema = TODO()
    val path: String = TODO("configuration.path")
    val version: Long = TODO("Check what version this is")

    // No public constructor or static factory methods as this class is only available inside a
    // write transaction.

    // No changelisteners are available inside transactions

    // Managing the transaction
    fun cancelTransaction() { TODO() }
    fun isInTransaction(): Boolean { TODO() }

    // Adding objects to Realm
    fun <E: RealmObject<E>> add(obj: E): E { TODO() }
    fun <E: RealmObject<E>> addOrUpdate(obj: E, overrideSameValues: Boolean = false) { TODO() }
    fun <E: EmbeddedRealmObject<E>, P: RealmObject<P>> add(embeddedObject: E, parent: P, property: String) { TODO() }
    fun <E: EmbeddedRealmObject<E>, P: RealmObject<P>> add(embeddedObject: E, parent: P, property: KProperty1<P, Any>) { TODO() }

    // Deletions
    fun <E: BaseRealmModel<E>> delete(clazz: KClass<E>) { TODO() }
    fun deleteAll() { TODO() }

    // Users should not be able to close mutable Realms.
    internal fun close() { TODO() }

    // Query methods
    suspend fun <E : BaseRealmModel<E>> find(clazz: KClass<E>, primaryKey: Any): E? { TODO() }
    fun <E : BaseRealmModel<E>> filter(clazz: KClass<E>, filter: String = ""): RealmQuery<E> { TODO() }
    fun isEmpty(): Boolean { TODO() }

    // Get Live object from frozen object. Looking up based on ObjKey is fast enough that it doesn't
    // have to be a suspend function.
    // QUESTION: Is there a better name? find(), thaw(), findWritable(), findLatest(), update(), something else?
    suspend fun <E : BaseRealmModel<E>> find(frozenObject: E): E? { TODO() }
}