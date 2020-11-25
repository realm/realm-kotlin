package io.realm.internal

import io.realm.interop.RealmInterop
import io.realm.runtimeapi.Link
import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmModel
import io.realm.runtimeapi.RealmModelInternal
import kotlin.reflect.KClass

// TODO API-INTERNAL
fun <T: RealmModel> RealmModelInternal.manage(realm: NativePointer, type: KClass<T>, objectPointer: NativePointer): T {
    this.`$realm$IsManaged` = true
    this.`$realm$Pointer` = realm
    this.`$realm$TableName` = type.simpleName
    this.`$realm$ObjectPointer` = objectPointer
    // FIXME Initialize actual link; requires handling of link in compiler plugin
    // this.link = RealmInterop.realm_object_as_link()
    return this as T
}

// TODO API-INTERNAL
fun <T: RealmModel> RealmModelInternal.link(realm: NativePointer, type: KClass<T>, link: Link): T {
    this.`$realm$IsManaged` = true
    this.`$realm$Pointer` = realm
    this.`$realm$TableName` = type.simpleName
    // FIXME Could be lazy loaded from link; requires handling of link in compiler plugin
    this.`$realm$ObjectPointer` = RealmInterop.realm_get_object(realm, link.tableKey, link.objKey)
    return this as T
}

