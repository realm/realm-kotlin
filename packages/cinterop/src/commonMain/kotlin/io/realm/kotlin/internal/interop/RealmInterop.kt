@file:JvmMultifileClass
@file:JvmName("RealmInteropJvm")
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

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.jvm.JvmInline
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

// Wrapper for the C-API realm_class_key_t uniquely identifying the class/table in the schema
@JvmInline
value class ClassKey(val key: Long)
// Wrapper for the C-API realm_property_key_t uniquely identifying the property within a class/table
@JvmInline
value class PropertyKey(val key: Long)
// Wrapper for the C-API realm_object_key_t uniquely identifying an object within a class/table
@JvmInline
value class ObjectKey(val key: Long)

// Constants for invalid keys
expect val INVALID_CLASS_KEY: ClassKey
expect val INVALID_PROPERTY_KEY: PropertyKey

const val OBJECT_ID_BYTES_SIZE = 12
const val UUID_BYTES_SIZE = 16

const val INDEX_NOT_FOUND = -1L

// Pure marker interfaces corresponding to the C-API realm_x_t struct types
interface CapiT
interface RealmConfigT : CapiT
interface RealmSchemaT : CapiT
interface RealmT : CapiT
interface LiveRealmT : RealmT
interface FrozenRealmT : RealmT
interface RealmObjectT : CapiT
interface RealmListT : CapiT
interface RealmSetT : CapiT
interface RealmMapT : CapiT
interface RealmResultsT : CapiT
interface RealmQueryT : CapiT
interface RealmCallbackTokenT : CapiT
interface RealmNotificationTokenT : CapiT
interface RealmChangesT : CapiT
interface RealmSchedulerT : CapiT
interface RealmKeyPathArrayT : CapiT

// Public type aliases binding to internal verbose type safe type definitions. This should allow us
// to easily change implementation details later on.
typealias RealmNativePointer = NativePointer<out CapiT>
typealias RealmConfigurationPointer = NativePointer<RealmConfigT>
typealias RealmSchemaPointer = NativePointer<RealmSchemaT>
typealias RealmPointer = NativePointer<out RealmT>
typealias LiveRealmPointer = NativePointer<LiveRealmT>
typealias FrozenRealmPointer = NativePointer<FrozenRealmT>
typealias RealmObjectPointer = NativePointer<RealmObjectT>
typealias RealmListPointer = NativePointer<RealmListT>
typealias RealmSetPointer = NativePointer<RealmSetT>
typealias RealmMapPointer = NativePointer<RealmMapT>
typealias RealmResultsPointer = NativePointer<RealmResultsT>
typealias RealmQueryPointer = NativePointer<RealmQueryT>
typealias RealmCallbackTokenPointer = NativePointer<RealmCallbackTokenT>
typealias RealmNotificationTokenPointer = NativePointer<RealmNotificationTokenT>
typealias RealmChangesPointer = NativePointer<RealmChangesT>
typealias RealmSchedulerPointer = NativePointer<RealmSchedulerT>
typealias RealmKeyPathArrayPointer = NativePointer<RealmKeyPathArrayT>

@Suppress("FunctionNaming", "LongParameterList")
expect object RealmInterop {
    fun realm_value_get(value: RealmValue): Any?
    fun realm_get_version_id(realm: RealmPointer): Long
    fun realm_get_library_version(): String
    fun realm_refresh(realm: RealmPointer)
    fun realm_get_num_versions(realm: RealmPointer): Long

    fun realm_schema_new(schema: List<Pair<ClassInfo, List<PropertyInfo>>>): RealmSchemaPointer

    fun realm_config_new(): RealmConfigurationPointer
    fun realm_config_set_path(config: RealmConfigurationPointer, path: String)
    fun realm_config_set_schema_mode(config: RealmConfigurationPointer, mode: SchemaMode)
    fun realm_config_set_schema_version(config: RealmConfigurationPointer, version: Long)
    fun realm_config_set_schema(config: RealmConfigurationPointer, schema: RealmSchemaPointer)
    fun realm_config_set_max_number_of_active_versions(config: RealmConfigurationPointer, maxNumberOfVersions: Long)
    fun realm_config_set_encryption_key(config: RealmConfigurationPointer, encryptionKey: ByteArray)
    fun realm_config_get_encryption_key(config: RealmConfigurationPointer): ByteArray?
    fun realm_config_set_should_compact_on_launch_function(config: RealmConfigurationPointer, callback: CompactOnLaunchCallback)
    fun realm_config_set_migration_function(config: RealmConfigurationPointer, callback: MigrationCallback)
    fun realm_config_set_automatic_backlink_handling(config: RealmConfigurationPointer, enabled: Boolean)
    fun realm_config_set_data_initialization_function(config: RealmConfigurationPointer, callback: DataInitializationCallback)
    fun realm_config_set_in_memory(config: RealmConfigurationPointer, inMemory: Boolean)
    fun realm_schema_validate(schema: RealmSchemaPointer, mode: SchemaValidationMode): Boolean

    fun realm_create_scheduler(): RealmSchedulerPointer
    fun realm_create_scheduler(dispatcher: CoroutineDispatcher): RealmSchedulerPointer
    /**
     * Open a realm on the current thread.
     *
     * The core scheduler is only advancing/delivering notifications if:
     * - Android: This is called on a thread with a Looper, in which case all events are delivered
     *   to the looper
     * - Native: This is called on the main thread or if supplying a single threaded dispatcher
     *   that is backed by the same thread that is opening the realm.
     * TODO Consider doing a custom JVM core scheduler that uses a coroutine dispatcher, or find a
     *  way to get a dispatcher for the current execution environment on Native so that we can avoid
     *  passing the dispatcher from outside. See comments in native implementation on how this
     *  could maybe be achieved.
     *
     *  The [config] Pointer passed in should only be used _once_ to open a Realm.
     *
     *  @return Pair of `(pointer, fileCreated)` where `pointer` is a reference to the SharedReam
     *  that was opened and `fileCreated` indicate whether or not the file was created as part of
     *  opening the Realm.
     */
    // The dispatcher argument is only used on Native to build a core scheduler dispatching to the
    // dispatcher. The realm itself must also be opened on the same thread
    fun realm_open(config: RealmConfigurationPointer, scheduler: RealmSchedulerPointer): Pair<LiveRealmPointer, Boolean>

    fun realm_add_realm_changed_callback(realm: LiveRealmPointer, block: () -> Unit): RealmCallbackTokenPointer
    fun realm_add_schema_changed_callback(realm: LiveRealmPointer, block: (RealmSchemaPointer) -> Unit): RealmCallbackTokenPointer

    fun realm_freeze(liveRealm: LiveRealmPointer): FrozenRealmPointer
    fun realm_is_frozen(realm: RealmPointer): Boolean
    fun realm_close(realm: RealmPointer)
    fun realm_delete_files(path: String)
    fun realm_compact(realm: RealmPointer): Boolean
    fun realm_convert_with_config(
        realm: RealmPointer,
        config: RealmConfigurationPointer,
        mergeWithExisting: Boolean
    )

    fun realm_get_schema(realm: RealmPointer): RealmSchemaPointer
    fun realm_get_schema_version(realm: RealmPointer): Long
    fun realm_get_num_classes(realm: RealmPointer): Long
    fun realm_get_class_keys(realm: RealmPointer): List<ClassKey>
    fun realm_find_class(realm: RealmPointer, name: String): ClassKey?
    fun realm_get_class(realm: RealmPointer, classKey: ClassKey): ClassInfo
    fun realm_get_class_properties(realm: RealmPointer, classKey: ClassKey, max: Long): List<PropertyInfo>

    /**
     * This method should only ever be called from `LongPointerWrapper` and `CPointerWrapper`
     */
    internal fun realm_release(p: RealmNativePointer)

    /**
     * Check if two pointers are pointing to the same underlying data.
     *
     * The same object at two different versions are not considered equal, even if no data
     * has changed (beside the version).
     */
    fun realm_equals(p1: RealmNativePointer, p2: RealmNativePointer): Boolean

    fun realm_is_closed(realm: RealmPointer): Boolean

    fun realm_begin_read(realm: RealmPointer)
    fun realm_begin_write(realm: LiveRealmPointer)
    fun realm_commit(realm: LiveRealmPointer)
    fun realm_rollback(realm: LiveRealmPointer)
    fun realm_is_in_transaction(realm: RealmPointer): Boolean

    fun realm_update_schema(realm: LiveRealmPointer, schema: RealmSchemaPointer)

    fun realm_object_create(realm: LiveRealmPointer, classKey: ClassKey): RealmObjectPointer
    fun realm_object_create_with_primary_key(
        realm: LiveRealmPointer,
        classKey: ClassKey,
        primaryKeyTransport: RealmValue
    ): RealmObjectPointer
    // How to propagate C-API did_create out
    fun realm_object_get_or_create_with_primary_key(
        realm: LiveRealmPointer,
        classKey: ClassKey,
        primaryKeyTransport: RealmValue
    ): RealmObjectPointer
    fun realm_object_is_valid(obj: RealmObjectPointer): Boolean
    fun realm_object_get_key(obj: RealmObjectPointer): ObjectKey
    fun realm_object_resolve_in(obj: RealmObjectPointer, realm: RealmPointer): RealmObjectPointer?

    fun realm_object_as_link(obj: RealmObjectPointer): Link
    fun realm_object_get_table(obj: RealmObjectPointer): ClassKey

    fun realm_get_col_key(realm: RealmPointer, classKey: ClassKey, col: String): PropertyKey

    fun MemAllocator.realm_get_value(obj: RealmObjectPointer, key: PropertyKey): RealmValue
    fun realm_set_value(
        obj: RealmObjectPointer,
        key: PropertyKey,
        value: RealmValue,
        isDefault: Boolean
    )
    fun realm_set_embedded(obj: RealmObjectPointer, key: PropertyKey): RealmObjectPointer
    fun realm_set_list(obj: RealmObjectPointer, key: PropertyKey): RealmListPointer
    fun realm_set_dictionary(obj: RealmObjectPointer, key: PropertyKey): RealmMapPointer
    fun realm_object_add_int(obj: RealmObjectPointer, key: PropertyKey, value: Long)
    fun <T> realm_object_get_parent(
        obj: RealmObjectPointer,
        block: (ClassKey, RealmObjectPointer) -> T
    ): T

    // list
    fun realm_get_list(obj: RealmObjectPointer, key: PropertyKey): RealmListPointer
    fun realm_get_backlinks(obj: RealmObjectPointer, sourceClassKey: ClassKey, sourcePropertyKey: PropertyKey): RealmResultsPointer
    fun realm_list_size(list: RealmListPointer): Long
    fun MemAllocator.realm_list_get(list: RealmListPointer, index: Long): RealmValue
    fun realm_list_find(list: RealmListPointer, value: RealmValue): Long
    fun realm_list_get_list(list: RealmListPointer, index: Long): RealmListPointer
    fun realm_list_get_dictionary(list: RealmListPointer, index: Long): RealmMapPointer
    fun realm_list_add(list: RealmListPointer, index: Long, transport: RealmValue)
    fun realm_list_insert_embedded(list: RealmListPointer, index: Long): RealmObjectPointer
    // Returns the element previously at the specified position
    fun realm_list_set(list: RealmListPointer, index: Long, inputTransport: RealmValue)
    fun realm_list_insert_list(list: RealmListPointer, index: Long): RealmListPointer
    fun realm_list_insert_dictionary(list: RealmListPointer, index: Long): RealmMapPointer
    fun realm_list_set_list(list: RealmListPointer, index: Long): RealmListPointer
    fun realm_list_set_dictionary(list: RealmListPointer, index: Long): RealmMapPointer

    // Returns the newly inserted element as the previous embedded element is automatically delete
    // by this operation
    fun MemAllocator.realm_list_set_embedded(list: RealmListPointer, index: Long): RealmValue
    fun realm_list_clear(list: RealmListPointer)
    fun realm_list_remove_all(list: RealmListPointer)
    fun realm_list_erase(list: RealmListPointer, index: Long)
    fun realm_list_resolve_in(list: RealmListPointer, realm: RealmPointer): RealmListPointer?
    fun realm_list_is_valid(list: RealmListPointer): Boolean

    // set
    fun realm_get_set(obj: RealmObjectPointer, key: PropertyKey): RealmSetPointer
    fun realm_set_size(set: RealmSetPointer): Long
    fun realm_set_clear(set: RealmSetPointer)
    fun realm_set_insert(set: RealmSetPointer, transport: RealmValue): Boolean
    fun MemAllocator.realm_set_get(set: RealmSetPointer, index: Long): RealmValue
    fun realm_set_find(set: RealmSetPointer, transport: RealmValue): Boolean
    fun realm_set_erase(set: RealmSetPointer, transport: RealmValue): Boolean
    fun realm_set_remove_all(set: RealmSetPointer)
    fun realm_set_resolve_in(set: RealmSetPointer, realm: RealmPointer): RealmSetPointer?
    fun realm_set_is_valid(set: RealmSetPointer): Boolean

    // dictionary
    fun realm_get_dictionary(obj: RealmObjectPointer, key: PropertyKey): RealmMapPointer
    fun realm_dictionary_clear(dictionary: RealmMapPointer)
    fun realm_dictionary_size(dictionary: RealmMapPointer): Long
    fun realm_dictionary_to_results(dictionary: RealmMapPointer): RealmResultsPointer
    fun MemAllocator.realm_dictionary_find(
        dictionary: RealmMapPointer,
        mapKey: RealmValue
    ): RealmValue
    fun realm_dictionary_find_list(
        dictionary: RealmMapPointer,
        mapKey: RealmValue
    ): RealmListPointer
    fun realm_dictionary_find_dictionary(
        dictionary: RealmMapPointer,
        mapKey: RealmValue
    ): RealmMapPointer
    fun MemAllocator.realm_dictionary_get(
        dictionary: RealmMapPointer,
        pos: Int
    ): Pair<RealmValue, RealmValue>

    fun MemAllocator.realm_dictionary_insert(
        dictionary: RealmMapPointer,
        mapKey: RealmValue,
        value: RealmValue
    ): Pair<RealmValue, Boolean>
    fun MemAllocator.realm_dictionary_erase(
        dictionary: RealmMapPointer,
        mapKey: RealmValue
    ): Pair<RealmValue, Boolean>
    fun realm_dictionary_contains_key(
        dictionary: RealmMapPointer,
        mapKey: RealmValue
    ): Boolean
    fun realm_dictionary_contains_value(
        dictionary: RealmMapPointer,
        value: RealmValue
    ): Boolean
    fun MemAllocator.realm_dictionary_insert_embedded(
        dictionary: RealmMapPointer,
        mapKey: RealmValue
    ): RealmValue
    fun realm_dictionary_insert_list(dictionary: RealmMapPointer, mapKey: RealmValue): RealmListPointer
    fun realm_dictionary_insert_dictionary(dictionary: RealmMapPointer, mapKey: RealmValue): RealmMapPointer
    fun realm_dictionary_get_keys(dictionary: RealmMapPointer): RealmResultsPointer
    fun realm_dictionary_resolve_in(
        dictionary: RealmMapPointer,
        realm: RealmPointer
    ): RealmMapPointer?

    fun realm_dictionary_is_valid(dictionary: RealmMapPointer): Boolean

    // query
    fun realm_query_parse(
        realm: RealmPointer,
        classKey: ClassKey,
        query: String,
        args: RealmQueryArgumentList
    ): RealmQueryPointer
    fun realm_query_parse_for_results(
        results: RealmResultsPointer,
        query: String,
        args: RealmQueryArgumentList
    ): RealmQueryPointer
    fun realm_query_parse_for_list(
        list: RealmListPointer,
        query: String,
        args: RealmQueryArgumentList
    ): RealmQueryPointer
    fun realm_query_parse_for_set(
        set: RealmSetPointer,
        query: String,
        args: RealmQueryArgumentList
    ): RealmQueryPointer
    fun realm_query_find_first(query: RealmQueryPointer): Link?
    fun realm_query_find_all(query: RealmQueryPointer): RealmResultsPointer
    fun realm_query_count(query: RealmQueryPointer): Long
    fun realm_query_append_query(
        query: RealmQueryPointer,
        filter: String,
        args: RealmQueryArgumentList
    ): RealmQueryPointer
    fun realm_query_get_description(query: RealmQueryPointer): String
    // Not implemented in C-API yet
    // RLM_API bool realm_query_delete_all(const realm_query_t*);

    fun realm_results_get_query(results: RealmResultsPointer): RealmQueryPointer
    fun realm_results_resolve_in(results: RealmResultsPointer, realm: RealmPointer): RealmResultsPointer
    fun realm_results_count(results: RealmResultsPointer): Long
    fun MemAllocator.realm_results_average(
        results: RealmResultsPointer,
        propertyKey: PropertyKey
    ): Pair<Boolean, RealmValue>
    fun MemAllocator.realm_results_sum(
        results: RealmResultsPointer,
        propertyKey: PropertyKey
    ): RealmValue
    fun MemAllocator.realm_results_max(
        results: RealmResultsPointer,
        propertyKey: PropertyKey
    ): RealmValue
    fun MemAllocator.realm_results_min(
        results: RealmResultsPointer,
        propertyKey: PropertyKey
    ): RealmValue

    // FIXME OPTIMIZE Get many
    fun MemAllocator.realm_results_get(results: RealmResultsPointer, index: Long): RealmValue
    fun realm_results_get_list(results: RealmResultsPointer, index: Long): RealmListPointer
    fun realm_results_get_dictionary(results: RealmResultsPointer, index: Long): RealmMapPointer
    fun realm_results_delete_all(results: RealmResultsPointer)

    fun realm_get_object(realm: RealmPointer, link: Link): RealmObjectPointer

    fun realm_object_find_with_primary_key(
        realm: RealmPointer,
        classKey: ClassKey,
        transport: RealmValue
    ): RealmObjectPointer?
    fun realm_object_delete(obj: RealmObjectPointer)

    fun realm_create_key_paths_array(realm: RealmPointer, clazz: ClassKey, keyPaths: List<String>): RealmKeyPathArrayPointer
    fun realm_object_add_notification_callback(
        obj: RealmObjectPointer,
        keyPaths: RealmKeyPathArrayPointer?,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer
    fun realm_results_add_notification_callback(
        results: RealmResultsPointer,
        keyPaths: RealmKeyPathArrayPointer?,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer
    fun realm_list_add_notification_callback(
        list: RealmListPointer,
        keyPaths: RealmKeyPathArrayPointer?,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer
    fun realm_set_add_notification_callback(
        set: RealmSetPointer,
        keyPaths: RealmKeyPathArrayPointer?,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer
    fun realm_dictionary_add_notification_callback(
        map: RealmMapPointer,
        keyPaths: RealmKeyPathArrayPointer?,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer
    fun realm_object_changes_get_modified_properties(
        change: RealmChangesPointer
    ): List<PropertyKey>
    fun <T, R> realm_collection_changes_get_indices(
        change: RealmChangesPointer,
        builder: CollectionChangeSetBuilder<T, R>
    )
    fun <T, R> realm_collection_changes_get_ranges(
        change: RealmChangesPointer,
        builder: CollectionChangeSetBuilder<T, R>
    )
    fun <R> realm_dictionary_get_changes(
        change: RealmChangesPointer,
        builder: DictionaryChangeSetBuilder<R>
    )

    fun realm_set_log_callback(callback: LogCallback)

    fun realm_set_log_level(level: CoreLogLevel)

    fun realm_set_log_level_category(category: String, level: CoreLogLevel)

    fun realm_get_log_level_category(category: String): CoreLogLevel

    fun realm_get_category_names(): List<String>
}
