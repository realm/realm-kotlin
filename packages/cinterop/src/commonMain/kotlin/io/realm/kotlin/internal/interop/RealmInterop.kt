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

import io.realm.kotlin.internal.interop.sync.ApiKeyWrapper
import io.realm.kotlin.internal.interop.sync.AuthProvider
import io.realm.kotlin.internal.interop.sync.CoreConnectionState
import io.realm.kotlin.internal.interop.sync.CoreSubscriptionSetState
import io.realm.kotlin.internal.interop.sync.CoreSyncSessionState
import io.realm.kotlin.internal.interop.sync.CoreUserState
import io.realm.kotlin.internal.interop.sync.MetadataMode
import io.realm.kotlin.internal.interop.sync.NetworkTransport
import io.realm.kotlin.internal.interop.sync.ProgressDirection
import io.realm.kotlin.internal.interop.sync.SyncSessionResyncMode
import io.realm.kotlin.internal.interop.sync.SyncUserIdentity
import io.realm.kotlin.internal.interop.sync.WebSocketTransport
import io.realm.kotlin.internal.interop.sync.WebsocketCallbackResult
import io.realm.kotlin.internal.interop.sync.WebsocketErrorCode
import kotlinx.coroutines.CoroutineDispatcher
import org.mongodb.kbson.ObjectId
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

// Sync types
// Pure marker interfaces corresponding to the C-API realm_x_t struct types
interface RealmAsyncOpenTaskT : CapiT
interface RealmAppT : CapiT
interface RealmAppConfigT : CapiT
interface RealmSyncConfigT : CapiT
interface RealmSyncClientConfigT : CapiT
interface RealmCredentialsT : CapiT
interface RealmUserT : CapiT
interface RealmNetworkTransportT : CapiT
interface RealmSyncSessionT : CapiT
interface RealmSubscriptionT : CapiT
interface RealmSyncSocketObserverPointerT : CapiT
interface RealmSyncSocketCallbackPointerT : CapiT

interface RealmBaseSubscriptionSet : CapiT
interface RealmSyncSocket : CapiT
interface RealmSubscriptionSetT : RealmBaseSubscriptionSet
interface RealmMutableSubscriptionSetT : RealmBaseSubscriptionSet
interface RealmSyncSocketT : RealmSyncSocket

// Public type aliases binding to internal verbose type safe type definitions. This should allow us
// to easily change implementation details later on.
typealias RealmAsyncOpenTaskPointer = NativePointer<RealmAsyncOpenTaskT>
typealias RealmAppPointer = NativePointer<RealmAppT>
typealias RealmAppConfigurationPointer = NativePointer<RealmAppConfigT>
typealias RealmSyncConfigurationPointer = NativePointer<RealmSyncConfigT>
typealias RealmSyncClientConfigurationPointer = NativePointer<RealmSyncClientConfigT>
typealias RealmCredentialsPointer = NativePointer<RealmCredentialsT>
typealias RealmUserPointer = NativePointer<RealmUserT>
typealias RealmNetworkTransportPointer = NativePointer<RealmNetworkTransportT>
typealias RealmSyncSessionPointer = NativePointer<RealmSyncSessionT>
typealias RealmSubscriptionPointer = NativePointer<RealmSubscriptionT>
typealias RealmBaseSubscriptionSetPointer = NativePointer<out RealmBaseSubscriptionSet>
typealias RealmSubscriptionSetPointer = NativePointer<RealmSubscriptionSetT>
typealias RealmMutableSubscriptionSetPointer = NativePointer<RealmMutableSubscriptionSetT>
typealias RealmSyncSocketPointer = NativePointer<RealmSyncSocketT>
typealias RealmSyncSocketObserverPointer = NativePointer<RealmSyncSocketObserverPointerT>
typealias RealmSyncSocketCallbackPointer = NativePointer<RealmSyncSocketCallbackPointerT>
typealias RealmWebsocketHandlerCallbackPointer = NativePointer<CapiT>
typealias RealmWebsocketProviderPointer = NativePointer<CapiT>
/**
 * Class for grouping and normalizing values we want to send as part of
 * logging in Sync Users.
 */
@Suppress("LongParameterList")
class SyncConnectionParams(
    sdkVersion: String,
    bundleId: String,
    platformVersion: String,
    device: String,
    deviceVersion: String,
    framework: Runtime,
    frameworkVersion: String
) {
    val sdkName = "Kotlin"
    val bundleId: String
    val sdkVersion: String
    val platformVersion: String
    val device: String
    val deviceVersion: String
    val framework: String
    val frameworkVersion: String

    enum class Runtime(public val description: String) {
        JVM("JVM"),
        ANDROID("Android"),
        NATIVE("Native")
    }

    init {
        this.sdkVersion = sdkVersion
        this.bundleId = bundleId
        this.platformVersion = platformVersion
        this.device = device
        this.deviceVersion = deviceVersion
        this.framework = framework.description
        this.frameworkVersion = frameworkVersion
    }
}

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

    // Opening a Realm asynchronously. Only supported for synchronized realms.
    fun realm_open_synchronized(config: RealmConfigurationPointer): RealmAsyncOpenTaskPointer
    fun realm_async_open_task_start(task: RealmAsyncOpenTaskPointer, callback: AsyncOpenCallback)
    fun realm_async_open_task_cancel(task: RealmAsyncOpenTaskPointer)

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

    // App
    fun realm_app_get(
        appConfig: RealmAppConfigurationPointer,
        syncClientConfig: RealmSyncClientConfigurationPointer,
        basePath: String,
    ): RealmAppPointer
    fun realm_app_get_current_user(app: RealmAppPointer): RealmUserPointer?
    fun realm_app_get_all_users(app: RealmAppPointer): List<RealmUserPointer>
    fun realm_app_log_in_with_credentials(app: RealmAppPointer, credentials: RealmCredentialsPointer, callback: AppCallback<RealmUserPointer>)
    fun realm_app_log_out(app: RealmAppPointer, user: RealmUserPointer, callback: AppCallback<Unit>)
    fun realm_app_remove_user(app: RealmAppPointer, user: RealmUserPointer, callback: AppCallback<Unit>)
    fun realm_app_delete_user(app: RealmAppPointer, user: RealmUserPointer, callback: AppCallback<Unit>)
    fun realm_app_link_credentials(app: RealmAppPointer, user: RealmUserPointer, credentials: RealmCredentialsPointer, callback: AppCallback<RealmUserPointer>)
    fun realm_clear_cached_apps()
    fun realm_app_sync_client_get_default_file_path_for_realm(
        syncConfig: RealmSyncConfigurationPointer,
        overriddenName: String?
    ): String
    fun realm_app_user_apikey_provider_client_create_apikey(
        app: RealmAppPointer,
        user: RealmUserPointer,
        name: String,
        callback: AppCallback<ApiKeyWrapper>
    )

    fun realm_app_user_apikey_provider_client_delete_apikey(
        app: RealmAppPointer,
        user: RealmUserPointer,
        id: ObjectId,
        callback: AppCallback<Unit>,
    )

    fun realm_app_user_apikey_provider_client_disable_apikey(
        app: RealmAppPointer,
        user: RealmUserPointer,
        id: ObjectId,
        callback: AppCallback<Unit>,
    )

    fun realm_app_user_apikey_provider_client_enable_apikey(
        app: RealmAppPointer,
        user: RealmUserPointer,
        id: ObjectId,
        callback: AppCallback<Unit>,
    )

    fun realm_app_user_apikey_provider_client_fetch_apikey(
        app: RealmAppPointer,
        user: RealmUserPointer,
        id: ObjectId,
        callback: AppCallback<ApiKeyWrapper>,
    )

    fun realm_app_user_apikey_provider_client_fetch_apikeys(
        app: RealmAppPointer,
        user: RealmUserPointer,
        callback: AppCallback<Array<ApiKeyWrapper>>,
    )

    fun realm_app_get_base_url(
        app: RealmAppPointer,
    ): String

    fun realm_app_update_base_url(
        app: RealmAppPointer,
        baseUrl: String?,
        callback: AppCallback<Unit>,
    )

    // User
    fun realm_user_get_all_identities(user: RealmUserPointer): List<SyncUserIdentity>
    fun realm_user_get_identity(user: RealmUserPointer): String
    fun realm_user_get_access_token(user: RealmUserPointer): String
    fun realm_user_get_refresh_token(user: RealmUserPointer): String
    fun realm_user_get_device_id(user: RealmUserPointer): String
    fun realm_user_is_logged_in(user: RealmUserPointer): Boolean
    fun realm_user_log_out(user: RealmUserPointer)
    fun realm_user_get_state(user: RealmUserPointer): CoreUserState
    fun realm_user_get_profile(user: RealmUserPointer): String
    fun realm_user_get_custom_data(user: RealmUserPointer): String?
    fun realm_user_refresh_custom_data(app: RealmAppPointer, user: RealmUserPointer, callback: AppCallback<Unit>)

    // Sync client config
    fun realm_sync_client_config_new(): RealmSyncClientConfigurationPointer

    fun realm_sync_client_config_set_default_binding_thread_observer(
        syncClientConfig: RealmSyncClientConfigurationPointer,
        appId: String
    )

    fun realm_app_config_set_base_file_path(
        appConfig: RealmAppConfigurationPointer,
        basePath: String
    )

    fun realm_sync_client_config_set_multiplex_sessions(syncClientConfig: RealmSyncClientConfigurationPointer, enabled: Boolean)

    fun realm_set_log_callback(callback: LogCallback)

    fun realm_set_log_level(level: CoreLogLevel)

    fun realm_set_log_level_category(category: String, level: CoreLogLevel)

    fun realm_get_log_level_category(category: String): CoreLogLevel

    fun realm_get_category_names(): List<String>

    fun realm_app_config_set_metadata_mode(
        appConfig: RealmAppConfigurationPointer,
        metadataMode: MetadataMode
    )

    fun realm_app_config_set_metadata_encryption_key(
        appConfig: RealmAppConfigurationPointer,
        encryptionKey: ByteArray
    )
    fun realm_sync_client_config_set_user_agent_binding_info(
        syncClientConfig: RealmSyncClientConfigurationPointer,
        bindingInfo: String
    )
    fun realm_sync_client_config_set_user_agent_application_info(
        syncClientConfig: RealmSyncClientConfigurationPointer,
        applicationInfo: String
    )

    fun realm_sync_client_config_set_connect_timeout(syncClientConfig: RealmSyncClientConfigurationPointer, timeoutMs: ULong)
    fun realm_sync_client_config_set_connection_linger_time(syncClientConfig: RealmSyncClientConfigurationPointer, timeoutMs: ULong)
    fun realm_sync_client_config_set_ping_keepalive_period(syncClientConfig: RealmSyncClientConfigurationPointer, timeoutMs: ULong)
    fun realm_sync_client_config_set_pong_keepalive_timeout(syncClientConfig: RealmSyncClientConfigurationPointer, timeoutMs: ULong)
    fun realm_sync_client_config_set_fast_reconnect_limit(syncClientConfig: RealmSyncClientConfigurationPointer, timeoutMs: ULong)

    fun realm_sync_config_new(
        user: RealmUserPointer,
        partition: String
    ): RealmSyncConfigurationPointer
    fun realm_sync_config_set_error_handler(
        syncConfig: RealmSyncConfigurationPointer,
        errorHandler: SyncErrorCallback
    )
    fun realm_sync_config_set_resync_mode(
        syncConfig: RealmSyncConfigurationPointer,
        resyncMode: SyncSessionResyncMode
    )
    fun realm_sync_config_set_before_client_reset_handler(
        syncConfig: RealmSyncConfigurationPointer,
        beforeHandler: SyncBeforeClientResetHandler
    )
    fun realm_sync_config_set_after_client_reset_handler(
        syncConfig: RealmSyncConfigurationPointer,
        afterHandler: SyncAfterClientResetHandler
    )
    fun realm_sync_immediately_run_file_actions(app: RealmAppPointer, syncPath: String): Boolean

    // SyncSession
    fun realm_sync_session_get(realm: RealmPointer): RealmSyncSessionPointer
    fun realm_sync_session_wait_for_download_completion(
        syncSession: RealmSyncSessionPointer,
        callback: SyncSessionTransferCompletionCallback
    )
    fun realm_sync_session_wait_for_upload_completion(
        syncSession: RealmSyncSessionPointer,
        callback: SyncSessionTransferCompletionCallback
    )
    fun realm_sync_session_state(syncSession: RealmSyncSessionPointer): CoreSyncSessionState
    fun realm_sync_connection_state(syncSession: RealmSyncSessionPointer): CoreConnectionState
    fun realm_sync_session_pause(syncSession: RealmSyncSessionPointer)
    fun realm_sync_session_resume(syncSession: RealmSyncSessionPointer)
    fun realm_sync_session_handle_error_for_testing(
        syncSession: RealmSyncSessionPointer,
        error: ErrorCode,
        errorMessage: String,
        isFatal: Boolean
    )

    fun realm_sync_session_register_progress_notifier(
        syncSession: RealmSyncSessionPointer /* = io.realm.kotlin.internal.interop.NativePointer<io.realm.kotlin.internal.interop.RealmSyncSessionT> */,
        direction: ProgressDirection,
        isStreaming: Boolean,
        callback: ProgressCallback,
    ): RealmNotificationTokenPointer

    fun realm_sync_session_register_connection_state_change_callback(
        syncSession: RealmSyncSessionPointer,
        callback: ConnectionStateChangeCallback,
    ): RealmNotificationTokenPointer

    // AppConfig
    fun realm_network_transport_new(networkTransport: NetworkTransport): RealmNetworkTransportPointer
    fun realm_app_config_new(
        appId: String,
        networkTransport: RealmNetworkTransportPointer,
        baseUrl: String? = null,
        connectionParams: SyncConnectionParams
    ): RealmAppConfigurationPointer
    fun realm_app_config_set_base_url(appConfig: RealmAppConfigurationPointer, baseUrl: String)

    // Credentials
    fun realm_app_credentials_new_anonymous(reuseExisting: Boolean): RealmCredentialsPointer
    fun realm_app_credentials_new_email_password(username: String, password: String): RealmCredentialsPointer
    fun realm_app_credentials_new_api_key(key: String): RealmCredentialsPointer
    fun realm_app_credentials_new_apple(idToken: String): RealmCredentialsPointer
    fun realm_app_credentials_new_facebook(accessToken: String): RealmCredentialsPointer
    fun realm_app_credentials_new_google_id_token(idToken: String): RealmCredentialsPointer
    fun realm_app_credentials_new_google_auth_code(authCode: String): RealmCredentialsPointer
    fun realm_app_credentials_new_jwt(jwtToken: String): RealmCredentialsPointer
    fun realm_app_credentials_new_custom_function(serializedEjsonPayload: String): RealmCredentialsPointer
    fun realm_auth_credentials_get_provider(credentials: RealmCredentialsPointer): AuthProvider
    fun realm_app_credentials_serialize_as_json(credentials: RealmCredentialsPointer): String

    // Email Password Authentication
    fun realm_app_email_password_provider_client_register_email(
        app: RealmAppPointer,
        email: String,
        password: String,
        callback: AppCallback<Unit>
    )
    fun realm_app_email_password_provider_client_confirm_user(
        app: RealmAppPointer,
        token: String,
        tokenId: String,
        callback: AppCallback<Unit>
    )
    fun realm_app_email_password_provider_client_resend_confirmation_email(
        app: RealmAppPointer,
        email: String,
        callback: AppCallback<Unit>
    )
    fun realm_app_email_password_provider_client_retry_custom_confirmation(
        app: RealmAppPointer,
        email: String,
        callback: AppCallback<Unit>
    )
    fun realm_app_email_password_provider_client_send_reset_password_email(
        app: RealmAppPointer,
        email: String,
        callback: AppCallback<Unit>
    )
    fun realm_app_email_password_provider_client_reset_password(
        app: RealmAppPointer,
        token: String,
        tokenId: String,
        newPassword: String,
        callback: AppCallback<Unit>
    )
    fun realm_app_call_reset_password_function(
        app: RealmAppPointer,
        email: String,
        newPassword: String,
        serializedEjsonPayload: String,
        callback: AppCallback<Unit>
    )

    fun realm_app_call_function(
        app: RealmAppPointer,
        user: RealmUserPointer,
        name: String,
        serviceName: String? = null,
        serializedEjsonArgs: String, // as ejson
        callback: AppCallback<String>
    )

    // Sync Client
    fun realm_app_sync_client_reconnect(app: RealmAppPointer)
    fun realm_app_sync_client_has_sessions(app: RealmAppPointer): Boolean
    fun realm_app_sync_client_wait_for_sessions_to_terminate(app: RealmAppPointer)

    // Sync config
    fun realm_config_set_sync_config(
        realmConfiguration: RealmConfigurationPointer,
        syncConfiguration: RealmSyncConfigurationPointer
    )

    // Flexible Sync
    fun realm_flx_sync_config_new(user: RealmUserPointer): RealmSyncConfigurationPointer

    // Flexible Sync Subscription
    fun realm_sync_subscription_id(subscription: RealmSubscriptionPointer): ObjectId
    fun realm_sync_subscription_name(subscription: RealmSubscriptionPointer): String?
    fun realm_sync_subscription_object_class_name(subscription: RealmSubscriptionPointer): String
    fun realm_sync_subscription_query_string(subscription: RealmSubscriptionPointer): String
    fun realm_sync_subscription_created_at(subscription: RealmSubscriptionPointer): Timestamp
    fun realm_sync_subscription_updated_at(subscription: RealmSubscriptionPointer): Timestamp

    // Flexible Sync Subscription Set
    fun realm_sync_get_latest_subscriptionset(realm: RealmPointer): RealmSubscriptionSetPointer
    fun realm_sync_on_subscriptionset_state_change_async(
        subscriptionSet: RealmSubscriptionSetPointer,
        destinationState: CoreSubscriptionSetState,
        callback: SubscriptionSetCallback
    )
    fun realm_sync_subscriptionset_version(subscriptionSet: RealmBaseSubscriptionSetPointer): Long
    fun realm_sync_subscriptionset_state(subscriptionSet: RealmBaseSubscriptionSetPointer): CoreSubscriptionSetState
    fun realm_sync_subscriptionset_error_str(subscriptionSet: RealmBaseSubscriptionSetPointer): String?
    fun realm_sync_subscriptionset_size(subscriptionSet: RealmBaseSubscriptionSetPointer): Long
    fun realm_sync_subscription_at(
        subscriptionSet: RealmBaseSubscriptionSetPointer,
        index: Long
    ): RealmSubscriptionPointer
    fun realm_sync_find_subscription_by_name(
        subscriptionSet: RealmBaseSubscriptionSetPointer,
        name: String
    ): RealmSubscriptionPointer?
    fun realm_sync_find_subscription_by_query(
        subscriptionSet: RealmBaseSubscriptionSetPointer,
        query: RealmQueryPointer
    ): RealmSubscriptionPointer?
    fun realm_sync_subscriptionset_refresh(subscriptionSet: RealmSubscriptionSetPointer): Boolean
    fun realm_sync_make_subscriptionset_mutable(
        subscriptionSet: RealmSubscriptionSetPointer
    ): RealmMutableSubscriptionSetPointer

    // Flexible Sync Mutable Subscription Set
    fun realm_sync_subscriptionset_clear(
        mutableSubscriptionSet: RealmMutableSubscriptionSetPointer
    ): Boolean
    // Returns a Pair of (<subscriptionPtr>, <true if inserted, false if updated>)
    fun realm_sync_subscriptionset_insert_or_assign(
        mutableSubscriptionSet: RealmMutableSubscriptionSetPointer,
        query: RealmQueryPointer,
        name: String?
    ): Pair<RealmSubscriptionPointer, Boolean>
    fun realm_sync_subscriptionset_erase_by_name(
        mutableSubscriptionSet: RealmMutableSubscriptionSetPointer,
        name: String
    ): Boolean
    fun realm_sync_subscriptionset_erase_by_query(
        mutableSubscriptionSet: RealmMutableSubscriptionSetPointer,
        query: RealmQueryPointer
    ): Boolean
    fun realm_sync_subscriptionset_erase_by_id(
        mutableSubscriptionSet: RealmMutableSubscriptionSetPointer,
        sub: RealmSubscriptionPointer
    ): Boolean
    fun realm_sync_subscriptionset_commit(
        mutableSubscriptionSet: RealmMutableSubscriptionSetPointer
    ): RealmSubscriptionSetPointer

    fun realm_sync_set_websocket_transport(
        syncClientConfig: RealmSyncClientConfigurationPointer,
        webSocketTransport: WebSocketTransport
    )

    fun realm_sync_socket_callback_complete(nativePointer: RealmWebsocketHandlerCallbackPointer, cancelled: Boolean = false, status: WebsocketCallbackResult = WebsocketCallbackResult.RLM_ERR_SYNC_SOCKET_SUCCESS, reason: String = "")

    fun realm_sync_socket_websocket_connected(nativePointer: RealmWebsocketProviderPointer, protocol: String)

    fun realm_sync_socket_websocket_error(nativePointer: RealmWebsocketProviderPointer)

    fun realm_sync_socket_websocket_message(
        nativePointer: RealmWebsocketProviderPointer,
        data: ByteArray
    ): Boolean

    fun realm_sync_socket_websocket_closed(nativePointer: RealmWebsocketProviderPointer, wasClean: Boolean, errorCode: WebsocketErrorCode, reason: String = "")
}
