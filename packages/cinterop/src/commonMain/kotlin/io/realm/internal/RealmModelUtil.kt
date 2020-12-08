package io.realm.internal

import io.realm.interop.RealmInterop
import io.realm.runtimeapi.Link
import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmModel
import io.realm.runtimeapi.RealmModelInternal
import kotlin.reflect.KClass

// TODO API-INTERNAL
fun <T : RealmModel> RealmModelInternal.manage(realm: NativePointer, type: KClass<T>, objectPointer: NativePointer): T {
    this.`$realm$IsManaged` = true
    this.`$realm$Pointer` = realm
    this.`$realm$TableName` = type.simpleName
    this.`$realm$ObjectPointer` = objectPointer
    // FIXME API-LIFECYCLE Initialize actual link; requires handling of link in compiler plugin
    // this.link = RealmInterop.realm_object_as_link()
    return this as T
}

// TODO API-INTERNAL
fun <T : RealmModel> RealmModelInternal.link(realm: NativePointer, type: KClass<T>, link: Link): T {
    this.`$realm$IsManaged` = true
    this.`$realm$Pointer` = realm
    this.`$realm$TableName` = type.simpleName
    // FIXME API-LIFECYCLE Could be lazy loaded from link; requires handling of link in compiler plugin
    this.`$realm$ObjectPointer` = RealmInterop.realm_get_object(realm, link)
    return this as T
}

fun RealmModelInternal.unmanage() {
    // FIXME API-LIFECYCLE For now update the object to an inconsistent state that triggers Realm setters and
    //  getters to raise an IllegalStateException by keeping the `$realm$IsManaged` property set to
    //  true (triggers delegation to Realm-backed getter/setter) while clearing the native
    //  pointers (triggers the native getter/setter to throw the IllegalStateException).
    this.`$realm$IsManaged` = true
    this.`$realm$ObjectPointer` = null
    this.`$realm$Pointer` = null
}
