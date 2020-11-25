package io.realm.interop
// FIXME API-CLEANUP Rename io.realm.interop. to something with platform?
//  https://github.com/realm/realm-kotlin/issues/56

import io.realm.runtimeapi.Link
import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmModel

actual object RealmInterop {
    // TODO API-CLEANUP Maybe pull library loading into separate method
    //  https://github.com/realm/realm-kotlin/issues/56
    init {
        System.loadLibrary("realmc")
    }

    actual fun realm_get_library_version(): String {
        return realmc.realm_get_library_version()
    }

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
                flags = clazz.flags.fold(0) { flags, element -> flags or element.nativeValue }
            }
            // Properties
            val classProperties = realmc.new_propertyArray(properties.size)
            for ((j, property) in properties.withIndex()) {
                val cproperty = realm_property_info_t().apply {
                    name = property.name
                    public_name = property.publicName
                    type = property.type.nativeValue
                    collection_type = property.collectionType.nativeValue
                    link_target = property.linkTarget
                    link_origin_property_name = property.linkOriginPropertyName
                    key = realm_col_key_t() // property.key
                    flags = property.flags.fold(0) { flags, element -> flags or element.nativeValue }
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
        realmc.realm_config_set_schema_mode((config as LongPointerWrapper).ptr, mode.nativeValue)
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
        // TODO API-SCHEMA
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
        realm_set_value(o, propertyInfo(realm, classInfo(realm, table), col).key.col_key, value, isDefault)
    }

    actual fun <T> realm_get_value(realm: NativePointer, o: NativePointer, table: String, col: String, type: PropertyType): T {
        val pinfo = propertyInfo(realm, classInfo(realm, table), col)
        val cvalue = realm_value_t()
        realmc.realm_get_value((o as LongPointerWrapper).ptr, pinfo.key, cvalue)
        when (cvalue.type) {
            realm_value_type_e.RLM_TYPE_STRING ->
                return cvalue.string as T
            else ->
                // TODO API-FULL Implement all types
                TODO("Only string are support at the moment")
        }
    }

    private fun classInfo(realm: NativePointer, table: String): realm_class_info_t {
        val found = booleanArrayOf(false)
        val classInfo = realm_class_info_t()
        realmc.realm_find_class((realm as LongPointerWrapper).ptr, table, found, classInfo)
        if (!found[0]) {
            throw RuntimeException("Cannot find class: '$table")
        }
        return classInfo
    }

    private fun propertyInfo(realm: NativePointer, classInfo: realm_class_info_t, col: String): realm_property_info_t {
        val found = booleanArrayOf(false)
        val pinfo = realm_property_info_t()
        realmc.realm_find_property((realm as LongPointerWrapper).ptr, classInfo.key, col, found, pinfo)
        if (!found[0]) {
            throw RuntimeException("Cannot find property: '$col' in '$classInfo.name'")
        }
        return pinfo
    }

    // Typed convenience methods
    actual fun objectGetString(realm: NativePointer, o: NativePointer, table: String, col: String): String {
        return realm_get_value<String>(realm, o, table, col, PropertyType.RLM_PROPERTY_TYPE_STRING)
    }

    actual fun objectSetString(realm: NativePointer, o: NativePointer, table: String, col: String, value: String) {
        realm_set_value(realm, o, table, col, value, false)
    }

    actual fun realm_query_parse(realm: NativePointer, table: String, query: String, vararg args: Any ): NativePointer {
        val count = args.size
        val classKey = classInfo(realm, table).key
        val x = classKey.table_key
        val cArgs = realmc.new_valueArray(count)
        args.mapIndexed { i, arg ->
            realmc.valueArray_setitem(cArgs, i, value(arg))
        }
        return LongPointerWrapper(realmc.realm_query_parse(realm.cptr(), classKey, query, count.toLong(), cArgs))
    }

    actual fun <T: RealmModel> realm_query_find_first(realm: NativePointer) : Link {
        val value = realm_value_t()
        val found = booleanArrayOf(false)
        realmc.realm_query_find_first(realm.cptr(), value, found)
        // FIXME Validate that we have:
        //  - found anything
        //  - it is a link
        //  - i matches the specific type
        if (!found[0]) {
            error("Query did not find anything")
        }
        if (value.type != realm_value_type_e.RLM_TYPE_LINK) {
            error("Query did not return link but ${value.type}")
        }
        return Link(value.link.target.obj_key, value.link.target_table.table_key)
    }

    actual fun realm_query_find_all(query: NativePointer): NativePointer {
        return LongPointerWrapper(realmc.realm_query_find_all(query.cptr()))
    }

    actual fun realm_results_count(results: NativePointer): Long {
        val count = realm_size_t()
        realmc.realm_results_count(results.cptr(), count)
        return count.value
    }

    // TODO OPTIMIZE Getting a range
    actual fun <T> realm_results_get(results: NativePointer, index: Long): Link {
        val value = realm_value_t()
        realmc.realm_results_get(results.cptr(), index, value)
        return Link(value.link.target.obj_key, value.link.target_table.table_key)
    }

    actual fun realm_get_object(realm: NativePointer, tableKey: Long, objKey: Long): NativePointer {
        val table = realm_table_key_t().apply { table_key = tableKey }
        val obj = realm_obj_key_t().apply { obj_key = objKey }
        return LongPointerWrapper(realmc.realm_get_object(realm.cptr(), table, obj))
    }

    fun NativePointer.cptr(): Long {
        return (this as LongPointerWrapper).ptr
    }

    // FIXME EVALUATE 
    //  - Can we always derive value type or do we also have coercion, etc.
    private fun <T> realm_value_t.get(): T {
        return when(PropertyType.of(this.type)) {
            PropertyType.RLM_PROPERTY_TYPE_STRING -> string as T
            else -> TODO()
        }
    }

    private fun value(o: Any): realm_value_t {
        val value: realm_value_t = realm_value_t()
        when(o) {
            is String -> {
                value.type = realm_value_type_e.RLM_TYPE_STRING
                value.string  = o
            }
            else -> {
                TODO("Value conversion not yet implemented for : ${o.javaClass}")
            }

        }
        return value
    }

}
