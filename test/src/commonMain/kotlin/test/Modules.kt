package test

import io.realm.runtimeapi.RealmModel
import io.realm.runtimeapi.RealmModule

class A : RealmModel

class B : RealmModel

class C : RealmModel

@RealmModule
class Entities

@RealmModule(A::class, C::class)
class Subset
