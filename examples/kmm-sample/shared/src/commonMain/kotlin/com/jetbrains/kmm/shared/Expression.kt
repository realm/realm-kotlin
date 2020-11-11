package com.jetbrains.kmm.shared

import io.realm.runtimeapi.RealmModel
import io.realm.runtimeapi.RealmObject

@RealmObject
class Expression : RealmModel {
    var string : String = ""
}
