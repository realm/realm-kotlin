package io.realm.interop

// FIXME API-INTERNAL Consider adding marker interfaces NativeRealm, NativeRealmConfig, etc. as type parameter
//  to NativePointer. NOTE Verify that it is supported for Kotlin Native!
import io.realm.runtimeapi.Link
import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmModel

@Suppress("FunctionNaming", "LongParameterList")
expect object RealmInterop {

    fun realm_get_library_version(): String

    fun realm_schema_new(tables: List<Table>): NativePointer

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

    // FIXME API-INTERNAL Maybe keep full realm_class_info_t/realm_property_info_t representation in Kotlin
    // FIXME API-INTERNAL How to return boolean 'found'? Currently throwing runtime exceptions
    fun realm_find_class(realm: NativePointer, name: String): Long
    fun realm_object_create(realm: NativePointer, key: Long): NativePointer
    // FIXME API-INTERNAL Optimize with direct paths instead of generic type parameter. Currently wrapping
    //  type and key-lookups internally
    fun <T> realm_set_value(realm: NativePointer, obj: NativePointer, table: String, col: String, value: T, isDefault: Boolean)
    fun <T> realm_get_value(realm: NativePointer, obj: NativePointer, table: String, col: String, type: PropertyType): T

    // Typed convenience methods
    fun objectGetString(realm: NativePointer, obj: NativePointer, table: String, col: String): String
    fun objectSetString(realm: NativePointer, obj: NativePointer, table: String, col: String, value: String)

    // FIXME Support for all types
    //  https://github.com/realm/realm-kotlin/issues/69
//    override fun objectGetInt64(pointer: NativePointer, propertyName: String): Long? {
//    }
//    override fun objectSetInt64(pointer: NativePointer, propertyName: String, value: Long) {
//    }

    fun realm_query_parse(realm: NativePointer, table: String, query: String, vararg args: Any): NativePointer

    fun <T : RealmModel> realm_query_find_first(realm: NativePointer): Link
    fun realm_query_find_all(query: NativePointer): NativePointer

    fun realm_results_count(results: NativePointer): Long
    // FIXME OPTIMIZE Get many
    fun <T> realm_results_get(results: NativePointer, index: Long): Link

    fun realm_get_object(realm: NativePointer, link: Link): NativePointer
}
