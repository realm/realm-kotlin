package io.realm.model

import realm.RealmModel

open class Person (open var name: String) : RealmModel() {
    constructor() : this("")
    open var age: Int = 0
}