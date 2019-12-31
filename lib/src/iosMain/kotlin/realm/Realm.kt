package realm //TODO use io.realm

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import objectstore_wrapper.*
import platform.Foundation.*

private fun callback(name: CPointer<ByteVar>?) {
    val message = name?.toKString() ?: error("Received null in callback")
    println("Hello $message from Kotlin")
}

actual class Realm actual constructor() {
    private lateinit var dbPointer: CPointer<database_t>
    private lateinit var realmConfiguration: RealmConfiguration
    //TODO add the factory here


//    actual fun open(dbName: String, schema: String) : Realm {//TODO instead of string maybe pass in an array of object that inherit from Model so we can build the Factory that will invoke the empty ctor of the user model maybe do a swich on type then invoke by reflection? the new operator (or define a static method via the compiler that provide the new instance method or define it in the root package RealmModel the method is defined but overrided in the Proxy class to provide an actual implementation )
//        val path = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).first().toString()
//        val location = NSURL.URLWithString(path)?.URLByAppendingPathComponent("${dbName}.realm")?.filePathURL?.absoluteString?.removePrefix("file://")
//        println (">>>>>>>>> location = $location")
//        dbPointer = create(location, schema)!!
//        return this
//    }

    actual fun beginTransaction() {
        begin_transaction(dbPointer)
    }

    actual fun commitTransaction() {
        commit_transaction(dbPointer)
    }

    actual fun registerListener(f: () -> Unit) {
        println("registerListener")
        register_listner(dbPointer, "Person", staticCFunction(::callback))
        // use the token to know which callback should be forwarded wo which lambda
    }

//    actual inline fun <reified T : RealmModel> create(): T {
//        val objectPointer: CPointer<realm_object_t>? = add_object(
//            dbPointer,
//            "Person"
//        ) //TODO find a way to infer name from the type T (Mediator? that maps types to names?)
//        val model: KClass<RealmModel> = realmConfiguration.schema?.find {  it == T::class } ?: throw IllegalArgumentException("Specified type is not part of the schema")
//
//        // need to create DogProxy instance
//        val realmModel = Class.forName("${model.qualifiedName}Proxy")?.newInstance() as T //FIXME reflection
//        realmModel.objectPointer = CPointerWrapper(objectPointer!!)
//        realmModel.isManaged = true
//        realmModel.tableName = "Person"
//        return realmModel
//    }

    actual companion object {
        actual fun open(realmConfiguration: RealmConfiguration): Realm {
            val directory = realmConfiguration.path ?:
                NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).first().toString()
            val realmName = realmConfiguration.name ?: "default"

//            val location = NSURL.URLWithString(directory)?.URLByAppendingPathComponent("${realmName}.realm")
//                ?.filePathURL?.absoluteString?.removePrefix("file://")

            val location = "$directory/${realmName}.realm".removePrefix("file://")

            println(">>>>>>>>> location = $location")
            val schema = "[ { \"name\": \"Person\", \"properties\": { \"name\": \"string\", \"age\": \"int\"}}]" //TODO use schema Array generated from type

            val realm = Realm()
            realm.realmConfiguration = realmConfiguration
            realm.dbPointer = create(location, schema)!!
            return realm

        }
    }

    actual fun <T : RealmModel> save(unmanaged: T): T {
        print("save for table ${unmanaged::class.simpleName}") //FIXME
        val managedModel: T = unmanaged.newInstance()
        val objectPointer: CPointer<realm_object_t>? = add_object(
            dbPointer,
            "Person"
        )
        managedModel.objectPointer = CPointerWrapper(objectPointer!!)
        managedModel.isManaged = true
        managedModel.tableName = unmanaged.tableName
        return managedModel
    }
}