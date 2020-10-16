package io.realm

import io.realm.interop.RealmInterop.realm_config_new
import io.realm.interop.RealmInterop.realm_open
import io.realm.runtimeapi.NativePointer
import kotlin.reflect.KClass

class Realm {
    private var dbPointer: NativePointer? = null //TODO nullable to avoid "'lateinit' modifier is not allowed on properties of primitive types"
    private lateinit var realmConfiguration: RealmConfiguration

    companion object {
        fun open(realmConfiguration: RealmConfiguration) : Realm {
            //TODO
            // IN Android use lazy property delegation init to load the shared library
            //   use the function call (lazy init to do any preprocessing before starting Realm eg: log level etc)
            //  or implement an init method which is a No-OP in iOS but in Android it load the shared library

            val schema = "[ { \"name\": \"Person\", \"properties\": { \"name\": \"string\", \"age\": \"int\"}}]" //TODO use schema Array generated from type
            val realm = Realm()
            realm.realmConfiguration = realmConfiguration
            val config = realm_config_new()
            // FIXME Add schema
            realm.dbPointer = realm_open(config)
            return realm
        }
    }
    //    fun open(dbName: String, schema: String) : Realm
    fun beginTransaction() {
         TODO()
    }

    fun commitTransaction() {
        TODO()
    }

    fun cancelTransaction() {
        TODO()
    }

    fun registerListener(f: () -> Unit) {

    }

    fun <T : RealmModel> objects(clazz : KClass<T>, query: String) : RealmResults<T> {
        val objectType = clazz.simpleName?:error("Cannot get class name") //TODO infer type from T
        // TODO check nullability of pointer and throw
        val query : NativePointer = TODO()
        return RealmResults(
            query,
            clazz,
            realmConfiguration.modelFactory
        )
    }
    //    reflection is not supported in K/N so we can't offer method like
    //    inline fun <reified T : RealmModel> create() : T
    //    to create a dynamically managed model. we're limited thus to persist methods
    //    were we take an already created un-managed instance and return a new manageable one
    //    (note since parameter are immutable in Kotlin, we need to create a new instance instead of
    //    doing this operation in place)
    fun <T : RealmModel> create(type: KClass<T>) : T {
        val objectType = type.simpleName?: error("Cannot get class name")
        val managedModel = realmConfiguration.modelFactory.invoke(type)
        managedModel.objectPointer = TODO()
        managedModel.isManaged = true
        managedModel.tableName = objectType
        return managedModel as T
    }
}
