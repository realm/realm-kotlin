package test

import io.realm.runtimeapi.RealmModule
import io.realm.runtimeapi.RealmObject

@RealmObject
class A

@RealmObject
class B

@RealmObject
class C

@RealmModule
class Entities

@RealmModule(A::class, C::class)
class Subset
