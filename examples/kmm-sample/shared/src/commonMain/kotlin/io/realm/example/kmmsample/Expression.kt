package io.realm.example.kmmsample

import io.realm.runtimeapi.RealmModel
import io.realm.runtimeapi.RealmObject

@RealmObject
class Expression : RealmModel {
    var expressionString: String = ""
}
