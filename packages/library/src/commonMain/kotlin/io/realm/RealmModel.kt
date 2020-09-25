package io.realm

expect class BindingPointer
// use type alias to map CPointer<realm_object_t>? between Android and iOS
abstract class RealmModel(var isManaged: Boolean, var objectPointer: BindingPointer?, var tableName: String?, realm: Realm?) {
    constructor() : this (false, null, null, null)
    open fun <T : RealmModel> newInstance(): T { throw IllegalStateException("this should be implemented by Realm model classes and invoked internally to create instances without using reflection") }
}
