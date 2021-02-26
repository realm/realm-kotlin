package modules.input
import io.realm.runtimeapi.RealmModule
import io.realm.runtimeapi.RealmModel

class A : RealmModel

class B : RealmModel

class C : RealmModel

@RealmModule
class Entities

@RealmModule(A::class, C::class)
class Subset
