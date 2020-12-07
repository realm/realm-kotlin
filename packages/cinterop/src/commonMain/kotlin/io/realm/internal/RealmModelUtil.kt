package io.realm.internal

import io.realm.runtimeapi.RealmModelInternal

fun RealmModelInternal.unmanage() {
    // FIXME API For now update the object to an inconsistent state that triggers Realm setters and
    //  getters to raise an IllegalStateException by keeping the `$realm$IsManaged` property set to
    //  true (triggers delegation to Realm-backed getter/setter) while clearing the native
    //  pointers (triggers the native getter/setter to throw the IllegalStateException).
    this.`$realm$IsManaged` = true
    this.`$realm$ObjectPointer` = null
    this.`$realm$Pointer` = null
}
