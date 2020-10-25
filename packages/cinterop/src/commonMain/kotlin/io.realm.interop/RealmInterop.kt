package io.realm.interop

// FIXME Consider adding marker interfaces NativeRealm, NativeRealmConfig, etc. as type parameter
//  to NativePointer. NOTE Verify that it is supported for Kotlin Native!
import io.realm.runtimeapi.NativePointer


expect object RealmInterop {

    fun realm_get_library_version(): String;

    fun realm_schema_new(classes: List<Class>): NativePointer

    fun realm_config_new(): NativePointer
    fun realm_config_set_path(config: NativePointer, path: String)
    fun realm_config_set_schema_mode(config: NativePointer, mode: SchemaMode)
    fun realm_config_set_schema_version(config: NativePointer, version: Long)
    fun realm_config_set_schema(config: NativePointer, schema: NativePointer)

    fun realm_schema_validate(schema: NativePointer): Boolean

    fun realm_open(config: NativePointer): NativePointer
    fun realm_close(realm: NativePointer)

    fun realm_get_schema(realm: NativePointer): NativePointer
    fun realm_get_num_classes(realm: NativePointer): Long

    fun realm_release(o: NativePointer)

    fun realm_begin_write(realm: NativePointer)
    fun realm_commit(realm: NativePointer)

    // FIXME Maybe keep full realm_class_info_t/realm_property_info_t representation in Kotlin
    // FIXME Only operating on key/Long to get going
    // FIXME How to return boolean 'found'? Currently throwing runtime exceptions
    fun realm_find_class(realm:NativePointer, name: String): Long
    fun realm_object_create(realm: NativePointer, key: Long): NativePointer
    // FIXME Optimize with direct paths instead of generic type parameter. Currently wrapping
    //  type and key-lookups internally
    fun <T> realm_set_value(realm:NativePointer, o: NativePointer, table: String, col: String, value: T, isDefault: Boolean)
    fun <T> realm_get_value(realm:NativePointer, o: NativePointer, table: String, col: String, type: PropertyType): T

}
