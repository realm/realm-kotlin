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
// TODO https://github.com/realm/realm-kotlin/issues/70
@file:Suppress("TooGenericExceptionThrown", "TooGenericExceptionCaught")

package io.realm.interop

import io.realm.interop.Constants.ENCRYPTION_KEY_LENGTH
import io.realm.interop.RealmInterop.cptr
import kotlinx.coroutines.CoroutineDispatcher

// FIXME API-CLEANUP Rename io.realm.interop. to something with platform?
//  https://github.com/realm/realm-kotlin/issues/56

private val INVALID_CLASS_KEY: Long by lazy { realmc.getRLM_INVALID_CLASS_KEY() }
private val INVALID_PROPERTY_KEY: Long by lazy { realmc.getRLM_INVALID_PROPERTY_KEY() }

/**
 * JVM/Android interop implementation.
 *
 * NOTE: All methods that return a boolean to indicate an exception are being checked automatically in JNI.
 * So there is no need to verify the return value in the JVM interop layer.
 */
@Suppress("LargeClass", "FunctionNaming", "TooGenericExceptionCaught")
actual object RealmInterop {

    // TODO API-CLEANUP Maybe pull library loading into separate method
    //  https://github.com/realm/realm-kotlin/issues/56
    init {
        System.loadLibrary("realmc")
    }

    actual fun realm_get_version_id(realm: NativePointer): Long {
        val version = realm_version_id_t()
        val found = BooleanArray(1)
        realmc.realm_get_version_id(realm.cptr(), found, version)
        return if (found[0]) {
            version.version
        } else {
            throw IllegalStateException("No VersionId was available. Reading the VersionId requires a valid read transaction.")
        }
    }

    actual fun realm_get_library_version(): String {
        return realmc.realm_get_library_version()
    }

    actual fun realm_get_num_versions(realm: NativePointer): Long {
        val result = LongArray(1)
        realmc.realm_get_num_versions(realm.cptr(), result)
        return result.first()
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
                primary_key = clazz.primaryKey ?: ""
                num_properties = properties.size.toLong()
                num_computed_properties = 0
                key = INVALID_CLASS_KEY
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
                    key = INVALID_PROPERTY_KEY
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
        realmc.realm_config_set_schema_version((config as LongPointerWrapper).ptr, version)
    }

    actual fun realm_config_set_schema(config: NativePointer, schema: NativePointer) {
        realmc.realm_config_set_schema((config as LongPointerWrapper).ptr, (schema as LongPointerWrapper).ptr)
    }

    actual fun realm_config_set_max_number_of_active_versions(config: NativePointer, maxNumberOfVersions: Long) {
        realmc.realm_config_set_max_number_of_active_versions(config.cptr(), maxNumberOfVersions)
    }

    actual fun realm_config_set_encryption_key(config: NativePointer, encryptionKey: ByteArray) {
        realmc.realm_config_set_encryption_key(config.cptr(), encryptionKey, encryptionKey.size.toLong())
    }

    actual fun realm_config_get_encryption_key(config: NativePointer): ByteArray? {
        val key = ByteArray(ENCRYPTION_KEY_LENGTH)
        val keyLength: Long = realmc.realm_config_get_encryption_key(config.cptr(), key)

        if (keyLength == ENCRYPTION_KEY_LENGTH.toLong()) {
            return key
        }
        return null
    }

    actual fun realm_open(config: NativePointer, dispatcher: CoroutineDispatcher?): NativePointer {
        val realmPtr = LongPointerWrapper(realmc.realm_open((config as LongPointerWrapper).ptr))
        // Ensure that we can read version information, etc.
        realm_begin_read(realmPtr)
        return realmPtr
    }

    actual fun realm_freeze(liveRealm: NativePointer): NativePointer {
        return LongPointerWrapper(realmc.realm_freeze(liveRealm.cptr()))
    }

    actual fun realm_thaw(frozenRealm: NativePointer): NativePointer {
        val realmPtr = LongPointerWrapper(realmc.realm_thaw(frozenRealm.cptr()))
        // Ensure that we can read version information, etc.
        realm_begin_read(realmPtr)
        return realmPtr
    }

    actual fun realm_is_frozen(realm: NativePointer): Boolean {
        return realmc.realm_is_frozen(realm.cptr())
    }

    actual fun realm_close(realm: NativePointer) {
        realmc.realm_close((realm as LongPointerWrapper).ptr)
    }

    actual fun realm_schema_validate(schema: NativePointer, mode: SchemaValidationMode): Boolean {
        return realmc.realm_schema_validate((schema as LongPointerWrapper).ptr, mode.nativeValue.toLong())
    }

    actual fun realm_get_schema(realm: NativePointer): NativePointer {
        // TODO API-SCHEMA
        TODO("Not yet implemented")
    }

    actual fun realm_get_num_classes(realm: NativePointer): Long {
        return realmc.realm_get_num_classes((realm as LongPointerWrapper).ptr)
    }

    actual fun realm_release(p: NativePointer) {
        realmc.realm_release((p as LongPointerWrapper).ptr)
    }

    actual fun realm_is_closed(realm: NativePointer): Boolean {
        return realmc.realm_is_closed((realm as LongPointerWrapper).ptr)
    }

    actual fun realm_begin_read(realm: NativePointer) {
        realmc.realm_begin_read((realm as LongPointerWrapper).ptr)
    }

    actual fun realm_begin_write(realm: NativePointer) {
        realmc.realm_begin_write((realm as LongPointerWrapper).ptr)
    }

    actual fun realm_commit(realm: NativePointer) {
        realmc.realm_commit((realm as LongPointerWrapper).ptr)
    }

    actual fun realm_rollback(realm: NativePointer) {
        realmc.realm_rollback((realm as LongPointerWrapper).ptr)
    }

    actual fun realm_is_in_transaction(realm: NativePointer): Boolean {
        return realmc.realm_is_writable(realm.cptr())
    }

    actual fun realm_object_create(realm: NativePointer, classKey: ClassKey): NativePointer {
        return LongPointerWrapper(realmc.realm_object_create((realm as LongPointerWrapper).ptr, classKey.key))
    }

    actual fun realm_object_create_with_primary_key(realm: NativePointer, classKey: ClassKey, primaryKey: Any?): NativePointer {
        return LongPointerWrapper(realmc.realm_object_create_with_primary_key((realm as LongPointerWrapper).ptr, classKey.key, to_realm_value(primaryKey)))
    }

    actual fun realm_object_is_valid(obj: NativePointer): Boolean {
        return realmc.realm_object_is_valid(obj.cptr())
    }

    actual fun realm_object_freeze(liveObject: NativePointer, frozenRealm: NativePointer): NativePointer {
        return LongPointerWrapper(realmc.realm_object_freeze(liveObject.cptr(), frozenRealm.cptr()))
    }

    actual fun realm_object_thaw(frozenObject: NativePointer, liveRealm: NativePointer): NativePointer? {
        return LongPointerWrapper(realmc.realm_object_thaw(frozenObject.cptr(), liveRealm.cptr()))
    }

    actual fun realm_find_class(realm: NativePointer, name: String): ClassKey {
        val info = realm_class_info_t()
        val found = booleanArrayOf(false)
        realmc.realm_find_class((realm as LongPointerWrapper).ptr, name, found, info)
        if (!found[0]) {
            throw IllegalArgumentException("Cannot find class: '$name")
        }
        return ClassKey(info.key)
    }

    actual fun realm_object_as_link(obj: NativePointer): Link {
        val link: realm_link_t = realmc.realm_object_as_link(obj.cptr())
        return Link(link.target_table, link.target)
    }

    actual fun realm_get_col_key(realm: NativePointer, table: String, col: String): ColumnKey {
        return ColumnKey(propertyInfo(realm, classInfo(realm, table), col).key)
    }

    actual fun <T> realm_get_value(obj: NativePointer, key: ColumnKey): T {
        // TODO OPTIMIZED Consider optimizing this to construct T in JNI call
        val cvalue = realm_value_t()
        realmc.realm_get_value((obj as LongPointerWrapper).ptr, key.key, cvalue)
        return from_realm_value(cvalue)
    }

    private fun <T> from_realm_value(value: realm_value_t?): T {
        return when (value?.type) {
            realm_value_type_e.RLM_TYPE_STRING ->
                value.string
            realm_value_type_e.RLM_TYPE_INT ->
                value.integer
            realm_value_type_e.RLM_TYPE_BOOL ->
                value._boolean
            realm_value_type_e.RLM_TYPE_FLOAT ->
                value.fnum
            realm_value_type_e.RLM_TYPE_DOUBLE ->
                value.dnum
            realm_value_type_e.RLM_TYPE_LINK ->
                value.asLink()
            realm_value_type_e.RLM_TYPE_NULL,
            null ->
                null
            else ->
                TODO("Unsupported type for from_realm_value ${value.type}")
        } as T
    }

    actual fun <T> realm_set_value(o: NativePointer, key: ColumnKey, value: T, isDefault: Boolean) {
        val cvalue = to_realm_value(value)
        realmc.realm_set_value((o as LongPointerWrapper).ptr, key.key, cvalue, isDefault)
    }

    actual fun realm_get_list(obj: NativePointer, key: ColumnKey): NativePointer {
        return LongPointerWrapper(realmc.realm_get_list((obj as LongPointerWrapper).ptr, key.key))
    }

    actual fun realm_list_size(list: NativePointer): Long {
        val size = realm_size_t()
        realmc.realm_list_size(list.cptr(), size)
        return size.value
    }

    actual fun <T> realm_list_get(list: NativePointer, index: Long): T {
        val cvalue = realm_value_t()
        realmc.realm_list_get(list.cptr(), index, cvalue)
        return from_realm_value(cvalue)
    }

    actual fun <T> realm_list_add(list: NativePointer, index: Long, value: T) {
        val cvalue = to_realm_value(value)
        realmc.realm_list_insert(list.cptr(), index, cvalue)
    }

    actual fun <T> realm_list_set(list: NativePointer, index: Long, value: T): T {
        return realm_list_get<T>(list, index).also {
            realmc.realm_list_set(list.cptr(), index, to_realm_value(value))
        }
    }

    actual fun realm_list_clear(list: NativePointer) {
        realmc.realm_list_clear(list.cptr())
    }

    actual fun realm_list_erase(list: NativePointer, index: Long) {
        realmc.realm_list_erase(list.cptr(), index)
    }

    actual fun realm_list_freeze(
        liveList: NativePointer,
        frozenRealm: NativePointer
    ): NativePointer {
        return LongPointerWrapper(realmc.realm_list_freeze(liveList.cptr(), frozenRealm.cptr()))
    }

    actual fun realm_list_thaw(
        frozenList: NativePointer,
        liveRealm: NativePointer
    ): NativePointer {
        return LongPointerWrapper(realmc.realm_list_thaw(frozenList.cptr(), liveRealm.cptr()))
    }

    // TODO OPTIMIZE Maybe move this to JNI to avoid multiple round trips for allocating and
    //  updating before actually calling
    private fun <T> to_realm_value(value: T): realm_value_t {
        val cvalue = realm_value_t()
        if (value == null) {
            cvalue.type = realm_value_type_e.RLM_TYPE_NULL
        } else {
            when (value) {
                is String -> {
                    cvalue.type = realm_value_type_e.RLM_TYPE_STRING
                    cvalue.string = value
                }
                is Byte -> {
                    cvalue.type = realm_value_type_e.RLM_TYPE_INT
                    cvalue.integer = value.toLong()
                }
                is Char -> {
                    cvalue.type = realm_value_type_e.RLM_TYPE_INT
                    cvalue.integer = value.toLong()
                }
                is Short -> {
                    cvalue.type = realm_value_type_e.RLM_TYPE_INT
                    cvalue.integer = value.toLong()
                }
                is Int -> {
                    cvalue.type = realm_value_type_e.RLM_TYPE_INT
                    cvalue.integer = value.toLong()
                }
                is Long -> {
                    cvalue.type = realm_value_type_e.RLM_TYPE_INT
                    cvalue.integer = value
                }
                is Boolean -> {
                    cvalue.type = realm_value_type_e.RLM_TYPE_BOOL
                    cvalue._boolean = value
                }
                is Float -> {
                    cvalue.type = realm_value_type_e.RLM_TYPE_FLOAT
                    cvalue.fnum = value
                }
                is Double -> {
                    cvalue.type = realm_value_type_e.RLM_TYPE_DOUBLE
                    cvalue.dnum = value
                }
                is RealmObjectInterop -> {
                    val nativePointer = (value as RealmObjectInterop).`$realm$ObjectPointer`
                        ?: error("Cannot add unmanaged object")
                    cvalue.link = realmc.realm_object_as_link(nativePointer.cptr())
                    cvalue.type = realm_value_type_e.RLM_TYPE_LINK
                }
                else -> {
                    TODO("Unsupported type for to_realm_value `${value!!::class.simpleName}`")
                }
            }
        }
        return cvalue
    }

    actual fun realm_object_add_notification_callback(obj: NativePointer, callback: Callback): NativePointer {
        return LongPointerWrapper(
            realmc.register_object_notification_cb(
                obj.cptr(),
                object : NotificationCallback {
                    override fun onChange(pointer: Long) {
                        callback.onChange(LongPointerWrapper(pointer, managed = false)) // FIXME use managed pointer https://github.com/realm/realm-kotlin/issues/147
                    }
                }
            ),
            managed = false
        )
    }

    actual fun realm_results_add_notification_callback(results: NativePointer, callback: Callback): NativePointer {
        return LongPointerWrapper(
            realmc.register_results_notification_cb(
                results.cptr(),
                object : NotificationCallback {
                    override fun onChange(pointer: Long) {
                        callback.onChange(LongPointerWrapper(pointer, managed = false)) // FIXME use managed pointer https://github.com/realm/realm-kotlin/issues/147
                    }
                }
            ),
            managed = false
        )
    }

    actual fun realm_list_add_notification_callback(
        list: NativePointer,
        callback: Callback
    ): NativePointer {
        return LongPointerWrapper(
            realmc.register_list_notification_cb(
                list.cptr(),
                object : NotificationCallback {
                    override fun onChange(pointer: Long) {
                        callback.onChange(LongPointerWrapper(pointer, managed = false)) // FIXME use managed pointer https://github.com/realm/realm-kotlin/issues/147
                    }
                }
            ),
            managed = false
        )
    }

    private fun classInfo(realm: NativePointer, table: String): realm_class_info_t {
        val found = booleanArrayOf(false)
        val classInfo = realm_class_info_t()
        realmc.realm_find_class((realm as LongPointerWrapper).ptr, table, found, classInfo)
        if (!found[0]) {
            throw IllegalArgumentException("Cannot find class: '$table")
        }
        return classInfo
    }

    private fun propertyInfo(realm: NativePointer, classInfo: realm_class_info_t, col: String): realm_property_info_t {
        val found = booleanArrayOf(false)
        val pinfo = realm_property_info_t()
        realmc.realm_find_property((realm as LongPointerWrapper).ptr, classInfo.key, col, found, pinfo)
        if (!found[0]) {
            throw IllegalArgumentException("Cannot find property: '$col' in '$classInfo.name'")
        }
        return pinfo
    }

    actual fun realm_query_parse(realm: NativePointer, table: String, query: String, vararg args: Any?): NativePointer {
        val count = args.size
        val classKey = classInfo(realm, table).key
        val cArgs = realmc.new_valueArray(count)
        args.mapIndexed { i, arg ->
            realmc.valueArray_setitem(cArgs, i, to_realm_value(arg))
        }
        return LongPointerWrapper(realmc.realm_query_parse(realm.cptr(), classKey, query, count.toLong(), cArgs))
    }

    actual fun realm_query_find_first(realm: NativePointer): Link? {
        val value = realm_value_t()
        val found = booleanArrayOf(false)
        realmc.realm_query_find_first(realm.cptr(), value, found)
        if (!found[0]) {
            return null
        }
        return value.asLink()
    }

    actual fun realm_query_find_all(query: NativePointer): NativePointer {
        return LongPointerWrapper(realmc.realm_query_find_all(query.cptr()))
    }

    actual fun realm_results_freeze(liveResults: NativePointer, frozenRealm: NativePointer): NativePointer {
        return LongPointerWrapper(realmc.realm_results_freeze(liveResults.cptr(), frozenRealm.cptr()))
    }

    actual fun realm_results_thaw(frozenResults: NativePointer, liveRealm: NativePointer): NativePointer {
        return LongPointerWrapper(realmc.realm_results_thaw(frozenResults.cptr(), liveRealm.cptr()))
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
        return value.asLink()
    }

    actual fun realm_get_object(realm: NativePointer, link: Link): NativePointer {
        return LongPointerWrapper(realmc.realm_get_object(realm.cptr(), link.tableKey, link.objKey))
    }

    actual fun realm_object_find_with_primary_key(realm: NativePointer, classKey: ClassKey, primaryKey: Any?): NativePointer? {
        val cprimaryKey = to_realm_value(primaryKey)
        val found = booleanArrayOf(false)
        return nativePointerOrNull(realmc.realm_object_find_with_primary_key(realm.cptr(), classKey.key, cprimaryKey, found))
    }

    actual fun realm_results_delete_all(results: NativePointer) {
        realmc.realm_results_delete_all(results.cptr())
    }

    actual fun realm_object_delete(obj: NativePointer) {
        realmc.realm_object_delete((obj as LongPointerWrapper).ptr)
    }

    fun nativePointerOrNull(ptr: Long, managed: Boolean = true): NativePointer? {
        return if (ptr != 0L) {
            LongPointerWrapper(ptr, managed)
        } else {
            null
        }
    }

    fun NativePointer.cptr(): Long {
        return (this as LongPointerWrapper).ptr
    }

    private fun realm_value_t.asLink(): Link {
        if (this.type != realm_value_type_e.RLM_TYPE_LINK) {
            error("Value is not of type link: $this.type")
        }
        return Link(this.link.target_table, this.link.target)
    }
}
