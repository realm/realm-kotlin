package modules.input
import io.realm.RealmModule
import io.realm.RealmObject

class A : RealmObject

class B : RealmObject

class C : RealmObject

@RealmModule
class Entities

@RealmModule(A::class, C::class)
class Subset
