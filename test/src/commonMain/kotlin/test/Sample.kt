package test
import io.realm.runtimeapi.RealmModel
import io.realm.runtimeapi.RealmObject

@RealmObject
class Sample: RealmModel {
    var name: String = "foo"
}
