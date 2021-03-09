package test

import io.realm.runtimeapi.RealmObject
import io.realm.runtimeapi.RealmModule

class A : RealmObject

class B : RealmObject

class C : RealmObject

@RealmModule
class Entities

@RealmModule(A::class, C::class)
class Subset
