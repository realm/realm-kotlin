package io.realm.example
import io.realm.runtimeapi.RealmObject

@RealmObject
class Sample {
    var name: String? = "a"
    fun dumpSchema() : String = "${Sample.schema()}"
}
