package io.realm

import android.content.Context
import io.realm.annotations.ModifyWithRealmCompilerPlugin
import io.realm.internal.BaseRealm
import io.realm.internal.RealmProxy
import io.realm.internal.Row
import io.realm.internal.ProxyHelperMethods
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlin.reflect.KClass

class Realm : BaseRealm {

    // Interact with proxy objects

    fun <T : RealmObject> add(obj: T) {
        val row: Row? = internalInsert(obj)
        internalManage(obj,this, row!!)
        obj.onManaged()
    }

    @ModifyWithRealmCompilerPlugin
    private inline fun <T : RealmObject> internalInsert(obj: T): Row? {
        // This assume that we place helper methods in an companion object
        // This will be re-written to `obj.insert(obj, this)` by the compiler plugin
        return (obj as ProxyHelperMethods<T>).insert(obj, this)
    }

    @ModifyWithRealmCompilerPlugin
    private fun internalManage(model: RealmObject, realm: Realm, row: Row) {
        // `(model as RealmProxy)` will be rewritten to just `model`
        (model as RealmProxy).apply {
            this.realm = realm
            this.row = row
        }
    }

    // Queries

    // Option 1: Expose queries like Realm Java
    fun <T : RealmObject> where(clazz: T): RealmQuery<T> {
        TODO()
    }

    // Option 2: Expose queries like Cocoa/JS with 1 string based query method
    fun <T : RealmObject> objects(clazz: T) : RealmResults<T> {
        TODO();
    }

    // Unclear if we should expose change listener API's at all?
    fun observe(): Flow<Realm> {
        TODO()
    }

    // Callback/Suspend functions and naming

    // Option 1: "Migrate" Async to mean actually mean suspend
//    suspend fun executeTransactionAsync(transaction: (Realm) -> Unit) { TODO() }
//    fun executeTransaction(transaction: (Realm) -> Unit) { TODO() }

    // Option 2: JS-like
    suspend fun executeTransaction(transaction: (Realm) -> Unit) { TODO() }
    fun executeTransactionSync(transaction: (Realm) -> Unit) { TODO() }

    // Option 3: Only provide suspend function and require use of runBlocking
//    suspend fun executeTransaction(transaction: (Realm) -> Unit) { TODO() }

    // Option 4: Others?


    // Initializing Realm

    companion object {
        @JvmStatic
        fun init(context: Context) { TODO() }
        @JvmStatic
        fun getDefaultInstance(): Realm { TODO() }
    }
}