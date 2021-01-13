package io.realm.migration

import io.realm.dynamic.DynamicRealm
import io.realm.dynamic.DynamicRealmObject
import io.realm.schema.MutableRealmSchema
import io.realm.schema.RealmSchema

// Class that can facilitate an automatic migration
// The properties and functions are copied from Swift
// The API for this class is still a bit unsure. The .NET and Swift API's are pretty different.
class Migration {

    val oldRealm: DynamicRealm = TODO("Should be opened as readonly")
    val newRealm: DynamicRealm = TODO()
    val oldSchema: RealmSchema = TODO()
    val newSchema: MutableRealmSchema  = TODO("Unclear if this should be mutable")

    fun enumerateObjects(clazz: String, action: (oldObject: DynamicRealmObject, newObject: DynamicRealmObject) -> Unit) { TODO() }

}