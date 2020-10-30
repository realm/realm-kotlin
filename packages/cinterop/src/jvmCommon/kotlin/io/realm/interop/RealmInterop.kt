package io.realm.interop
// FIXME Rename io.realm.interop. to something with platform?

import io.realm.runtimeapi.NativePointer
import kotlin.jvm.JvmName

// JVM/Android specific pointer wrapper
class LongPointerWrapper(@get:JvmName("ptr") val ptr: Long) : NativePointer

actual object RealmInterop {
    // TODO Maybe pull library loading into separate method
    init {
        System.loadLibrary("realmc")
    }

    actual fun realm_get_library_version(): String {
        return realmc.realm_get_library_version()
    }

    // FIXME Maybe eliminate Class/Property and initialize native pointers to realm_class_info_t and
    //  realm_property_info_t directly in generated class
    actual fun realm_schema_new(tables: List<Table>): NativePointer {
        val count = tables.size
        val cclasses = realmc.new_classArray(count)
        val cproperties = realmc.new_propertyArrayArray(count)

        for ((i, clazz) in tables.withIndex()) {
            val properties = clazz.properties
            // Class
            val cclass = realm_class_info_t().apply {
                name = clazz.name
                primary_key = clazz.primaryKey
                num_properties = properties.size.toLong()
                num_computed_properties = 0
                key = realm_table_key_t()
                flags = clazz.flags.fold(0) { flags, element -> flags or element.value }
            }
            // Properties
            val classProperties = realmc.new_propertyArray(properties.size)
            for ((j, property) in properties.withIndex()) {
                val cproperty = realm_property_info_t().apply {
                    name = property.name
                    public_name = property.publicName
                    type = property.type.value
                    collection_type = property.collectionType.value
                    link_target = property.linkTarget
                    link_origin_property_name = property.linkOriginPropertyName
                    key = realm_col_key_t() // property.key
                    flags = property.flags.fold(0) { flags, element -> flags or element.value }
                }
                realmc.propertyArray_setitem(classProperties, j, cproperty)
            }
            realmc.classArray_setitem(cclasses, i, cclass)
            realmc.propertyArrayArray_setitem(cproperties, i, classProperties)
        }
        return LongPointerWrapper(realmc.realm_schema_new(cclasses, count.toLong(), cproperties))
    }

    actual fun realm_config_new(): NativePointer {
        return LongPointerWrapper(realmc.realm_config_new())
    }

    actual fun realm_config_set_path(config: NativePointer, path: String) {
        realmc.realm_config_set_path((config as LongPointerWrapper).ptr, path)
    }

    actual fun realm_config_set_schema_mode(config: NativePointer, mode: SchemaMode) {
        realmc.realm_config_set_schema_mode((config as LongPointerWrapper).ptr, mode.value)
    }

    actual fun realm_config_set_schema_version(config: NativePointer, version: Long) {
        realmc.realm_config_set_schema_version((config as LongPointerWrapper).ptr, version.toBigInteger())
    }

    actual fun realm_config_set_schema(config: NativePointer, schema: NativePointer) {
        realmc.realm_config_set_schema((config as LongPointerWrapper).ptr, (schema as LongPointerWrapper).ptr)
    }

    actual fun realm_open(config: NativePointer): NativePointer {
        // Compiler complains without useless cast
        return LongPointerWrapper(realmc.realm_open((config as LongPointerWrapper).ptr))
    }

    actual fun realm_close(realm: NativePointer) {
        realmc.realm_close((realm as LongPointerWrapper).ptr)
    }

    actual fun realm_schema_validate(schema: NativePointer): Boolean {
        return realmc.realm_schema_validate((schema as LongPointerWrapper).ptr)
    }

    actual fun realm_get_schema(realm: NativePointer): NativePointer {
        TODO("Not yet implemented")
    }

    actual fun realm_get_num_classes(realm: NativePointer): Long {
        return realmc.realm_get_num_classes((realm as LongPointerWrapper).ptr)
    }

    actual fun realm_release(o: NativePointer) {
        realmc.realm_release((o as LongPointerWrapper).ptr)
    }

    actual fun realm_begin_write(realm: NativePointer) {
        realmc.realm_begin_write((realm as LongPointerWrapper).ptr)
    }

    actual fun realm_commit(realm: NativePointer) {
        realmc.realm_commit((realm as LongPointerWrapper).ptr)
    }

    actual fun realm_object_create(realm: NativePointer, key: Long): NativePointer {
        val ckey = realm_table_key_t().apply { table_key = key }
        return LongPointerWrapper(realmc.realm_object_create((realm as LongPointerWrapper).ptr, ckey))
    }

    actual fun realm_find_class(realm: NativePointer, name: String): Long {
        val info = realm_class_info_t()
        val found = booleanArrayOf(false)
        realmc.realm_find_class((realm as LongPointerWrapper).ptr, name, found, info)
        if (!found[0]) {
            throw RuntimeException("Cannot find class: '$name")
        }
        return info.key.table_key
    }

    private fun <T> realm_set_value(o: NativePointer, key: Long, value: T, isDefault: Boolean) {
        val ckey = realm_col_key_t().apply { col_key = key }
        val cvalue = realm_value_t()
        when (value!!::class) {
            String::class -> {
                cvalue.type = realm_value_type_e.RLM_TYPE_STRING
                cvalue.string = value as String
            }
            else -> {
                TODO("Only string are support at the moment")
            }
        }
        realmc.realm_set_value((o as LongPointerWrapper).ptr, ckey, cvalue, isDefault)
    }

    actual fun <T> realm_set_value(realm: NativePointer, o: NativePointer, table: String, col: String, value: T, isDefault: Boolean) {
        realm_set_value(o, property_info(realm, table, col).key.col_key, value, isDefault)
    }

    actual fun <T> realm_get_value(realm: NativePointer, o: NativePointer, table: String, col: String, type: PropertyType): T {
        val pinfo = property_info(realm, table, col)
        val cvalue = realm_value_t()
        realmc.realm_get_value((o as LongPointerWrapper).ptr, pinfo.key, cvalue)
        when (cvalue.type) {
            realm_value_type_e.RLM_TYPE_STRING ->
                return cvalue.string as T
            else ->
                TODO("Only string are support at the moment")
        }
    }

    // Lookup property info from realm and table and column name
    private fun property_info(realm: NativePointer, table: String, col: String): realm_property_info_t {
        val found = booleanArrayOf(false)
        val tinfo = realm_class_info_t()
        realmc.realm_find_class((realm as LongPointerWrapper).ptr, table, found, tinfo)
        if (!found[0]) {
            throw RuntimeException("Cannot find class: '$table")
        }
        val pinfo = realm_property_info_t()
        val ckey = realm_table_key_t().apply { table_key = tinfo.key.table_key }
        found[0] = false
        realmc.realm_find_property((realm as LongPointerWrapper).ptr, ckey, col, found, pinfo)
        if (!found[0]) {
            throw RuntimeException("Cannot find property: '$col' in '$table'")
        }
        return pinfo
    }
}
