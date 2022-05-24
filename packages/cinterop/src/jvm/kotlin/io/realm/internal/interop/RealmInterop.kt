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

package io.realm.internal.interop

import io.realm.internal.interop.Constants.ENCRYPTION_KEY_LENGTH
import io.realm.internal.interop.RealmInterop.asObjectId
import io.realm.internal.interop.RealmInterop.cptr
import io.realm.internal.interop.sync.AuthProvider
import io.realm.internal.interop.sync.CoreSubscriptionSetState
import io.realm.internal.interop.sync.CoreUserState
import io.realm.internal.interop.sync.JVMSyncSessionTransferCompletionCallback
import io.realm.internal.interop.sync.MetadataMode
import io.realm.internal.interop.sync.NetworkTransport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

// FIXME API-CLEANUP Rename io.realm.interop. to something with platform?
//  https://github.com/realm/realm-kotlin/issues/56

actual val INVALID_CLASS_KEY: ClassKey by lazy { ClassKey(realmc.getRLM_INVALID_CLASS_KEY()) }
actual val INVALID_PROPERTY_KEY: PropertyKey by lazy { PropertyKey(realmc.getRLM_INVALID_PROPERTY_KEY()) }

/**
 * JVM/Android interop implementation.
 *
 * NOTE: All methods that return a boolean to indicate an exception are being checked automatically in JNI.
 * So there is no need to verify the return value in the JVM interop layer.
 */
@Suppress("LargeClass", "FunctionNaming", "TooGenericExceptionCaught")
actual object RealmInterop {

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
        realmc.realm_refresh(realm.cptr())
    }

    actual fun realm_schema_new(schema: List<Pair<ClassInfo, List<PropertyInfo>>>): RealmSchemaPointer {
        val count = schema.size
        val cclasses = realmc.new_classArray(count)
        val cproperties = realmc.new_propertyArrayArray(count)

        for ((i, entry) in schema.withIndex()) {
            val (clazz, properties) = entry
            // Class
            val cclass = realm_class_info_t().apply {
                name = clazz.name
                primary_key = clazz.primaryKey
                num_properties = properties.size.toLong()
                num_computed_properties = 0
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
        return LongPointerWrapper(realmc.realm_schema_new(cclasses, count.toLong(), cproperties))
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

    actual fun realm_config_set_data_initialization_function(config: RealmConfigurationPointer, callback: DataInitializationCallback) {
        realmc.realm_config_set_data_initialization_function(config.cptr(), callback)
    }

    actual fun realm_open(config: RealmConfigurationPointer, dispatcher: CoroutineDispatcher?): Pair<LiveRealmPointer, Boolean> {
        // Configure callback to track if the file was created as part of opening
        var fileCreated = false
        val callback = DataInitializationCallback {
            fileCreated = true
            true
        }
        realm_config_set_data_initialization_function(config, callback)

        // create a custom Scheduler for JVM if a Coroutine Dispatcher is provided other wise
        // pass null to use the generic one
        val realmPtr = LongPointerWrapper<LiveRealmT>(
            realmc.open_realm_with_scheduler(
                (config as LongPointerWrapper).ptr,
                if (dispatcher != null) JVMScheduler(dispatcher) else null
            )
        )
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
    }

    actual fun realm_release(p: RealmNativePointer) {
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

    actual fun realm_object_create_with_primary_key(realm: LiveRealmPointer, classKey: ClassKey, primaryKey: RealmValue): RealmObjectPointer {
        return LongPointerWrapper(realmc.realm_object_create_with_primary_key(realm.cptr(), classKey.key, to_realm_value(primaryKey)))
    }
    actual fun realm_object_get_or_create_with_primary_key(realm: LiveRealmPointer, classKey: ClassKey, primaryKey: RealmValue): RealmObjectPointer {
        val created = booleanArrayOf(false)
        return LongPointerWrapper(realmc.realm_object_get_or_create_with_primary_key(realm.cptr(), classKey.key, to_realm_value(primaryKey), created))
    }

    actual fun realm_object_is_valid(obj: RealmObjectPointer): Boolean {
        return realmc.realm_object_is_valid(obj.cptr())
    }

    actual fun realm_object_get_key(obj: RealmObjectPointer): Long {
        return realmc.realm_object_get_key(obj.cptr())
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

    actual fun realm_get_value(obj: RealmObjectPointer, key: PropertyKey): RealmValue {
        // TODO OPTIMIZED Consider optimizing this to construct T in JNI call
        val cvalue = realm_value_t()
        realmc.realm_get_value((obj as LongPointerWrapper).ptr, key.key, cvalue)
        return from_realm_value(cvalue)
    }

    private fun from_realm_value(value: realm_value_t): RealmValue {
        return RealmValue(
            when (value?.type) {
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
                realm_value_type_e.RLM_TYPE_TIMESTAMP ->
                    value.asTimestamp()
                realm_value_type_e.RLM_TYPE_OBJECT_ID ->
                    value.asObjectId()
                realm_value_type_e.RLM_TYPE_LINK ->
                    value.asLink()
                realm_value_type_e.RLM_TYPE_NULL ->
                    null
                else ->
                    TODO("Unsupported type for from_realm_value ${value.type}")
            }
        )
    }

    actual fun realm_set_value(obj: RealmObjectPointer, key: PropertyKey, value: RealmValue, isDefault: Boolean) {
        val cvalue = to_realm_value(value)
        realmc.realm_set_value(obj.cptr(), key.key, cvalue, isDefault)
    }

    actual fun realm_get_list(obj: RealmObjectPointer, key: PropertyKey): RealmListPointer {
        return LongPointerWrapper(
            realmc.realm_get_list(
                (obj as LongPointerWrapper).ptr,
                key.key
            )
        )
    }

    actual fun realm_list_size(list: RealmListPointer): Long {
        val size = LongArray(1)
        realmc.realm_list_size(list.cptr(), size)
        return size[0]
    }

    actual fun realm_list_get(list: RealmListPointer, index: Long): RealmValue {
        val cvalue = realm_value_t()
        realmc.realm_list_get(list.cptr(), index, cvalue)
        return from_realm_value(cvalue)
    }

    actual fun realm_list_add(list: RealmListPointer, index: Long, value: RealmValue) {
        val cvalue = to_realm_value(value)
        realmc.realm_list_insert(list.cptr(), index, cvalue)
    }

    actual fun realm_list_set(list: RealmListPointer, index: Long, value: RealmValue): RealmValue {
        return realm_list_get(list, index).also {
            realmc.realm_list_set(list.cptr(), index, to_realm_value(value))
        }
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

    // TODO OPTIMIZE Maybe move this to JNI to avoid multiple round trips for allocating and
    //  updating before actually calling
    @Suppress("ComplexMethod", "LongMethod")
    private fun to_realm_value(realmValue: RealmValue): realm_value_t {
        val cvalue = realm_value_t()
        val value = realmValue.value
        if (value == null) {
            cvalue.type = realm_value_type_e.RLM_TYPE_NULL
        } else {
            when (value) {
                is String -> {
                    cvalue.type = realm_value_type_e.RLM_TYPE_STRING
                    cvalue.string = value
                }
                /*is Byte -> {
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
                }*/
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
                is Timestamp -> {
                    cvalue.type = realm_value_type_e.RLM_TYPE_TIMESTAMP
                    cvalue.timestamp = realm_timestamp_t().apply {
                        seconds = value.seconds
                        nanoseconds = value.nanoSeconds
                    }
                }
                is ObjectIdWrapper -> {
                    cvalue.type = realm_value_type_e.RLM_TYPE_OBJECT_ID
                    cvalue.object_id = realm_object_id_t().apply {
                        val data = ShortArray(OBJECT_ID_BYTES_SIZE)
                        @OptIn(ExperimentalUnsignedTypes::class)
                        (0 until OBJECT_ID_BYTES_SIZE).map {
                            data[it] = value.bytes[it].toShort()
                        }
                        bytes = data
                    }
                }
                is RealmObjectInterop -> {
                    val nativePointer = value.objectPointer
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

    actual fun realm_object_add_notification_callback(obj: RealmObjectPointer, callback: Callback<RealmChangesPointer>): RealmNotificationTokenPointer {
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

    actual fun realm_results_add_notification_callback(results: RealmResultsPointer, callback: Callback<RealmChangesPointer>): RealmNotificationTokenPointer {
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
        list: RealmListPointer,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer {
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

    actual fun realm_object_changes_get_modified_properties(change: RealmChangesPointer): List<PropertyKey> {
        val propertyCount = realmc.realm_object_changes_get_num_modified_properties(change.cptr())

        val keys = LongArray(propertyCount.toInt())
        realmc.realm_object_changes_get_modified_properties(change.cptr(), keys, propertyCount)
        return keys.map { PropertyKey(it) }
    }

    private fun initIndicesArray(size: LongArray): LongArray = LongArray(size[0].toInt())
    private fun initRangeArray(size: LongArray): Array<LongArray> = Array(size[0].toInt()) { LongArray(2) }

    actual fun <T, R> realm_collection_changes_get_indices(change: RealmChangesPointer, builder: ListChangeSetBuilder<T, R>) {
        val insertionCount = LongArray(1)
        val deletionCount = LongArray(1)
        val modificationCount = LongArray(1)
        val movesCount = LongArray(1)

        realmc.realm_collection_changes_get_num_changes(
            change.cptr(),
            deletionCount,
            insertionCount,
            modificationCount,
            movesCount
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
    }

    actual fun <T, R> realm_collection_changes_get_ranges(change: RealmChangesPointer, builder: ListChangeSetBuilder<T, R>) {
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
    }

    actual fun realm_app_get(
        appConfig: RealmAppConfigurationPointer,
        syncClientConfig: RealmSyncClientConfigurationPointer,
        basePath: String
    ): RealmAppPointer {
        return LongPointerWrapper(realmc.realm_app_get(appConfig.cptr(), syncClientConfig.cptr()))
    }

    actual fun realm_app_log_in_with_credentials(
        app: RealmAppPointer,
        credentials: RealmCredentialsPointer,
        callback: AppCallback<RealmUserPointer>
    ) {
        realmc.realm_app_log_in_with_credentials(app.cptr(), credentials.cptr(), callback)
    }

    actual fun realm_app_log_out(
        app: RealmAppPointer,
        user: RealmUserPointer,
        callback: AppCallback<Unit>
    ) {
        realmc.realm_app_log_out(app.cptr(), user.cptr(), callback)
    }

    actual fun realm_app_remove_user(
        app: RealmAppPointer,
        user: RealmUserPointer,
        callback: AppCallback<Unit>
    ) {
        realmc.realm_app_remove_user(app.cptr(), user.cptr(), callback)
    }

    actual fun realm_app_get_current_user(app: RealmAppPointer): RealmUserPointer? {
        val ptr = realmc.realm_app_get_current_user(app.cptr())
        return nativePointerOrNull(ptr)
    }

    actual fun realm_app_get_all_users(app: RealmAppPointer): List<RealmUserPointer> {
        // We get the current amount of users by providing a zero-sized array and `out_n`
        // argument. Then the current count is written to `out_n`.
        // See https://github.com/realm/realm-core/blob/master/src/realm.h#L2634
        val capacityCount = LongArray(1)
        realmc.realm_app_get_all_users(app.cptr(), LongArray(0), 0, capacityCount)

        // Read actual users. We don't care about the small chance of missing a new user
        // between these two calls as that indicate two sections of user code running on
        // different threads and not coordinating.
        val actualUsersCount = LongArray(1)
        val users = LongArray(capacityCount[0].toInt())
        realmc.realm_app_get_all_users(app.cptr(), users, capacityCount[0], actualUsersCount)
        val result: MutableList<RealmUserPointer> = mutableListOf()
        for (i in 0 until actualUsersCount[0].toInt()) {
            users[i].let { ptr: Long ->
                result.add(LongPointerWrapper(ptr, managed = true))
            }
        }
        return result
    }

    actual fun realm_user_get_identity(user: RealmUserPointer): String {
        return realmc.realm_user_get_identity(user.cptr())
    }

    actual fun realm_user_is_logged_in(user: RealmUserPointer): Boolean {
        return realmc.realm_user_is_logged_in(user.cptr())
    }

    actual fun realm_user_log_out(user: RealmUserPointer) {
        realmc.realm_user_log_out(user.cptr())
    }

    actual fun realm_user_get_state(user: RealmUserPointer): CoreUserState {
        return CoreUserState.of(realmc.realm_user_get_state(user.cptr()))
    }

    actual fun realm_clear_cached_apps() {
        realmc.realm_clear_cached_apps()
    }

    actual fun realm_app_sync_client_get_default_file_path_for_realm(
        app: RealmAppPointer,
        syncConfig: RealmSyncConfigurationPointer,
        overriddenName: String?
    ): String {
        return realmc.realm_app_sync_client_get_default_file_path_for_realm(
            syncConfig.cptr(),
            overriddenName
        )
    }

    actual fun realm_sync_client_config_new(): RealmSyncClientConfigurationPointer {
        return LongPointerWrapper(realmc.realm_sync_client_config_new())
    }

    actual fun realm_sync_client_config_set_base_file_path(
        syncClientConfig: RealmSyncClientConfigurationPointer,
        basePath: String
    ) {
        realmc.realm_sync_client_config_set_base_file_path(syncClientConfig.cptr(), basePath)
    }

    actual fun realm_sync_client_config_set_log_callback(
        syncClientConfig: RealmSyncClientConfigurationPointer,
        callback: SyncLogCallback
    ) {
        realmc.set_log_callback(syncClientConfig.cptr(), callback)
    }

    actual fun realm_sync_client_config_set_log_level(
        syncClientConfig: RealmSyncClientConfigurationPointer,
        level: CoreLogLevel
    ) {
        realmc.realm_sync_client_config_set_log_level(syncClientConfig.cptr(), level.priority)
    }

    actual fun realm_sync_client_config_set_metadata_mode(
        syncClientConfig: RealmSyncClientConfigurationPointer,
        metadataMode: MetadataMode
    ) {
        realmc.realm_sync_client_config_set_metadata_mode(
            syncClientConfig.cptr(),
            metadataMode.nativeValue
        )
    }

    actual fun realm_network_transport_new(networkTransport: NetworkTransport): RealmNetworkTransportPointer {
        return LongPointerWrapper(realmc.realm_network_transport_new(networkTransport))
    }

    actual fun realm_sync_config_set_error_handler(
        syncConfig: RealmSyncConfigurationPointer,
        errorHandler: SyncErrorCallback
    ) {
        realmc.sync_set_error_handler(syncConfig.cptr(), errorHandler)
    }

    actual fun realm_sync_session_get(realm: RealmPointer): RealmSyncSessionPointer {
        return LongPointerWrapper(realmc.realm_sync_session_get(realm.cptr()))
    }

    actual fun realm_sync_session_wait_for_download_completion(
        syncSession: RealmSyncSessionPointer,
        callback: SyncSessionTransferCompletionCallback
    ) {
        realmc.realm_sync_session_wait_for_download_completion(
            syncSession.cptr(),
            JVMSyncSessionTransferCompletionCallback(callback)
        )
    }

    actual fun realm_sync_session_wait_for_upload_completion(
        syncSession: RealmSyncSessionPointer,
        callback: SyncSessionTransferCompletionCallback
    ) {
        realmc.realm_sync_session_wait_for_upload_completion(
            syncSession.cptr(),
            JVMSyncSessionTransferCompletionCallback(callback)
        )
    }

    @Suppress("LongParameterList")
    actual fun realm_app_config_new(
        appId: String,
        networkTransport: RealmNetworkTransportPointer,
        baseUrl: String?,
        platform: String,
        platformVersion: String,
        sdkVersion: String,
    ): RealmAppConfigurationPointer {
        val config = realmc.realm_app_config_new(appId, networkTransport.cptr())

        baseUrl?.let { realmc.realm_app_config_set_base_url(config, it) }

        realmc.realm_app_config_set_platform(config, platform)
        realmc.realm_app_config_set_platform_version(config, platformVersion)
        realmc.realm_app_config_set_sdk_version(config, sdkVersion)

        // TODO Fill in appropriate app meta data
        //  https://github.com/realm/realm-kotlin/issues/407
        realmc.realm_app_config_set_local_app_version(config, "APP_VERSION")
        return LongPointerWrapper(config)
    }

    actual fun realm_app_config_set_base_url(appConfig: RealmAppConfigurationPointer, baseUrl: String) {
        realmc.realm_app_config_set_base_url(appConfig.cptr(), baseUrl)
    }

    actual fun realm_app_credentials_new_anonymous(): RealmCredentialsPointer {
        return LongPointerWrapper(realmc.realm_app_credentials_new_anonymous())
    }

    actual fun realm_app_credentials_new_email_password(username: String, password: String): RealmCredentialsPointer {
        return LongPointerWrapper(realmc.realm_app_credentials_new_email_password(username, password))
    }

    actual fun realm_app_credentials_new_api_key(key: String): RealmCredentialsPointer {
        return LongPointerWrapper(realmc.realm_app_credentials_new_user_api_key(key))
    }

    actual fun realm_app_credentials_new_apple(idToken: String): RealmCredentialsPointer {
        return LongPointerWrapper(realmc.realm_app_credentials_new_apple(idToken))
    }

    actual fun realm_app_credentials_new_facebook(accessToken: String): RealmCredentialsPointer {
        return LongPointerWrapper(realmc.realm_app_credentials_new_facebook(accessToken))
    }

    actual fun realm_app_credentials_new_google_id_token(idToken: String): RealmCredentialsPointer {
        return LongPointerWrapper(realmc.realm_app_credentials_new_google_id_token(idToken))
    }

    actual fun realm_app_credentials_new_google_auth_code(authCode: String): RealmCredentialsPointer {
        return LongPointerWrapper(realmc.realm_app_credentials_new_google_auth_code(authCode))
    }

    actual fun realm_app_credentials_new_jwt(jwtToken: String): RealmCredentialsPointer {
        return LongPointerWrapper(realmc.realm_app_credentials_new_jwt(jwtToken))
    }

    actual fun realm_auth_credentials_get_provider(credentials: RealmCredentialsPointer): AuthProvider {
        return AuthProvider.of(realmc.realm_auth_credentials_get_provider(credentials.cptr()))
    }

    actual fun realm_app_credentials_serialize_as_json(credentials: RealmCredentialsPointer): String {
        return realmc.realm_app_credentials_serialize_as_json(credentials.cptr())
    }

    actual fun realm_app_email_password_provider_client_register_email(
        app: RealmAppPointer,
        email: String,
        password: String,
        callback: AppCallback<Unit>
    ) {
        realmc.realm_app_email_password_provider_client_register_email(
            app.cptr(),
            email,
            password,
            callback
        )
    }

    actual fun realm_app_email_password_provider_client_confirm_user(
        app: RealmAppPointer,
        token: String,
        tokenId: String,
        callback: AppCallback<Unit>
    ) {
        realmc.realm_app_email_password_provider_client_confirm_user(
            app.cptr(),
            token,
            tokenId,
            callback
        )
    }

    actual fun realm_app_email_password_provider_client_resend_confirmation_email(
        app: RealmAppPointer,
        email: String,
        callback: AppCallback<Unit>
    ) {
        realmc.realm_app_email_password_provider_client_resend_confirmation_email(
            app.cptr(),
            email,
            callback
        )
    }

    actual fun realm_app_email_password_provider_client_retry_custom_confirmation(
        app: RealmAppPointer,
        email: String,
        callback: AppCallback<Unit>
    ) {
        realmc.realm_app_email_password_provider_client_retry_custom_confirmation(
            app.cptr(),
            email,
            callback
        )
    }

    actual fun realm_app_email_password_provider_client_send_reset_password_email(
        app: RealmAppPointer,
        email: String,
        callback: AppCallback<Unit>
    ) {
        realmc.realm_app_email_password_provider_client_send_reset_password_email(
            app.cptr(),
            email,
            callback
        )
    }

    actual fun realm_app_email_password_provider_client_reset_password(
        app: RealmAppPointer,
        token: String,
        tokenId: String,
        newPassword: String,
        callback: AppCallback<Unit>
    ) {
        realmc.realm_app_email_password_provider_client_reset_password(
            app.cptr(),
            token,
            tokenId,
            newPassword,
            callback
        )
    }

    actual fun realm_sync_config_new(user: RealmUserPointer, partition: String): RealmSyncConfigurationPointer {
        return LongPointerWrapper(realmc.realm_sync_config_new(user.cptr(), partition))
    }

    actual fun realm_config_set_sync_config(realmConfiguration: RealmConfigurationPointer, syncConfiguration: RealmSyncConfigurationPointer) {
        realmc.realm_config_set_sync_config(realmConfiguration.cptr(), syncConfiguration.cptr())
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

    actual fun realm_query_parse(realm: RealmPointer, classKey: ClassKey, query: String, args: Array<RealmValue>): RealmQueryPointer {
        val count = args.size
        val cArgs = realmc.new_valueArray(count)
        args.mapIndexed { i, arg ->
            realmc.valueArray_setitem(cArgs, i, to_realm_value(arg))
        }
        return LongPointerWrapper(realmc.realm_query_parse(realm.cptr(), classKey.key, query, count.toLong(), cArgs))
    }

    actual fun realm_query_parse_for_results(
        results: RealmResultsPointer,
        query: String,
        args: Array<RealmValue>
    ): RealmQueryPointer {
        val count = args.size
        val cArgs = realmc.new_valueArray(count)
        args.mapIndexed { i, arg ->
            realmc.valueArray_setitem(cArgs, i, to_realm_value(arg))
        }
        return LongPointerWrapper(
            realmc.realm_query_parse_for_results(results.cptr(), query, count.toLong(), cArgs)
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
        args: Array<RealmValue>
    ): RealmQueryPointer {
        val count = args.size
        val cArgs = realmc.new_valueArray(count)
        args.mapIndexed { i, arg ->
            realmc.valueArray_setitem(cArgs, i, to_realm_value(arg))
        }
        return LongPointerWrapper(
            realmc.realm_query_append_query(query.cptr(), filter, count.toLong(), cArgs)
        )
    }

    actual fun realm_query_get_description(query: RealmQueryPointer): String {
        return realmc.realm_query_get_description(query.cptr())
    }

    actual fun realm_results_resolve_in(results: RealmResultsPointer, realm: RealmPointer): RealmResultsPointer {
        return LongPointerWrapper(realmc.realm_results_resolve_in(results.cptr(), realm.cptr()))
    }

    actual fun realm_results_count(results: RealmResultsPointer): Long {
        val count = LongArray(1)
        realmc.realm_results_count(results.cptr(), count)
        return count[0]
    }

    actual fun realm_results_average(
        results: RealmResultsPointer,
        propertyKey: PropertyKey
    ): Pair<Boolean, RealmValue> {
        val average = realm_value_t()
        val found = booleanArrayOf(false)
        realmc.realm_results_average(results.cptr(), propertyKey.key, average, found)
        return found[0] to from_realm_value(average)
    }

    actual fun realm_results_sum(results: RealmResultsPointer, propertyKey: PropertyKey): RealmValue {
        val sum = realm_value_t()
        val foundArray = BooleanArray(1)
        realmc.realm_results_sum(results.cptr(), propertyKey.key, sum, foundArray)
        return from_realm_value(sum)
    }

    actual fun realm_results_max(results: RealmResultsPointer, propertyKey: PropertyKey): RealmValue {
        val max = realm_value_t()
        val foundArray = BooleanArray(1)
        realmc.realm_results_max(results.cptr(), propertyKey.key, max, foundArray)
        return from_realm_value(max)
    }

    actual fun realm_results_min(results: RealmResultsPointer, propertyKey: PropertyKey): RealmValue {
        val min = realm_value_t()
        val foundArray = BooleanArray(1)
        realmc.realm_results_min(results.cptr(), propertyKey.key, min, foundArray)
        return from_realm_value(min)
    }

    // TODO OPTIMIZE Getting a range
    actual fun realm_results_get(results: RealmResultsPointer, index: Long): Link {
        val value = realm_value_t()
        realmc.realm_results_get(results.cptr(), index, value)
        return value.asLink()
    }

    actual fun realm_get_object(realm: RealmPointer, link: Link): RealmObjectPointer {
        return LongPointerWrapper(realmc.realm_get_object(realm.cptr(), link.classKey.key, link.objKey))
    }

    actual fun realm_object_find_with_primary_key(realm: RealmPointer, classKey: ClassKey, primaryKey: RealmValue): RealmObjectPointer? {
        val cprimaryKey = to_realm_value(primaryKey)
        val found = booleanArrayOf(false)
        return nativePointerOrNull(realmc.realm_object_find_with_primary_key(realm.cptr(), classKey.key, cprimaryKey, found))
    }

    actual fun realm_results_delete_all(results: RealmResultsPointer) {
        realmc.realm_results_delete_all(results.cptr())
    }

    actual fun realm_object_delete(obj: RealmObjectPointer) {
        realmc.realm_object_delete(obj.cptr())
    }

    actual fun realm_flx_sync_config_new(user: RealmUserPointer): RealmSyncConfigurationPointer {
        return LongPointerWrapper(realmc.realm_flx_sync_config_new(user.cptr()))
    }

    actual fun realm_sync_subscription_id(subscription: RealmSubscriptionPointer): ObjectIdWrapper {
        val nativeBytes: ShortArray = realmc.realm_sync_subscription_id(subscription.cptr()).bytes
        val byteArray = ByteArray(nativeBytes.size)
        nativeBytes.mapIndexed { index, b -> byteArray[index] = b.toByte() }
        return ObjectIdWrapperImpl(byteArray)
    }

    actual fun realm_sync_subscription_name(subscription: RealmSubscriptionPointer): String? {
        return realmc.realm_sync_subscription_name(subscription.cptr())
    }

    actual fun realm_sync_subscription_object_class_name(subscription: RealmSubscriptionPointer): String {
        return realmc.realm_sync_subscription_object_class_name(subscription.cptr())
    }

    actual fun realm_sync_subscription_query_string(subscription: RealmSubscriptionPointer): String {
        return realmc.realm_sync_subscription_query_string(subscription.cptr())
    }

    actual fun realm_sync_subscription_created_at(subscription: RealmSubscriptionPointer): Timestamp {
        val ts: realm_timestamp_t = realmc.realm_sync_subscription_created_at(subscription.cptr())
        return TimestampImpl(ts.seconds, ts.nanoseconds)
    }

    actual fun realm_sync_subscription_updated_at(subscription: RealmSubscriptionPointer): Timestamp {
        val ts: realm_timestamp_t = realmc.realm_sync_subscription_updated_at(subscription.cptr())
        return TimestampImpl(ts.seconds, ts.nanoseconds)
    }

    actual fun realm_sync_get_latest_subscriptionset(realm: RealmPointer): RealmSubscriptionSetPointer {
        return LongPointerWrapper(realmc.realm_sync_get_latest_subscription_set(realm.cptr()))
    }

    actual fun realm_sync_on_subscriptionset_state_change_async(
        subscriptionSet: RealmSubscriptionSetPointer,
        destinationState: CoreSubscriptionSetState,
        callback: SubscriptionSetCallback
    ) {
        val jvmWrapper: (Int) -> Any = { value: Int ->
            callback.onChange(CoreSubscriptionSetState.of(value))
        }
        realmc.realm_sync_on_subscription_set_state_change_async(
            subscriptionSet.cptr(),
            destinationState.nativeValue,
            jvmWrapper
        )
    }

    actual fun realm_sync_subscriptionset_version(subscriptionSet: RealmBaseSubscriptionSetPointer): Long {
        return realmc.realm_sync_subscription_set_version(subscriptionSet.cptr())
    }

    actual fun realm_sync_subscriptionset_state(subscriptionSet: RealmBaseSubscriptionSetPointer): CoreSubscriptionSetState {
        return CoreSubscriptionSetState.of(realmc.realm_sync_subscription_set_state(subscriptionSet.cptr()))
    }

    actual fun realm_sync_subscriptionset_error_str(subscriptionSet: RealmBaseSubscriptionSetPointer): String? {
        return realmc.realm_sync_subscription_set_error_str(subscriptionSet.cptr())
    }

    actual fun realm_sync_subscriptionset_size(subscriptionSet: RealmBaseSubscriptionSetPointer): Long {
        return realmc.realm_sync_subscription_set_size(subscriptionSet.cptr())
    }

    actual fun realm_sync_subscription_at(
        subscriptionSet: RealmBaseSubscriptionSetPointer,
        index: Long
    ): RealmSubscriptionPointer {
        return LongPointerWrapper(realmc.realm_sync_subscription_at(subscriptionSet.cptr(), index))
    }

    actual fun realm_sync_find_subscription_by_name(
        subscriptionSet: RealmBaseSubscriptionSetPointer,
        name: String
    ): RealmSubscriptionPointer? {
        val ptr = realmc.realm_sync_find_subscription_by_name(subscriptionSet.cptr(), name)
        return nativePointerOrNull(ptr)
    }

    actual fun realm_sync_find_subscription_by_query(
        subscriptionSet: RealmBaseSubscriptionSetPointer,
        query: RealmQueryPointer
    ): RealmSubscriptionPointer? {
        val ptr = realmc.realm_sync_find_subscription_by_query(subscriptionSet.cptr(), query.cptr())
        return nativePointerOrNull(ptr)
    }

    actual fun realm_sync_subscriptionset_refresh(subscriptionSet: RealmSubscriptionSetPointer): Boolean {
        return realmc.realm_sync_subscription_set_refresh(subscriptionSet.cptr())
    }

    actual fun realm_sync_make_subscriptionset_mutable(
        subscriptionSet: RealmSubscriptionSetPointer
    ): RealmMutableSubscriptionSetPointer {
        return LongPointerWrapper(
            realmc.realm_sync_make_subscription_set_mutable(subscriptionSet.cptr()),
            managed = false
        )
    }

    actual fun realm_sync_subscriptionset_clear(
        mutableSubscriptionSet: RealmMutableSubscriptionSetPointer
    ): Boolean {
        val erased = realmc.realm_sync_subscription_set_size(mutableSubscriptionSet.cptr()) > 0
        realmc.realm_sync_subscription_set_clear(mutableSubscriptionSet.cptr())
        return erased
    }

    actual fun realm_sync_subscriptionset_insert_or_assign(
        mutatableSubscriptionSet: RealmMutableSubscriptionSetPointer,
        query: RealmQueryPointer,
        name: String?
    ): Pair<RealmSubscriptionPointer, Boolean> {
        val outIndex = longArrayOf(1)
        val outInserted = BooleanArray(1)
        realmc.realm_sync_subscription_set_insert_or_assign_query(
            mutatableSubscriptionSet.cptr(),
            query.cptr(),
            name,
            outIndex,
            outInserted
        )
        return Pair(
            realm_sync_subscription_at(
                mutatableSubscriptionSet as RealmSubscriptionSetPointer,
                outIndex[0]
            ),
            outInserted[0]
        )
    }

    actual fun realm_sync_subscriptionset_erase_by_name(
        mutableSubscriptionSet: RealmMutableSubscriptionSetPointer,
        name: String
    ): Boolean {
        val erased = BooleanArray(1)
        realmc.realm_sync_subscription_set_erase_by_name(
            mutableSubscriptionSet.cptr(),
            name,
            erased
        )
        return erased[0]
    }

    actual fun realm_sync_subscriptionset_erase_by_query(
        mutableSubscriptionSet: RealmMutableSubscriptionSetPointer,
        query: RealmQueryPointer
    ): Boolean {
        val erased = BooleanArray(1)
        realmc.realm_sync_subscription_set_erase_by_query(
            mutableSubscriptionSet.cptr(),
            query.cptr(),
            erased
        )
        return erased[0]
    }

    actual fun realm_sync_subscriptionset_erase_by_id(
        mutableSubscriptionSet: RealmMutableSubscriptionSetPointer,
        sub: RealmSubscriptionPointer
    ): Boolean {
        val id = realmc.realm_sync_subscription_id(sub.cptr())
        val erased = BooleanArray(1)
        realmc.realm_sync_subscription_set_erase_by_id(
            mutableSubscriptionSet.cptr(),
            id,
            erased
        )
        return erased[0]
    }

    actual fun realm_sync_subscriptionset_commit(
        mutableSubscriptionSet: RealmMutableSubscriptionSetPointer
    ): RealmSubscriptionSetPointer {
        return LongPointerWrapper(realmc.realm_sync_subscription_set_commit(mutableSubscriptionSet.cptr()))
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

    private fun realm_value_t.asTimestamp(): Timestamp {
        if (this.type != realm_value_type_e.RLM_TYPE_TIMESTAMP) {
            error("Value is not of type Timestamp: $this.type")
        }
        return TimestampImpl(this.timestamp.seconds, this.timestamp.nanoseconds)
    }

    private fun realm_value_t.asObjectId(): ObjectIdWrapper {
        if (this.type != realm_value_type_e.RLM_TYPE_OBJECT_ID) {
            error("Value is not of type ObjectId: $this.type")
        }
        val byteArray = ByteArray(OBJECT_ID_BYTES_SIZE)
        this.object_id.bytes.mapIndexed { index, b -> byteArray[index] = b.toByte() }
        return ObjectIdWrapperImpl(byteArray)
    }

    private fun realm_value_t.asLink(): Link {
        if (this.type != realm_value_type_e.RLM_TYPE_LINK) {
            error("Value is not of type link: $this.type")
        }
        return Link(ClassKey(this.link.target_table), this.link.target)
    }
}

private class JVMScheduler(dispatcher: CoroutineDispatcher) {
    val scope: CoroutineScope = CoroutineScope(dispatcher)

    fun notifyCore(schedulerPointer: Long) {
        val function: suspend CoroutineScope.() -> Unit = {
            realmc.invoke_core_notify_callback(schedulerPointer)
        }
        scope.launch(
            scope.coroutineContext,
            CoroutineStart.DEFAULT,
            function
        )
    }
}

// using https://developer.android.com/reference/java/lang/System#getProperties()
private fun isAndroid(): Boolean =
    System.getProperty("java.specification.vendor")?.contains("Android") ?: false
