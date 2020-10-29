package sample.input
import io.realm.runtimeapi.RealmObject

@RealmObject
class Sample {
    var name: String? = "Realm"
    fun dumpSchema() : String = "${Sample.`$realm$schema`()}"
}
