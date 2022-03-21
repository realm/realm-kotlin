package io.realm.kotlin.benchmarks

import io.realm.RealmInstant
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.realmListOf

val SINGLE_SCHEMA = setOf(Entity1::class)
val SMALL_SCHEMA = setOf(
    Entity1::class,
    Entity2::class,
    Entity3::class,
    Entity4::class,
    Entity5::class
)
val LARGE_SCHEMA = setOf(
    Entity1::class,
    Entity2::class,
    Entity3::class,
    Entity4::class,
    Entity5::class,
    Entity6::class,
    Entity7::class,
    Entity8::class,
    Entity9::class,
    Entity10::class,
    Entity11::class,
    Entity12::class,
    Entity13::class,
    Entity14::class,
    Entity15::class,
    Entity16::class,
    Entity17::class,
    Entity18::class,
    Entity19::class,
    Entity20::class
)

class WithPrimaryKey : RealmObject {
    @PrimaryKey
    var stringField: String = "Realm"
    var longField: Long = 256
    var booleanField: Boolean = true
    var floatField: Float = 3.14f
    var doubleField: Double = 1.19840122
    var timestampField: RealmInstant = RealmInstant.fromEpochSeconds(100, 1000)
    var objectField: WithPrimaryKey? = null
    var objectListField: RealmList<Entity1> = realmListOf()
}

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

class Entity2 : RealmObject {
    var stringField: String = "Entity2"
    var longField: Long = 2
    var booleanField: Boolean = true
}

class Entity3 : RealmObject {
    var stringField: String = "Entity3"
    var longField: Long = 3
    var booleanField: Boolean = true
}

class Entity4 : RealmObject {
    var stringField: String = "Entity4"
    var longField: Long = 4
    var booleanField: Boolean = true
}

class Entity5 : RealmObject {
    var stringField: String = "Entity5"
    var longField: Long = 5
    var booleanField: Boolean = true
}

class Entity6 : RealmObject {
    var stringField: String = "Entity6"
    var longField: Long = 6
    var booleanField: Boolean = true
}

class Entity7 : RealmObject {
    var stringField: String = "Entity7"
    var longField: Long = 7
    var booleanField: Boolean = true
}

class Entity8 : RealmObject {
    var stringField: String = "Entity8"
    var longField: Long = 8
    var booleanField: Boolean = true
}

class Entity9 : RealmObject {
    var stringField: String = "Entity9"
    var longField: Long = 9
    var booleanField: Boolean = true
}

class Entity10 : RealmObject {
    var stringField: String = "Entity10"
    var longField: Long = 10
    var booleanField: Boolean = true
}

class Entity11 : RealmObject {
    var stringField: String = "Entity11"
    var longField: Long = 11
    var booleanField: Boolean = true
}

class Entity12 : RealmObject {
    var stringField: String = "Entity12"
    var longField: Long = 12
    var booleanField: Boolean = true
}

class Entity13 : RealmObject {
    var stringField: String = "Entity13"
    var longField: Long = 13
    var booleanField: Boolean = true
}
class Entity14 : RealmObject {
    var stringField: String = "Entity14"
    var longField: Long = 14
    var booleanField: Boolean = true
}

class Entity15 : RealmObject {
    var stringField: String = "Entity15"
    var longField: Long = 15
    var booleanField: Boolean = true
}

class Entity16 : RealmObject {
    var stringField: String = "Entity16"
    var longField: Long = 16
    var booleanField: Boolean = true
}

class Entity17 : RealmObject {
    var stringField: String = "Entity17"
    var longField: Long = 17
    var booleanField: Boolean = true
}

class Entity18 : RealmObject {
    var stringField: String = "Entity18"
    var longField: Long = 18
    var booleanField: Boolean = true
}

class Entity19 : RealmObject {
    var stringField: String = "Entity19"
    var longField: Long = 19
    var booleanField: Boolean = true
}

class Entity20 : RealmObject {
    var stringField: String = "Entity20"
    var longField: Long = 20
    var booleanField: Boolean = true
}
