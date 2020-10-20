package io.realm

import io.realm.runtimeapi.NativePointer

internal expect class BindingPointer : NativePointer
// use type alias to map CPointer<realm_object_t>? between Android and iOS
@Suppress("UnnecessaryAbstractClass")
public abstract class RealmModel(public var isManaged: Boolean, internal var objectPointer: NativePointer?, internal var tableName: String?, public var realm: Realm?) {
    internal constructor() : this (false, null, null, null)
    internal open fun <T : RealmModel> newInstance(): T {
        throw IllegalStateException("this should be implemented by Realm model classes and invoked internally to create instances without using reflection")
    }
}
