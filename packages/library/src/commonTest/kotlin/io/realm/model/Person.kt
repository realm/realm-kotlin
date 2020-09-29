package io.realm.model

import io.realm.RealmModel

open class Person (open var name: String) : RealmModel() {
    constructor() : this("")
    open var age: Int = 0
}