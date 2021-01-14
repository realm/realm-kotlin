package io.realm

import io.realm.base.BaseRealmModel
import io.realm.schema.RealmSchema
import kotlin.reflect.KClass

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
    fun <E: RealmObject> add(obj: E): E { TODO() }
    fun <E: RealmObject> addOrUpdate(obj: E, overrideSameValues: Boolean = false) { TODO() }
    fun <E: EmbeddedObject, P: RealmObject> add(obj: E, parent: P, property: String) { TODO() }

    // Deletions
    fun <E: BaseRealmModel> delete(clazz: KClass<E>) { TODO() }
    fun deleteAll() { TODO() }

    // Users should not be able to close mutable Realms.
    internal fun close() { TODO() }

    // Query methods
    fun <E : BaseRealmModel> find(frozenObject: E): E? { TODO() } // Find live object based on frozen object. Naming?
    suspend fun <E : BaseRealmModel> find(clazz: KClass<E>, primaryKey: Any): E? { TODO() }
    fun <E : BaseRealmModel> filter(clazz: KClass<E>, filter: String = ""): RealmQuery<E> { TODO() }
    fun isEmpty(): Boolean { TODO() }
}