package io.realm

import io.realm.internal.manage
import io.realm.interop.RealmInterop
import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmModel
import io.realm.runtimeapi.RealmModelInternal
import kotlin.reflect.KClass

// TODO API-PUBLIC Document platform specific internals (RealmInitilizer, etc.)
class Realm {
    private var dbPointer: NativePointer? = null // TODO API-INTERNAL nullable to avoid "'lateinit' modifier is not allowed on properties of primitive types"
    private lateinit var realmConfiguration: RealmConfiguration

    companion object {
        fun open(realmConfiguration: RealmConfiguration): Realm {
            // TODO API-INTERNAL
            //  IN Android use lazy property delegation init to load the shared library use the
            //  function call (lazy init to do any preprocessing before starting Realm eg: log level etc)
            //  or implement an init method which is a No-OP in iOS but in Android it load the shared library

            val realm = Realm()
            realm.realmConfiguration = realmConfiguration
            realm.dbPointer = RealmInterop.realm_open(realmConfiguration.nativeConfig)
            return realm
        }
    }

    //    fun open(dbName: String, schema: String) : Realm
    fun beginTransaction() {
        RealmInterop.realm_begin_write(dbPointer!!)
    }

    fun commitTransaction() {
        RealmInterop.realm_commit(dbPointer!!)
    }

    fun cancelTransaction() {
        TODO()
    }

    fun registerListener(f: () -> Unit) {
    }


    //    reflection is not supported in K/N so we can't offer method like
    //    inline fun <reified T : RealmModel> create() : T
    //    to create a dynamically managed model. we're limited thus to persist methods
    //    were we take an already created un-managed instance and return a new manageable one
    //    (note since parameter are immutable in Kotlin, we need to create a new instance instead of
    //    doing this operation in place)
    fun <T : RealmModel> create(type: KClass<T>): T {
        val objectType = type.simpleName ?: error("Cannot get class name")
        val managedModel = realmConfiguration.modelFactory.invoke(type) as RealmModelInternal
        val key = RealmInterop.realm_find_class(dbPointer!!, objectType)
        return managedModel.manage(
            dbPointer!!,
            type,
            RealmInterop.realm_object_create(dbPointer!!, key)
        )
    }

    fun <T: RealmModel> objects(clazz: KClass<T>): RealmResults<T> {
        return RealmResults(
            dbPointer!!,
            @Suppress("SpreadOperator") // TODO PERFORMANCE Spread operator triggers detekt
            { RealmInterop.realm_query_parse(dbPointer!!, clazz.simpleName!!, "TRUEPREDICATE") },
            clazz,
            realmConfiguration.modelFactory
        )
    }

}
