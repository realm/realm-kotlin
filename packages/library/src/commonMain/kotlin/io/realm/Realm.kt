package io.realm

import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

class Realm private constructor(val configuration: RealmConfiguration, private val dbPointer: BindingPointer) {

    companion object {
        var defaultConfiguration: RealmConfiguration? = null
        fun openDefault() {
            TODO("Return default Realm")
        }
        fun setDefaultConfiguration(realmConfiguration: RealmConfiguration?) {
            TODO()
        }
        suspend fun openAsync(configuration: RealmConfiguration): Realm { TODO("Does coroutines make sense here?") }

        fun getLocalInstanceCount(configuration: RealmConfiguration): Int { TODO() }
        fun getGlobalInstanceCount(configuration: RealmConfiguration): Int { TODO() }
        fun deleteRealm(configuration: RealmConfiguration): Int { TODO() }
        fun writeCopyTo(configuration: RealmConfiguration): Int { TODO() }
        fun writeEncryptedCopyTo(configuration: RealmConfiguration): Int { TODO() }

        fun open(configuration: RealmConfiguration) : Realm {
            //TODO
            // IN Android use lazy property delegation init to load the shared library
            //   use the function call (lazy init to do any preprocessing before starting Realm eg: log level etc)
            //  or implement an init method which is a No-OP in iOS but in Android it load the shared library

            val schema = "[ { \"name\": \"Person\", \"properties\": { \"name\": \"string\", \"age\": \"int\"}}]" //TODO use schema Array generated from type
            val dbPointer = CInterop.openRealm(PlatformUtils.getPathOrUseDefaultLocation(configuration), schema)
            val realm = Realm(configuration, dbPointer)
            return realm
        }
    }

    fun beginTransaction() {
        CInterop.beginTransaction(dbPointer!!)
    }

    fun commitTransaction() {
        CInterop.commitTransaction(dbPointer!!)
    }

    fun cancelTransaction() {
        CInterop.cancelTransaction(dbPointer!!)
    }

    // -------------------------------------------------------------
    // First draft at porting API's
    // -------------------------------------------------------------
    // Properties
    val schema: RealmSchema
        get() { TODO() }
    val isFrozen: Boolean
        get() { TODO() }
    var isInTransaction: Boolean = false
        get() { TODO() }
    val path: String = configuration.path!! // Is realm.configuration.path enough?
    var isClosed: Boolean = false
        get() { TODO() }
    var version: Long = 0 // Schema version ... Is realm.schema.version enough?
        get() { TODO() }
    val isReadOnly: Boolean
        get() { TODO() }

    // Adding / removing objects

    // Reflection is not supported in K/N so we can't offer method like
    // inline fun <reified T : RealmModel> create() : T
    // to create a dynamically managed model. we're limited thus to persist methods
    // were we take an already created un-managed instance and return a new manageable one
    // (note since parameter are immutable in Kotlin, we need to create a new instance instead of
    // doing this operation in place)
    fun <T : RealmModel> create(type: KClass<T>) : T {
        val objectType = type.simpleName?: error("Cannot get class name")
        val managedModel = configuration.modelFactory.invoke(type)
        managedModel.objectPointer = CInterop.addObject(dbPointer!!, objectType)
        managedModel.isManaged = true
        managedModel.tableName = objectType
        return managedModel as T
    }
    fun <T : RealmModel> add(obj: T): T { TODO("Should we return the object or not if modified in place (for more fluent API's) ") }
    fun delete(type: KClass<RealmModel>) { TODO() }}
    fun deleteAll() { TODO() }
    suspend fun executeTransaction(transaction: (Realm) -> Unit) { TODO() }

    // Queries
    // There is quite a lot of disparity between SDK's here:
    // objects vs. findAll and object vs. find vs. objectForPrimaryKey
    // Unfortunately, `object` is a keyword in Kotlin. So suggestion is to follow
    // .NET which also doesn't hint at an expensive action, e.g. `findAll()` sound expensive
    // compared to `all()` and with lazy-evaluated queries, `all()` is not expensive until
    // you start touching it.

    fun <T : RealmModel> all(type: KClass<T>): RealmResults<T> { TODO() }
 // FIXME replace with `all()` (TBD)
    fun <T : RealmModel> objects(clazz : KClass<T>, query: String) : RealmResults<T> {
        val objectType = clazz.simpleName?:error("Cannot get class name") //TODO infer type from T
        // TODO check nullability of pointer and throw
        return RealmResults(
                CInterop.realmresultsQuery(dbPointer!!, objectType, query),
                clazz,
                configuration.modelFactory
        )
    }

    // See See https://github.com/realm/realm-java/issues/5179
    // Should these be suspend functions?
    suspend fun <T : RealmModel> find(type: KClass<T>, primaryKey: Unit): RealmOptional<T> { TODO() }
    suspend fun <T : RealmModel> find(type: KClass<T>, globalKey: GlobalKey): RealmOptional<T> { TODO() }

    // Listeners
    suspend fun observe(listener: (Realm) -> Unit): Flow<Realm> { TODO("Listen to changes inside a coroutine") }
    // Unsure if we need to expose these for Kotlin?
    fun addChangeListener(listener: (Realm) -> Unit) { TODO("Do we want to expose in public API?") }
    fun removeChangeListener(listener: (Realm) -> Unit) { TODO("Do we want to expose in public API?") }
    fun removeAllChangeListeners() { TODO() }

    // Other methods
    fun refresh() { TODO() }
    fun freeze() { TODO() }
    // -------------------------------------------------------------
}