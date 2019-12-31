package sample
import realm.RealmModel

// 1. class needs to be open
// 2. inherit from RealmModel to indicate this is a class managed by Realm
// 3. empty ctor so the code gen can instantiate the object


open class Dog (open var name: String) : RealmModel() {
    constructor() : this("")
    open var age: Int = 0
}