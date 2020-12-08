/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.interop
// FIXME Rename io.realm.interop. to something with platform?

import io.realm.runtimeapi.NativePointer

actual object RealmInterop {
    // TODO Maybe pull library loading into separate method
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

    actual fun <T> realm_set_value(realm: NativePointer?, o: NativePointer?, table: String, col: String, value: T, isDefault: Boolean) {
        if (realm == null || o == null) {
            throw IllegalStateException("Cannot update deleted object")
        }
        realm_set_value(o, propertyInfo(realm, classInfo(realm, table), col).key.col_key, value, isDefault)
    }

    actual fun <T> realm_get_value(realm: NativePointer?, o: NativePointer?, table: String, col: String, type: PropertyType): T {
        if (realm == null || o == null) {
            throw IllegalStateException("Invalid/deleted object")
        }
        val pinfo = propertyInfo(realm, classInfo(realm, table), col)
        val cvalue = realm_value_t()
        realmc.realm_get_value((o as LongPointerWrapper).ptr, pinfo.key, cvalue)
        when (cvalue.type) {
            realm_value_type_e.RLM_TYPE_STRING ->
                return cvalue.string as T
            else ->
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
    actual fun objectGetString(realm: NativePointer?, o: NativePointer?, table: String, col: String): String {
        return realm_get_value<String>(realm, o, table, col, PropertyType.RLM_PROPERTY_TYPE_STRING)
    }

    actual fun objectSetString(realm: NativePointer?, o: NativePointer?, table: String, col: String, value: String) {
        realm_set_value(realm, o, table, col, value, false)
    }

    actual fun realm_object_delete(obj: NativePointer) {
        realmc.realm_object_delete((obj as LongPointerWrapper).ptr)
    }
}
