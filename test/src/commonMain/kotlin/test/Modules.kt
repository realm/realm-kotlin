package test

import io.realm.runtimeapi.RealmModule
import io.realm.runtimeapi.RealmObject

class A : RealmObject

class B : RealmObject

class C : RealmObject

@RealmModule
class Entities

@RealmModule(A::class, C::class)
class Subset
