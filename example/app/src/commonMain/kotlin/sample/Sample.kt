package sample

import realm.*

expect class Sample() {
    fun checkMe(): Int
}

expect object Platform {
    val name: String
}

// 1. setup test env to run on iOS
// 2. implement Model/accessor/schema logic

// For the schema use Kotlin Compiler to generate two proxies one for K/N which uses C-interop to access pointer for accessors
// and one using JNI for Android (some similar code logic could live in common)
// start by designing what you expect directly from the Kotlin Compiler Plugin, by writing down the expected generated class

// Frozen query should return data classes

fun hello(): String = "Hello from ${Platform.name}"

fun createRealmDB(name: String, schema: String) {
//    val schema = " { name: 'Person', properties: { name: 'string', age: 'int' } } "
    val realmconfiguration : RealmConfiguration = RealmConfiguration.Builder().name(name).build()
//    val realm = Realm().open(name, schema)
    val realm = Realm.open(realmconfiguration)

    println("________ createRealmDB ____")

    realm.registerListener {
        println("________ NOTIFICATION RECEIVED ____")
    }

    realm.beginTransaction()
    println("________ BEGIN TRANSACTION____")

    var dog : Dog = realm.save(DogProxy())
    dog.age = 42
    dog.name = "Akamaru"
    realm.commitTransaction()

    println("________ COMMIT TRANSACTION____")
    println("________ I have (name = ${dog.name} with age = ${dog.age}____")


}

class Proxy {
    fun proxyHello() = hello()
}

fun main() {
    println(hello())
}