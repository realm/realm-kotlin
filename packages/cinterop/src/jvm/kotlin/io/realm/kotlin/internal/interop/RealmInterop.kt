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

package io.realm.kotlin.internal.interop

import io.realm.kotlin.internal.interop.Constants.ENCRYPTION_KEY_LENGTH
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mongodb.kbson.ObjectId

// FIXME API-CLEANUP Rename io.realm.interop. to something with platform?
//  https://github.com/realm/realm-kotlin/issues/56

actual val INVALID_CLASS_KEY: ClassKey by lazy { ClassKey(realmc.getRLM_INVALID_CLASS_KEY()) }
actual val INVALID_PROPERTY_KEY: PropertyKey by lazy { PropertyKey(realmc.getRLM_INVALID_PROPERTY_KEY()) }

// The value to pass to JNI functions that accept longs as replacements for pointers and need
// to represent null.
const val NULL_POINTER_VALUE = 0L

/**
 * JVM/Android interop implementation.
 *
 * NOTE: All methods that return a boolean to indicate an exception are being checked automatically in JNI.
 * So there is no need to verify the return value in the JVM interop layer.
 */
@Suppress("LargeClass", "FunctionNaming", "TooGenericExceptionCaught")
actual object RealmInterop {

    actual fun realm_value_get(value: RealmValue): Any? = value.value

    actual fun realm_get_version_id(realm: RealmPointer): Long {
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

    actual fun realm_get_num_versions(realm: RealmPointer): Long {
        val result = LongArray(1)
        realmc.realm_get_num_versions(realm.cptr(), result)
        return result.first()
    }

    actual fun realm_refresh(realm: RealmPointer) {
        // Only returns `true` if the version changed, `false` if the version
        // was already at the latest. Errors will be represented by the actual
        // return value, so just ignore this out parameter.
        val didRefresh = booleanArrayOf(false)
        realmc.realm_refresh(realm.cptr(), didRefresh)
    }

    actual fun realm_schema_new(schema: List<Pair<ClassInfo, List<PropertyInfo>>>): RealmSchemaPointer {
        val count = schema.size
        val cclasses = realmc.new_classArray(count)
        val cproperties = realmc.new_propertyArrayArray(count)

        for ((i, entry) in schema.withIndex()) {
            val (clazz, properties) = entry

            val computedCount = properties.count { it.isComputed }

            // Class
            val cclass = realm_class_info_t().apply {
                name = clazz.name
                primary_key = clazz.primaryKey
                num_properties = (properties.size - computedCount).toLong()
                num_computed_properties = computedCount.toLong()
                key = INVALID_CLASS_KEY.key
                flags = clazz.flags
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
                    key = INVALID_PROPERTY_KEY.key
                    flags = property.flags
                }
                realmc.propertyArray_setitem(classProperties, j, cproperty)
            }
            realmc.classArray_setitem(cclasses, i, cclass)
            realmc.propertyArrayArray_setitem(cproperties, i, classProperties)
        }
        try {
            return LongPointerWrapper(realmc.realm_schema_new(cclasses, count.toLong(), cproperties))
        } finally {
            // Clean up classes
            for (classIndex in 0 until count) {
                val classInfo = realmc.classArray_getitem(cclasses, classIndex)

                // Clean properties
                val propertyArray = realmc.propertyArrayArray_getitem(cproperties, classIndex)

                val propertyCount = classInfo.getNum_properties() + classInfo.getNum_computed_properties()
                for (propertyIndex in 0 until propertyCount) {
                    val property = realmc.propertyArray_getitem(propertyArray, propertyIndex.toInt())

                    realmc.realm_property_info_t_cleanup(property)
                    property.delete()
                }

                realmc.delete_propertyArray(propertyArray)

                // end clean properties

                realmc.realm_class_info_t_cleanup(classInfo)
                classInfo.delete()
            }

            realmc.delete_propertyArrayArray(cproperties)
            realmc.delete_classArray(cclasses)
        }
    }

    actual fun realm_config_new(): RealmConfigurationPointer {
        return LongPointerWrapper(realmc.realm_config_new())
    }

    actual fun realm_config_set_path(config: RealmConfigurationPointer, path: String) {
        realmc.realm_config_set_path((config as LongPointerWrapper).ptr, path)
    }

    actual fun realm_config_set_schema_mode(config: RealmConfigurationPointer, mode: SchemaMode) {
        realmc.realm_config_set_schema_mode((config as LongPointerWrapper).ptr, mode.nativeValue)
    }

    actual fun realm_config_set_schema_version(config: RealmConfigurationPointer, version: Long) {
        realmc.realm_config_set_schema_version((config as LongPointerWrapper).ptr, version)
    }

    actual fun realm_config_set_schema(config: RealmConfigurationPointer, schema: RealmSchemaPointer) {
        realmc.realm_config_set_schema((config as LongPointerWrapper).ptr, (schema as LongPointerWrapper).ptr)
    }

    actual fun realm_config_set_max_number_of_active_versions(config: RealmConfigurationPointer, maxNumberOfVersions: Long) {
        realmc.realm_config_set_max_number_of_active_versions(config.cptr(), maxNumberOfVersions)
    }

    actual fun realm_config_set_encryption_key(config: RealmConfigurationPointer, encryptionKey: ByteArray) {
        realmc.realm_config_set_encryption_key(config.cptr(), encryptionKey, encryptionKey.size.toLong())
    }

    actual fun realm_config_get_encryption_key(config: RealmConfigurationPointer): ByteArray? {
        val key = ByteArray(ENCRYPTION_KEY_LENGTH)
        val keyLength: Long = realmc.realm_config_get_encryption_key(config.cptr(), key)

        if (keyLength == ENCRYPTION_KEY_LENGTH.toLong()) {
            return key
        }
        return null
    }

    actual fun realm_config_set_should_compact_on_launch_function(
        config: RealmConfigurationPointer,
        callback: CompactOnLaunchCallback
    ) {
        realmc.realm_config_set_should_compact_on_launch_function(config.cptr(), callback)
    }

    actual fun realm_config_set_migration_function(config: RealmConfigurationPointer, callback: MigrationCallback) {
        realmc.realm_config_set_migration_function(config.cptr(), callback)
    }

    actual fun realm_config_set_automatic_backlink_handling(config: RealmConfigurationPointer, enabled: Boolean) {
        realmc.realm_config_set_automatic_backlink_handling(config.cptr(), enabled)
    }

    actual fun realm_config_set_data_initialization_function(config: RealmConfigurationPointer, callback: DataInitializationCallback) {
        realmc.realm_config_set_data_initialization_function(config.cptr(), callback)
    }

    actual fun realm_config_set_in_memory(config: RealmConfigurationPointer, inMemory: Boolean) {
        realmc.realm_config_set_in_memory(config.cptr(), inMemory)
    }

    actual fun realm_create_scheduler(): RealmSchedulerPointer =
        LongPointerWrapper(realmc.realm_create_generic_scheduler())

    actual fun realm_create_scheduler(dispatcher: CoroutineDispatcher): RealmSchedulerPointer =
        LongPointerWrapper(realmc.realm_create_scheduler(JVMScheduler(dispatcher)))

    actual fun realm_open(
        config: RealmConfigurationPointer,
        scheduler: RealmSchedulerPointer,
    ): Pair<LiveRealmPointer, Boolean> {
        // Configure callback to track if the file was created as part of opening
        var fileCreated = false
        val callback = DataInitializationCallback {
            fileCreated = true
        }
        realm_config_set_data_initialization_function(config, callback)

        realmc.realm_config_set_scheduler(config.cptr(), scheduler.cptr())
        val realmPtr = LongPointerWrapper<LiveRealmT>(realmc.realm_open(config.cptr()))

        // Ensure that we can read version information, etc.
        realm_begin_read(realmPtr)
        return Pair(realmPtr, fileCreated)
    }

    actual fun realm_add_realm_changed_callback(realm: LiveRealmPointer, block: () -> Unit): RealmCallbackTokenPointer {
        return LongPointerWrapper(
            realmc.realm_add_realm_changed_callback(realm.cptr(), block),
            managed = false
        )
    }

    actual fun realm_add_schema_changed_callback(realm: LiveRealmPointer, block: (RealmSchemaPointer) -> Unit): RealmCallbackTokenPointer {
        return LongPointerWrapper(
            realmc.realm_add_schema_changed_callback(realm.cptr(), block),
            managed = false
        )
    }

    actual fun realm_freeze(liveRealm: LiveRealmPointer): FrozenRealmPointer {
        return LongPointerWrapper(realmc.realm_freeze(liveRealm.cptr()))
    }

    actual fun realm_is_frozen(realm: RealmPointer): Boolean {
        return realmc.realm_is_frozen(realm.cptr())
    }

    actual fun realm_close(realm: RealmPointer) {
        realmc.realm_close(realm.cptr())
    }

    actual fun realm_delete_files(path: String) {
        val deleted = booleanArrayOf(false)
        realmc.realm_delete_files(path, deleted)

        if (!deleted[0]) {
            throw IllegalStateException("It's not allowed to delete the file associated with an open Realm. Remember to call 'close()' on the instances of the realm before deleting its file: $path")
        }
    }

    actual fun realm_compact(realm: RealmPointer): Boolean {
        val compacted = booleanArrayOf(false)
        realmc.realm_compact(realm.cptr(), compacted)
        return compacted.first()
    }

    actual fun realm_convert_with_config(
        realm: RealmPointer,
        config: RealmConfigurationPointer,
        mergeWithExisting: Boolean
    ) {
        realmc.realm_convert_with_config(realm.cptr(), config.cptr(), mergeWithExisting)
    }

    actual fun realm_schema_validate(schema: RealmSchemaPointer, mode: SchemaValidationMode): Boolean {
        return realmc.realm_schema_validate(schema.cptr(), mode.nativeValue.toLong())
    }

    actual fun realm_get_schema(realm: RealmPointer): RealmSchemaPointer {
        return LongPointerWrapper(realmc.realm_get_schema(realm.cptr()))
    }

    actual fun realm_get_schema_version(realm: RealmPointer): Long {
        return realmc.realm_get_schema_version(realm.cptr())
    }

    actual fun realm_get_num_classes(realm: RealmPointer): Long {
        return realmc.realm_get_num_classes(realm.cptr())
    }

    actual fun realm_get_class_keys(realm: RealmPointer): List<ClassKey> {
        val count = realm_get_num_classes(realm)
        val keys = LongArray(count.toInt())
        val outCount = longArrayOf(0)
        realmc.realm_get_class_keys(realm.cptr(), keys, count, outCount)
        if (count != outCount[0]) {
            error("Invalid schema: Insufficient keys; got ${outCount[0]}, expected $count")
        }
        return keys.map { ClassKey(it) }
    }

    actual fun realm_find_class(realm: RealmPointer, name: String): ClassKey? {
        val info = realm_class_info_t()
        val found = booleanArrayOf(false)
        realmc.realm_find_class(realm.cptr(), name, found, info)
        return if (found[0]) {
            ClassKey(info.key)
        } else {
            null
        }
    }

    actual fun realm_get_class(realm: RealmPointer, classKey: ClassKey): ClassInfo {
        val info = realm_class_info_t()
        realmc.realm_get_class(realm.cptr(), classKey.key, info)
        return with(info) {
            ClassInfo(name, primary_key, num_properties, num_computed_properties, ClassKey(key), flags)
        }
    }
    actual fun realm_get_class_properties(realm: RealmPointer, classKey: ClassKey, max: Long): List<PropertyInfo> {
        val properties = realmc.new_propertyArray(max.toInt())
        val outCount = longArrayOf(0)
        realmc.realm_get_class_properties(realm.cptr(), classKey.key, properties, max, outCount)
        try {
            return if (outCount[0] > 0) {
                (0 until outCount[0]).map { i ->
                    with(realmc.propertyArray_getitem(properties, i.toInt())) {
                        PropertyInfo(
                            name,
                            public_name,
                            PropertyType.from(type),
                            CollectionType.from(collection_type),
                            link_target,
                            link_origin_property_name,
                            PropertyKey(key),
                            flags
                        )
                    }
                }
            } else {
                emptyList()
            }
        } finally {
            realmc.delete_propertyArray(properties)
        }
    }

    internal actual fun realm_release(p: RealmNativePointer) {
        realmc.realm_release(p.cptr())
    }

    actual fun realm_equals(p1: RealmNativePointer, p2: RealmNativePointer): Boolean {
        return realmc.realm_equals(p1.cptr(), p2.cptr())
    }

    actual fun realm_is_closed(realm: RealmPointer): Boolean {
        return realmc.realm_is_closed(realm.cptr())
    }

    actual fun realm_begin_read(realm: RealmPointer) {
        realmc.realm_begin_read(realm.cptr())
    }

    actual fun realm_begin_write(realm: LiveRealmPointer) {
        realmc.realm_begin_write(realm.cptr())
    }

    actual fun realm_commit(realm: LiveRealmPointer) {
        realmc.realm_commit(realm.cptr())
    }

    actual fun realm_rollback(realm: LiveRealmPointer) {
        realmc.realm_rollback(realm.cptr())
    }

    actual fun realm_is_in_transaction(realm: RealmPointer): Boolean {
        return realmc.realm_is_writable(realm.cptr())
    }

    actual fun realm_update_schema(realm: LiveRealmPointer, schema: RealmSchemaPointer) {
        realmc.realm_update_schema(realm.cptr(), schema.cptr())
    }

    actual fun realm_object_create(realm: LiveRealmPointer, classKey: ClassKey): RealmObjectPointer {
        return LongPointerWrapper(realmc.realm_object_create(realm.cptr(), classKey.key))
    }

    actual fun realm_object_create_with_primary_key(
        realm: LiveRealmPointer,
        classKey: ClassKey,
        primaryKeyTransport: RealmValue
    ): RealmObjectPointer {
        return LongPointerWrapper(
            realmc.realm_object_create_with_primary_key(
                realm.cptr(),
                classKey.key,
                primaryKeyTransport.value
            )
        )
    }

    actual fun realm_object_get_or_create_with_primary_key(
        realm: LiveRealmPointer,
        classKey: ClassKey,
        primaryKeyTransport: RealmValue
    ): RealmObjectPointer {
        val created = booleanArrayOf(false)
        return LongPointerWrapper(
            realmc.realm_object_get_or_create_with_primary_key(
                realm.cptr(),
                classKey.key,
                primaryKeyTransport.value,
                created
            )
        )
    }

    actual fun realm_object_is_valid(obj: RealmObjectPointer): Boolean {
        return realmc.realm_object_is_valid(obj.cptr())
    }

    actual fun realm_object_get_key(obj: RealmObjectPointer): ObjectKey {
        return ObjectKey(realmc.realm_object_get_key(obj.cptr()))
    }

    actual fun realm_object_resolve_in(obj: RealmObjectPointer, realm: RealmPointer): RealmObjectPointer? {
        val objectPointer = longArrayOf(0)
        realmc.realm_object_resolve_in(obj.cptr(), realm.cptr(), objectPointer)
        return if (objectPointer[0] != 0L) {
            LongPointerWrapper(objectPointer[0])
        } else {
            null
        }
    }

    actual fun realm_object_as_link(obj: RealmObjectPointer): Link {
        val link: realm_link_t = realmc.realm_object_as_link(obj.cptr())
        return Link(ClassKey(link.target_table), link.target)
    }

    actual fun realm_object_get_table(obj: RealmObjectPointer): ClassKey {
        return ClassKey(realmc.realm_object_get_table(obj.cptr()))
    }

    actual fun realm_get_col_key(
        realm: RealmPointer,
        classKey: ClassKey,
        col: String
    ): PropertyKey {
        return PropertyKey(propertyInfo(realm, classKey, col).key)
    }

    actual fun MemAllocator.realm_get_value(
        obj: RealmObjectPointer,
        key: PropertyKey
    ): RealmValue {
        val struct = allocRealmValueT()
        realmc.realm_get_value((obj as LongPointerWrapper).ptr, key.key, struct)
        return RealmValue(struct)
    }

    actual fun realm_set_value(
        obj: RealmObjectPointer,
        key: PropertyKey,
        value: RealmValue,
        isDefault: Boolean
    ) {
        realmc.realm_set_value(obj.cptr(), key.key, value.value, isDefault)
    }

    actual fun realm_set_embedded(obj: RealmObjectPointer, key: PropertyKey): RealmObjectPointer {
        return LongPointerWrapper(realmc.realm_set_embedded(obj.cptr(), key.key))
    }

    actual fun realm_set_list(obj: RealmObjectPointer, key: PropertyKey): RealmListPointer {
        realmc.realm_set_list(obj.cptr(), key.key)
        return realm_get_list(obj, key)
    }
    actual fun realm_set_dictionary(obj: RealmObjectPointer, key: PropertyKey): RealmMapPointer {
        realmc.realm_set_dictionary(obj.cptr(), key.key)
        return realm_get_dictionary(obj, key)
    }

    actual fun realm_object_add_int(obj: RealmObjectPointer, key: PropertyKey, value: Long) {
        realmc.realm_object_add_int(obj.cptr(), key.key, value)
    }

    actual fun <T> realm_object_get_parent(
        obj: RealmObjectPointer,
        block: (ClassKey, RealmObjectPointer) -> T
    ): T {
        val objectPointerArray = longArrayOf(0)
        val classKeyPointerArray = longArrayOf(0)

        realmc.realm_object_get_parent(
            /* object = */ obj.cptr(),
            /* parent = */ objectPointerArray,
            /* class_key = */ classKeyPointerArray
        )

        val classKey = ClassKey(classKeyPointerArray[0])
        val objectPointer = LongPointerWrapper<RealmObjectT>(objectPointerArray[0])

        return block(classKey, objectPointer)
    }

    actual fun realm_get_list(obj: RealmObjectPointer, key: PropertyKey): RealmListPointer {
        return LongPointerWrapper(
            realmc.realm_get_list(
                (obj as LongPointerWrapper).ptr,
                key.key
            )
        )
    }

    actual fun realm_get_backlinks(obj: RealmObjectPointer, sourceClassKey: ClassKey, sourcePropertyKey: PropertyKey): RealmResultsPointer {
        return LongPointerWrapper(
            realmc.realm_get_backlinks(
                (obj as LongPointerWrapper).ptr,
                sourceClassKey.key,
                sourcePropertyKey.key
            )
        )
    }

    actual fun realm_list_size(list: RealmListPointer): Long {
        val size = LongArray(1)
        realmc.realm_list_size(list.cptr(), size)
        return size[0]
    }

    actual fun MemAllocator.realm_list_get(
        list: RealmListPointer,
        index: Long
    ): RealmValue {
        val struct = allocRealmValueT()
        realmc.realm_list_get(list.cptr(), index, struct)
        return RealmValue(struct)
    }

    actual fun realm_list_find(list: RealmListPointer, value: RealmValue): Long {
        val index = LongArray(1)
        val found = BooleanArray(1)
        realmc.realm_list_find(list.cptr(), value.value, index, found)
        return if (found[0]) {
            index[0]
        } else {
            INDEX_NOT_FOUND
        }
    }

    actual fun realm_list_get_list(list: RealmListPointer, index: Long): RealmListPointer =
        LongPointerWrapper(realmc.realm_list_get_list(list.cptr(), index))

    actual fun realm_list_get_dictionary(list: RealmListPointer, index: Long): RealmMapPointer =
        LongPointerWrapper(realmc.realm_list_get_dictionary(list.cptr(), index))

    actual fun realm_list_add(list: RealmListPointer, index: Long, transport: RealmValue) {
        realmc.realm_list_insert(list.cptr(), index, transport.value)
    }

    actual fun realm_list_insert_embedded(list: RealmListPointer, index: Long): RealmObjectPointer {
        return LongPointerWrapper(realmc.realm_list_insert_embedded(list.cptr(), index))
    }
    actual fun realm_list_insert_list(list: RealmListPointer, index: Long): RealmListPointer {
        return LongPointerWrapper(realmc.realm_list_insert_list(list.cptr(), index))
    }
    actual fun realm_list_insert_dictionary(list: RealmListPointer, index: Long): RealmMapPointer {
        return LongPointerWrapper(realmc.realm_list_insert_dictionary(list.cptr(), index))
    }
    actual fun realm_list_set_list(list: RealmListPointer, index: Long): RealmListPointer {
        return LongPointerWrapper(realmc.realm_list_set_list(list.cptr(), index))
    }
    actual fun realm_list_set_dictionary(list: RealmListPointer, index: Long): RealmMapPointer {
        return LongPointerWrapper(realmc.realm_list_set_dictionary(list.cptr(), index))
    }

    actual fun realm_list_set(
        list: RealmListPointer,
        index: Long,
        inputTransport: RealmValue
    ) {
        realmc.realm_list_set(list.cptr(), index, inputTransport.value)
    }

    actual fun MemAllocator.realm_list_set_embedded(
        list: RealmListPointer,
        index: Long
    ): RealmValue {
        val struct = allocRealmValueT()

        // Returns the new object as a Link to follow convention of other getters and allow to
        // reuse the converter infrastructure
        val embedded = realmc.realm_list_set_embedded(list.cptr(), index)
        val link: realm_link_t = realmc.realm_object_as_link(embedded)
        return RealmValue(
            struct.apply {
                this.type = realm_value_type_e.RLM_TYPE_LINK
                this.link = link
            }
        )
    }

    actual fun realm_list_clear(list: RealmListPointer) {
        realmc.realm_list_clear(list.cptr())
    }

    actual fun realm_list_remove_all(list: RealmListPointer) {
        realmc.realm_list_remove_all(list.cptr())
    }

    actual fun realm_list_erase(list: RealmListPointer, index: Long) {
        realmc.realm_list_erase(list.cptr(), index)
    }

    actual fun realm_list_resolve_in(
        list: RealmListPointer,
        realm: RealmPointer
    ): RealmListPointer? {
        val listPointer = longArrayOf(0)
        realmc.realm_list_resolve_in(list.cptr(), realm.cptr(), listPointer)
        return if (listPointer[0] != 0L) {
            LongPointerWrapper(listPointer[0])
        } else {
            null
        }
    }

    actual fun realm_list_is_valid(list: RealmListPointer): Boolean {
        return realmc.realm_list_is_valid(list.cptr())
    }

    actual fun realm_get_set(obj: RealmObjectPointer, key: PropertyKey): RealmSetPointer {
        return LongPointerWrapper(realmc.realm_get_set(obj.cptr(), key.key))
    }

    actual fun realm_set_size(set: RealmSetPointer): Long {
        val size = LongArray(1)
        realmc.realm_set_size(set.cptr(), size)
        return size[0]
    }

    actual fun realm_set_clear(set: RealmSetPointer) {
        realmc.realm_set_clear(set.cptr())
    }

    actual fun realm_set_insert(set: RealmSetPointer, transport: RealmValue): Boolean {
        val size = LongArray(1)
        val inserted = BooleanArray(1)
        realmc.realm_set_insert(set.cptr(), transport.value, size, inserted)
        return inserted[0]
    }

    // See comment in darwin implementation as to why we don't return null just like we do in other
    // functions.
    actual fun MemAllocator.realm_set_get(
        set: RealmSetPointer,
        index: Long
    ): RealmValue {
        val struct = allocRealmValueT()
        realmc.realm_set_get(set.cptr(), index, struct)
        return RealmValue(struct)
    }

    actual fun realm_set_find(set: RealmSetPointer, transport: RealmValue): Boolean {
        val index = LongArray(1)
        val found = BooleanArray(1)
        realmc.realm_set_find(set.cptr(), transport.value, index, found)
        return found[0]
    }

    actual fun realm_set_erase(set: RealmSetPointer, transport: RealmValue): Boolean {
        val erased = BooleanArray(1)
        realmc.realm_set_erase(set.cptr(), transport.value, erased)
        return erased[0]
    }

    actual fun realm_set_remove_all(set: RealmSetPointer) {
        realmc.realm_set_remove_all(set.cptr())
    }

    actual fun realm_set_resolve_in(set: RealmSetPointer, realm: RealmPointer): RealmSetPointer? {
        val setPointer = longArrayOf(0)
        realmc.realm_set_resolve_in(set.cptr(), realm.cptr(), setPointer)
        return if (setPointer[0] != 0L) {
            LongPointerWrapper(setPointer[0])
        } else {
            null
        }
    }

    actual fun realm_set_is_valid(set: RealmSetPointer): Boolean {
        return realmc.realm_set_is_valid(set.cptr())
    }

    actual fun realm_get_dictionary(
        obj: RealmObjectPointer,
        key: PropertyKey
    ): RealmMapPointer {
        val ptr = realmc.realm_get_dictionary(obj.cptr(), key.key)
        return LongPointerWrapper(ptr)
    }

    actual fun realm_dictionary_clear(dictionary: RealmMapPointer) {
        realmc.realm_dictionary_clear(dictionary.cptr())
    }

    actual fun realm_dictionary_size(dictionary: RealmMapPointer): Long {
        val size = LongArray(1)
        realmc.realm_dictionary_size(dictionary.cptr(), size)
        return size[0]
    }

    actual fun realm_dictionary_to_results(
        dictionary: RealmMapPointer
    ): RealmResultsPointer {
        return LongPointerWrapper(realmc.realm_dictionary_to_results(dictionary.cptr()))
    }

    actual fun MemAllocator.realm_dictionary_find(
        dictionary: RealmMapPointer,
        mapKey: RealmValue
    ): RealmValue {
        val found = BooleanArray(1)
        val struct = allocRealmValueT()
        // Core will always return a realm_value_t, even if the value was not found, in which case
        // the type of the struct will be RLM_TYPE_NULL. This way we signal our converters not to
        // worry about nullability and just translate the struct types to their corresponding Kotlin
        // types.
        realmc.realm_dictionary_find(dictionary.cptr(), mapKey.value, struct, found)
        return RealmValue(struct)
    }

    actual fun realm_dictionary_find_list(
        dictionary: RealmMapPointer,
        mapKey: RealmValue
    ): RealmListPointer {
        return LongPointerWrapper(realmc.realm_dictionary_get_list(dictionary.cptr(), mapKey.value))
    }
    actual fun realm_dictionary_find_dictionary(
        dictionary: RealmMapPointer,
        mapKey: RealmValue
    ): RealmMapPointer {
        return LongPointerWrapper(realmc.realm_dictionary_get_dictionary(dictionary.cptr(), mapKey.value))
    }

    actual fun MemAllocator.realm_dictionary_get(
        dictionary: RealmMapPointer,
        pos: Int
    ): Pair<RealmValue, RealmValue> {
        val keyTransport = allocRealmValueT()
        val valueTransport = allocRealmValueT()
        realmc.realm_dictionary_get(dictionary.cptr(), pos.toLong(), keyTransport, valueTransport)
        return Pair(RealmValue(keyTransport), RealmValue(valueTransport))
    }

    actual fun MemAllocator.realm_dictionary_insert(
        dictionary: RealmMapPointer,
        mapKey: RealmValue,
        value: RealmValue
    ): Pair<RealmValue, Boolean> {
        val previousValue = realm_dictionary_find(dictionary, mapKey)
        val index = LongArray(1)
        val inserted = BooleanArray(1)
        realmc.realm_dictionary_insert(dictionary.cptr(), mapKey.value, value.value, index, inserted)
        return Pair(previousValue, inserted[0])
    }

    actual fun MemAllocator.realm_dictionary_erase(
        dictionary: RealmMapPointer,
        mapKey: RealmValue
    ): Pair<RealmValue, Boolean> {
        val previousValue = realm_dictionary_find(dictionary, mapKey)
        val erased = BooleanArray(1)
        realmc.realm_dictionary_erase(dictionary.cptr(), mapKey.value, erased)
        return Pair(previousValue, erased[0])
    }

    actual fun realm_dictionary_contains_key(
        dictionary: RealmMapPointer,
        mapKey: RealmValue
    ): Boolean {
        val found = BooleanArray(1)
        realmc.realm_dictionary_contains_key(dictionary.cptr(), mapKey.value, found)
        return found[0]
    }

    actual fun realm_dictionary_contains_value(
        dictionary: RealmMapPointer,
        value: RealmValue
    ): Boolean {
        val index = LongArray(1)
        realmc.realm_dictionary_contains_value(dictionary.cptr(), value.value, index)
        return index[0] != -1L
    }

    actual fun MemAllocator.realm_dictionary_insert_embedded(
        dictionary: RealmMapPointer,
        mapKey: RealmValue
    ): RealmValue {
        val struct = allocRealmValueT()

        // Returns the new object as a Link to follow convention of other getters and allow to
        // reuse the converter infrastructure
        val embedded = realmc.realm_dictionary_insert_embedded(dictionary.cptr(), mapKey.value)
        val link: realm_link_t = realmc.realm_object_as_link(embedded)
        return RealmValue(
            struct.apply {
                this.type = realm_value_type_e.RLM_TYPE_LINK
                this.link = link
            }
        )
    }

    actual fun realm_dictionary_insert_list(dictionary: RealmMapPointer, mapKey: RealmValue): RealmListPointer {
        return LongPointerWrapper(realmc.realm_dictionary_insert_list(dictionary.cptr(), mapKey.value))
    }

    actual fun realm_dictionary_insert_dictionary(dictionary: RealmMapPointer, mapKey: RealmValue): RealmMapPointer {
        return LongPointerWrapper(realmc.realm_dictionary_insert_dictionary(dictionary.cptr(), mapKey.value))
    }

    actual fun realm_dictionary_get_keys(dictionary: RealmMapPointer): RealmResultsPointer {
        val size = LongArray(1)
        val keysPointer = longArrayOf(0)
        realmc.realm_dictionary_get_keys(dictionary.cptr(), size, keysPointer)
        return if (keysPointer[0] != 0L) {
            LongPointerWrapper(keysPointer[0])
        } else {
            throw IllegalArgumentException("There was an error retrieving the dictionary keys.")
        }
    }

    actual fun realm_dictionary_resolve_in(
        dictionary: RealmMapPointer,
        realm: RealmPointer
    ): RealmMapPointer? {
        val dictionaryPointer = longArrayOf(0)
        realmc.realm_set_resolve_in(dictionary.cptr(), realm.cptr(), dictionaryPointer)
        return if (dictionaryPointer[0] != 0L) {
            LongPointerWrapper(dictionaryPointer[0])
        } else {
            null
        }
    }

    actual fun realm_dictionary_is_valid(dictionary: RealmMapPointer): Boolean {
        return realmc.realm_dictionary_is_valid(dictionary.cptr())
    }

    actual fun realm_create_key_paths_array(realm: RealmPointer, clazz: ClassKey, keyPaths: List<String>): RealmKeyPathArrayPointer {
        val ptr = realmc.realm_create_key_path_array(realm.cptr(), clazz.key, keyPaths.size.toLong(), keyPaths.toTypedArray())
        return LongPointerWrapper(ptr)
    }

    actual fun realm_object_add_notification_callback(
        obj: RealmObjectPointer,
        keyPaths: RealmKeyPathArrayPointer?,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer {

        return LongPointerWrapper(
            realmc.register_notification_cb(
                obj.cptr(),
                CollectionType.RLM_COLLECTION_TYPE_NONE.nativeValue,
                keyPaths?.cptr() ?: NULL_POINTER_VALUE,
                object : NotificationCallback {
                    override fun onChange(pointer: Long) {
                        callback.onChange(LongPointerWrapper(realmc.realm_clone(pointer), true))
                    }
                }
            ),
            managed = false
        )
    }

    actual fun realm_results_add_notification_callback(
        results: RealmResultsPointer,
        keyPaths: RealmKeyPathArrayPointer?,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer {
        return LongPointerWrapper(
            realmc.register_results_notification_cb(
                results.cptr(),
                keyPaths?.cptr() ?: NULL_POINTER_VALUE,
                object : NotificationCallback {
                    override fun onChange(pointer: Long) {
                        callback.onChange(LongPointerWrapper(realmc.realm_clone(pointer), true))
                    }
                }
            ),
            managed = false
        )
    }

    actual fun realm_list_add_notification_callback(
        list: RealmListPointer,
        keyPaths: RealmKeyPathArrayPointer?,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer {
        return LongPointerWrapper(
            realmc.register_notification_cb(
                list.cptr(),
                CollectionType.RLM_COLLECTION_TYPE_LIST.nativeValue,
                keyPaths?.cptr() ?: NULL_POINTER_VALUE,
                object : NotificationCallback {
                    override fun onChange(pointer: Long) {
                        callback.onChange(LongPointerWrapper(realmc.realm_clone(pointer), true))
                    }
                }
            ),
            managed = false
        )
    }

    actual fun realm_set_add_notification_callback(
        set: RealmSetPointer,
        keyPaths: RealmKeyPathArrayPointer?,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer {
        return LongPointerWrapper(
            realmc.register_notification_cb(
                set.cptr(),
                CollectionType.RLM_COLLECTION_TYPE_SET.nativeValue,
                keyPaths?.cptr() ?: NULL_POINTER_VALUE,
                object : NotificationCallback {
                    override fun onChange(pointer: Long) {
                        callback.onChange(LongPointerWrapper(realmc.realm_clone(pointer), true))
                    }
                }
            ),
            managed = false
        )
    }

    actual fun realm_dictionary_add_notification_callback(
        map: RealmMapPointer,
        keyPaths: RealmKeyPathArrayPointer?,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer {
        return LongPointerWrapper(
            realmc.register_notification_cb(
                map.cptr(),
                CollectionType.RLM_COLLECTION_TYPE_DICTIONARY.nativeValue,
                keyPaths?.cptr() ?: NULL_POINTER_VALUE,
                object : NotificationCallback {
                    override fun onChange(pointer: Long) {
                        callback.onChange(LongPointerWrapper(realmc.realm_clone(pointer), true))
                    }
                }
            ),
            managed = false
        )
    }

    actual fun realm_object_changes_get_modified_properties(change: RealmChangesPointer): List<PropertyKey> {
        val propertyCount = realmc.realm_object_changes_get_num_modified_properties(change.cptr())

        val keys = LongArray(propertyCount.toInt())
        realmc.realm_object_changes_get_modified_properties(change.cptr(), keys, propertyCount)
        return keys.map { PropertyKey(it) }
    }

    private fun initIndicesArray(size: LongArray): LongArray = LongArray(size[0].toInt())
    @Suppress("UnusedPrivateMember")
    private fun initRangeArray(size: LongArray): Array<LongArray> = Array(size[0].toInt()) { LongArray(2) }

    actual fun <T, R> realm_collection_changes_get_indices(change: RealmChangesPointer, builder: CollectionChangeSetBuilder<T, R>) {
        val insertionCount = LongArray(1)
        val deletionCount = LongArray(1)
        val modificationCount = LongArray(1)
        val movesCount = LongArray(1)
        // Not exposed in SDK yet, but could be used to provide optimized notifications when
        // collections are cleared.
        //  https://github.com/realm/realm-kotlin/issues/1498
        val collectionWasCleared = BooleanArray(1)
        val collectionWasDeleted = BooleanArray(1)

        realmc.realm_collection_changes_get_num_changes(
            change.cptr(),
            deletionCount,
            insertionCount,
            modificationCount,
            movesCount,
            collectionWasCleared,
            collectionWasDeleted,
        )

        val insertionIndices: LongArray = initIndicesArray(insertionCount)
        val modificationIndices: LongArray = initIndicesArray(modificationCount)
        val modificationIndicesAfter: LongArray = initIndicesArray(modificationCount)
        val deletionIndices: LongArray = initIndicesArray(deletionCount)
        val moves: realm_collection_move_t = realmc.new_collectionMoveArray(movesCount[0].toInt())

        realmc.realm_collection_changes_get_changes(
            change.cptr(),
            deletionIndices,
            deletionCount[0],
            insertionIndices,
            insertionCount[0],
            modificationIndices,
            modificationCount[0],
            modificationIndicesAfter,
            modificationCount[0],
            moves,
            movesCount[0]
        )

        builder.initIndicesArray(builder::insertionIndices, insertionIndices)
        builder.initIndicesArray(builder::deletionIndices, deletionIndices)
        builder.initIndicesArray(builder::modificationIndices, modificationIndices)
        builder.initIndicesArray(builder::modificationIndicesAfter, modificationIndicesAfter)
        builder.movesCount = movesCount[0].toInt()

        realmc.delete_collectionMoveArray(moves)
    }

    actual fun <T, R> realm_collection_changes_get_ranges(
        change: RealmChangesPointer,
        builder: CollectionChangeSetBuilder<T, R>
    ) {
        val insertRangesCount = LongArray(1)
        val deleteRangesCount = LongArray(1)
        val modificationRangesCount = LongArray(1)
        val movesCount = LongArray(1)

        realmc.realm_collection_changes_get_num_ranges(
            change.cptr(),
            deleteRangesCount,
            insertRangesCount,
            modificationRangesCount,
            movesCount
        )

        val insertionRanges: realm_index_range_t =
            realmc.new_indexRangeArray(insertRangesCount[0].toInt())
        val modificationRanges: realm_index_range_t =
            realmc.new_indexRangeArray(modificationRangesCount[0].toInt())
        val modificationRangesAfter: realm_index_range_t =
            realmc.new_indexRangeArray(modificationRangesCount[0].toInt())
        val deletionRanges: realm_index_range_t =
            realmc.new_indexRangeArray(deleteRangesCount[0].toInt())
        val moves: realm_collection_move_t = realmc.new_collectionMoveArray(movesCount[0].toInt())

        realmc.realm_collection_changes_get_ranges(
            change.cptr(),
            deletionRanges,
            deleteRangesCount[0],
            insertionRanges,
            insertRangesCount[0],
            modificationRanges,
            modificationRangesCount[0],
            modificationRangesAfter,
            modificationRangesCount[0],
            moves,
            movesCount[0]
        )

        builder.initRangesArray(builder::deletionRanges, deletionRanges, deleteRangesCount[0])
        builder.initRangesArray(builder::insertionRanges, insertionRanges, insertRangesCount[0])
        builder.initRangesArray(builder::modificationRanges, modificationRanges, modificationRangesCount[0])
        builder.initRangesArray(builder::modificationRangesAfter, modificationRangesAfter, modificationRangesCount[0])

        realmc.delete_indexRangeArray(insertionRanges)
        realmc.delete_indexRangeArray(modificationRanges)
        realmc.delete_indexRangeArray(modificationRangesAfter)
        realmc.delete_indexRangeArray(deletionRanges)
        realmc.delete_collectionMoveArray(moves)
    }

    actual fun <R> realm_dictionary_get_changes(
        change: RealmChangesPointer,
        builder: DictionaryChangeSetBuilder<R>
    ) {
        val deletions = longArrayOf(0)
        val insertions = longArrayOf(0)
        val modifications = longArrayOf(0)
        val collectionWasDeleted = BooleanArray(1)
        realmc.realm_dictionary_get_changes(
            change.cptr(),
            deletions,
            insertions,
            modifications,
            collectionWasDeleted,
        )

        val deletionStructs = realmc.new_valueArray(deletions[0].toInt())
        val insertionStructs = realmc.new_valueArray(insertions[0].toInt())
        val modificationStructs = realmc.new_valueArray(modifications[0].toInt())
        // Not exposed in SDK yet, but could be used to provide optimized notifications when
        // collections are cleared.
        //  https://github.com/realm/realm-kotlin/issues/1498
        val collectionWasCleared = booleanArrayOf(false)
        realmc.realm_dictionary_get_changed_keys(
            change.cptr(),
            deletionStructs,
            deletions,
            insertionStructs,
            insertions,
            modificationStructs,
            modifications,
            collectionWasCleared,
        )

        // TODO optimize - integrate within mem allocator?
        // Get keys and release array of structs
        val deletedKeys = (0 until deletions[0]).map {
            realmc.valueArray_getitem(deletionStructs, it.toInt()).string
        }
        val insertedKeys = (0 until insertions[0]).map {
            realmc.valueArray_getitem(insertionStructs, it.toInt()).string
        }
        val modifiedKeys = (0 until modifications[0]).map {
            realmc.valueArray_getitem(modificationStructs, it.toInt()).string
        }
        realmc.delete_valueArray(deletionStructs)
        realmc.delete_valueArray(insertionStructs)
        realmc.delete_valueArray(modificationStructs)

        builder.initDeletions(deletedKeys.toTypedArray())
        builder.initInsertions(insertedKeys.toTypedArray())
        builder.initModifications(modifiedKeys.toTypedArray())
    }

    actual fun realm_set_log_callback(callback: LogCallback) {
        realmc.set_log_callback(callback)
    }

    actual fun realm_set_log_level(level: CoreLogLevel) {
        realmc.realm_set_log_level(level.priority)
    }

    actual fun realm_set_log_level_category(category: String, level: CoreLogLevel) {
        realmc.realm_set_log_level_category(category, level.priority)
    }

    actual fun realm_get_log_level_category(category: String): CoreLogLevel =
        CoreLogLevel.valueFromPriority(realmc.realm_get_log_level_category(category).toShort())

    actual fun realm_get_category_names(): List<String> {
        @Suppress("UNCHECKED_CAST")
        val names: Array<String> = realmc.realm_get_log_category_names() as Array<String>
        return names.asList()
    }

    private fun classInfo(realm: RealmPointer, className: String): realm_class_info_t {
        val found = booleanArrayOf(false)
        val classInfo = realm_class_info_t()
        realmc.realm_find_class(realm.cptr(), className, found, classInfo)
        if (!found[0]) {
            throw IllegalArgumentException("Cannot find class: '$className'. Has the class been added to the Realm schema?")
        }
        return classInfo
    }

    private fun propertyInfo(realm: RealmPointer, classKey: ClassKey, col: String): realm_property_info_t {
        val found = booleanArrayOf(false)
        val pinfo = realm_property_info_t()
        realmc.realm_find_property(realm.cptr(), classKey.key, col, found, pinfo)
        if (!found[0]) {
            val className = realm_get_class(realm, classKey).name
            throw IllegalArgumentException("Cannot find property: '$col' in class '$className'")
        }
        return pinfo
    }

    actual fun realm_query_parse(
        realm: RealmPointer,
        classKey: ClassKey,
        query: String,
        args: RealmQueryArgumentList,
    ): RealmQueryPointer {
        return LongPointerWrapper(
            realmc.realm_query_parse(
                realm.cptr(),
                classKey.key,
                query,
                args.size,
                args.head
            )
        )
    }

    actual fun realm_query_parse_for_results(
        results: RealmResultsPointer,
        query: String,
        args: RealmQueryArgumentList,
    ): RealmQueryPointer {
        return LongPointerWrapper(
            realmc.realm_query_parse_for_results(
                results.cptr(),
                query,
                args.size,
                args.head
            )
        )
    }

    actual fun realm_query_parse_for_list(
        list: RealmListPointer,
        query: String,
        args: RealmQueryArgumentList,
    ): RealmQueryPointer {
        return LongPointerWrapper(
            realmc.realm_query_parse_for_list(
                list.cptr(),
                query,
                args.size,
                args.head
            )
        )
    }

    actual fun realm_query_parse_for_set(
        set: RealmSetPointer,
        query: String,
        args: RealmQueryArgumentList,
    ): RealmQueryPointer {
        return LongPointerWrapper(
            realmc.realm_query_parse_for_set(
                set.cptr(),
                query,
                args.size,
                args.head
            )
        )
    }

    actual fun realm_query_find_first(query: RealmQueryPointer): Link? {
        val value = realm_value_t()
        val found = booleanArrayOf(false)
        realmc.realm_query_find_first(query.cptr(), value, found)
        if (!found[0]) {
            return null
        }
        if (value.type != realm_value_type_e.RLM_TYPE_LINK) {
            error("Query did not return link but ${value.type}")
        }
        return value.asLink()
    }

    actual fun realm_query_find_all(query: RealmQueryPointer): RealmResultsPointer {
        return LongPointerWrapper(realmc.realm_query_find_all(query.cptr()))
    }

    actual fun realm_query_count(query: RealmQueryPointer): Long {
        val count = LongArray(1)
        realmc.realm_query_count(query.cptr(), count)
        return count[0]
    }

    actual fun realm_query_append_query(
        query: RealmQueryPointer,
        filter: String,
        args: RealmQueryArgumentList,
    ): RealmQueryPointer {
        return LongPointerWrapper(
            realmc.realm_query_append_query(query.cptr(), filter, args.size, args.head)
        )
    }

    actual fun realm_query_get_description(query: RealmQueryPointer): String {
        return realmc.realm_query_get_description(query.cptr())
    }

    actual fun realm_results_get_query(results: RealmResultsPointer): RealmQueryPointer {
        return LongPointerWrapper(realmc.realm_results_get_query(results.cptr()))
    }

    actual fun realm_results_resolve_in(results: RealmResultsPointer, realm: RealmPointer): RealmResultsPointer {
        return LongPointerWrapper(realmc.realm_results_resolve_in(results.cptr(), realm.cptr()))
    }

    actual fun realm_results_count(results: RealmResultsPointer): Long {
        val count = LongArray(1)
        realmc.realm_results_count(results.cptr(), count)
        return count[0]
    }

    actual fun MemAllocator.realm_results_average(
        results: RealmResultsPointer,
        propertyKey: PropertyKey
    ): Pair<Boolean, RealmValue> {
        val struct = allocRealmValueT()
        val found = booleanArrayOf(false)
        realmc.realm_results_average(results.cptr(), propertyKey.key, struct, found)
        return found[0] to RealmValue(struct)
    }

    actual fun MemAllocator.realm_results_sum(
        results: RealmResultsPointer,
        propertyKey: PropertyKey
    ): RealmValue {
        val struct = allocRealmValueT()
        val foundArray = BooleanArray(1)
        realmc.realm_results_sum(results.cptr(), propertyKey.key, struct, foundArray)
        return RealmValue(struct)
    }

    actual fun MemAllocator.realm_results_max(
        results: RealmResultsPointer,
        propertyKey: PropertyKey
    ): RealmValue {
        val struct = allocRealmValueT()
        val foundArray = BooleanArray(1)
        realmc.realm_results_max(results.cptr(), propertyKey.key, struct, foundArray)
        return RealmValue(struct)
    }

    actual fun MemAllocator.realm_results_min(
        results: RealmResultsPointer,
        propertyKey: PropertyKey
    ): RealmValue {
        val struct = allocRealmValueT()
        val foundArray = BooleanArray(1)
        realmc.realm_results_min(results.cptr(), propertyKey.key, struct, foundArray)
        return RealmValue(struct)
    }

    // TODO OPTIMIZE Getting a range
    actual fun MemAllocator.realm_results_get(
        results: RealmResultsPointer,
        index: Long
    ): RealmValue {
        val value = allocRealmValueT()
        realmc.realm_results_get(results.cptr(), index, value)
        return RealmValue(value)
    }

    actual fun realm_results_get_list(results: RealmResultsPointer, index: Long): RealmListPointer =
        LongPointerWrapper(realmc.realm_results_get_list(results.cptr(), index))

    actual fun realm_results_get_dictionary(results: RealmResultsPointer, index: Long): RealmMapPointer =
        LongPointerWrapper(realmc.realm_results_get_dictionary(results.cptr(), index))

    actual fun realm_get_object(realm: RealmPointer, link: Link): RealmObjectPointer {
        return LongPointerWrapper(realmc.realm_get_object(realm.cptr(), link.classKey.key, link.objKey))
    }

    actual fun realm_object_find_with_primary_key(
        realm: RealmPointer,
        classKey: ClassKey,
        transport: RealmValue
    ): RealmObjectPointer? {
        val found = booleanArrayOf(false)
        return nativePointerOrNull(
            realmc.realm_object_find_with_primary_key(
                realm.cptr(),
                classKey.key,
                transport.value,
                found
            )
        )
    }

    actual fun realm_results_delete_all(results: RealmResultsPointer) {
        realmc.realm_results_delete_all(results.cptr())
    }

    actual fun realm_object_delete(obj: RealmObjectPointer) {
        realmc.realm_object_delete(obj.cptr())
    }

    fun <T : CapiT> NativePointer<T>.cptr(): Long {
        return (this as LongPointerWrapper).ptr
    }

    private fun <T : CapiT> nativePointerOrNull(ptr: Long, managed: Boolean = true): NativePointer<T>? {
        return if (ptr != 0L) {
            LongPointerWrapper<T>(ptr, managed)
        } else {
            null
        }
    }
}

fun realm_value_t.asTimestamp(): Timestamp {
    if (this.type != realm_value_type_e.RLM_TYPE_TIMESTAMP) {
        error("Value is not of type Timestamp: $this.type")
    }
    return TimestampImpl(this.timestamp.seconds, this.timestamp.nanoseconds)
}

fun realm_value_t.asLink(): Link {
    if (this.type != realm_value_type_e.RLM_TYPE_LINK) {
        error("Value is not of type link: $this.type")
    }
    return Link(ClassKey(this.link.target_table), this.link.target)
}

fun ObjectId.asRealmObjectIdT(): realm_object_id_t {
    return realm_object_id_t().apply {
        val data = ShortArray(OBJECT_ID_BYTES_SIZE)
        val objectIdBytes = this@asRealmObjectIdT.toByteArray()
        (0 until OBJECT_ID_BYTES_SIZE).map {
            data[it] = objectIdBytes[it].toShort()
        }
        bytes = data
    }
}

private class JVMScheduler(dispatcher: CoroutineDispatcher) {
    val scope: CoroutineScope = CoroutineScope(dispatcher)
    val lock = SynchronizableObject()
    var cancelled = false

    fun notifyCore(schedulerPointer: Long) {
        scope.launch {
            lock.withLock {
                if (!cancelled) {
                    realmc.invoke_core_notify_callback(schedulerPointer)
                }
            }
        }
    }

    fun cancel() {
        lock.withLock {
            cancelled = true
        }
    }
}
