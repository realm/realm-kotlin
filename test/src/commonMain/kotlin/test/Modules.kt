package test

import io.realm.RealmModule
import io.realm.RealmObject

class A : RealmObject<A>

class B : RealmObject<A>

class C : RealmObject<A>

@RealmModule
class Entities

@RealmModule(A::class, C::class)
class Subset
