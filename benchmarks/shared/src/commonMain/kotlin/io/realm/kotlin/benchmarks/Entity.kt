package io.realm.kotlin.benchmarks

import io.realm.RealmInstant
import io.realm.RealmObject
import io.realm.RealmList
import io.realm.realmListOf

class Entity1 : RealmObject {
    var stringField: String = "Realm"
    var longField: Long = 256
    var booleanField: Boolean = true
    var floatField: Float = 3.14f
    var doubleField: Double = 1.19840122
    var timestampField: RealmInstant = RealmInstant.fromEpochSeconds(100, 1000)
    var objectField: Entity1? = null
    var objectListField: RealmList<Entity1> = realmListOf()
}