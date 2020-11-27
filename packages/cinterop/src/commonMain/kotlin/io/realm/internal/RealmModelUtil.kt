package io.realm.internal

import io.realm.runtimeapi.RealmModelInternal

fun RealmModelInternal.unmanage() {
    this.`$realm$IsManaged` = false
    this.`$realm$ObjectPointer` = null
    this.`$realm$Pointer` = null
}
