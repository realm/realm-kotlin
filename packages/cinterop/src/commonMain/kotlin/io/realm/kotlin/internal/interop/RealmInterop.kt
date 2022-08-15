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

import io.realm.kotlin.internal.interop.sync.AuthProvider
import io.realm.kotlin.internal.interop.sync.CoreSubscriptionSetState
import io.realm.kotlin.internal.interop.sync.CoreSyncSessionState
import io.realm.kotlin.internal.interop.sync.CoreUserState
import io.realm.kotlin.internal.interop.sync.MetadataMode
import io.realm.kotlin.internal.interop.sync.NetworkTransport
import io.realm.kotlin.internal.interop.sync.ProtocolClientErrorCode
import io.realm.kotlin.internal.interop.sync.SyncErrorCodeCategory
import io.realm.kotlin.internal.interop.sync.SyncSessionResyncMode
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
// Constants for invalid keys
expect val INVALID_CLASS_KEY: ClassKey
expect val INVALID_PROPERTY_KEY: PropertyKey

const val OBJECT_ID_BYTES_SIZE = 12

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
interface RealmResultsT : CapiT
interface RealmQueryT : CapiT
interface RealmCallbackTokenT : CapiT
interface RealmNotificationTokenT : CapiT
interface RealmChangesT : CapiT
interface RealmObjectChangesT : RealmChangesT
interface RealmCollectionChangesT : RealmChangesT

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
typealias RealmResultsPointer = NativePointer<RealmResultsT>
typealias RealmQueryPointer = NativePointer<RealmQueryT>
typealias RealmCallbackTokenPointer = NativePointer<RealmCallbackTokenT>
typealias RealmNotificationTokenPointer = NativePointer<RealmNotificationTokenT>
typealias RealmChangesPointer = NativePointer<RealmChangesT>

// Sync types
// Pure marker interfaces corresponding to the C-API realm_x_t struct types
interface RealmAppT : CapiT
interface RealmAppConfigT : CapiT
interface RealmSyncConfigT : CapiT
interface RealmSyncClientConfigT : CapiT
interface RealmCredentialsT : CapiT
interface RealmUserT : CapiT
interface RealmNetworkTransportT : CapiT
interface RealmSyncSessionT : CapiT
interface RealmSubscriptionT : CapiT
interface RealmBaseSubscriptionSet : CapiT
interface RealmSubscriptionSetT : RealmBaseSubscriptionSet
interface RealmMutableSubscriptionSetT : RealmBaseSubscriptionSet
// Public type aliases binding to internal verbose type safe type definitions. This should allow us
// to easily change implementation details later on.
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

@Suppress("FunctionNaming", "LongParameterList")
expect object RealmInterop {
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
    fun realm_config_set_data_initialization_function(config: RealmConfigurationPointer, callback: DataInitializationCallback)

    fun realm_schema_validate(schema: RealmSchemaPointer, mode: SchemaValidationMode): Boolean

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
     *  that was opened and `fileCreated` indicate wether or not the file was created as part of
     *  opening the Realm.
     */
    // The dispatcher argument is only used on Native to build a core scheduler dispatching to the
    // dispatcher. The realm itself must also be opened on the same thread
    fun realm_open(config: RealmConfigurationPointer, dispatcher: CoroutineDispatcher? = null): Pair<LiveRealmPointer, Boolean>

    fun realm_add_realm_changed_callback(realm: LiveRealmPointer, block: () -> Unit): RealmCallbackTokenPointer
    fun realm_add_schema_changed_callback(realm: LiveRealmPointer, block: (RealmSchemaPointer) -> Unit): RealmCallbackTokenPointer

    fun realm_freeze(liveRealm: LiveRealmPointer): FrozenRealmPointer
    fun realm_is_frozen(realm: RealmPointer): Boolean
    fun realm_close(realm: RealmPointer)
    fun realm_delete_files(path: String)
    fun realm_convert_with_config(realm: RealmPointer, config: RealmConfigurationPointer)

    fun realm_get_schema(realm: RealmPointer): RealmSchemaPointer
    fun realm_get_schema_version(realm: RealmPointer): Long
    fun realm_get_num_classes(realm: RealmPointer): Long
    fun realm_get_class_keys(realm: RealmPointer): List<ClassKey>
    fun realm_find_class(realm: RealmPointer, name: String): ClassKey?
    fun realm_get_class(realm: RealmPointer, classKey: ClassKey): ClassInfo
    fun realm_get_class_properties(realm: RealmPointer, classKey: ClassKey, max: Long): List<PropertyInfo>

    fun realm_release(p: RealmNativePointer)
    fun realm_equals(p1: RealmNativePointer, p2: RealmNativePointer): Boolean

    fun realm_is_closed(realm: RealmPointer): Boolean

    fun realm_begin_read(realm: RealmPointer)
    fun realm_begin_write(realm: LiveRealmPointer)
    fun realm_commit(realm: LiveRealmPointer)
    fun realm_rollback(realm: LiveRealmPointer)
    fun realm_is_in_transaction(realm: RealmPointer): Boolean

    fun realm_update_schema(realm: LiveRealmPointer, schema: RealmSchemaPointer)

    fun realm_object_create(realm: LiveRealmPointer, classKey: ClassKey): RealmObjectPointer
    fun realm_object_create_with_primary_key(realm: LiveRealmPointer, classKey: ClassKey, primaryKey: RealmValue): RealmObjectPointer
    // How to propagate C-API did_create out
    fun realm_object_get_or_create_with_primary_key(realm: LiveRealmPointer, classKey: ClassKey, primaryKey: RealmValue): RealmObjectPointer
    fun realm_object_is_valid(obj: RealmObjectPointer): Boolean
    fun realm_object_get_key(obj: RealmObjectPointer): Long
    fun realm_object_resolve_in(obj: RealmObjectPointer, realm: RealmPointer): RealmObjectPointer?

    fun realm_object_as_link(obj: RealmObjectPointer): Link
    fun realm_object_get_table(obj: RealmObjectPointer): ClassKey

    fun realm_get_col_key(realm: RealmPointer, classKey: ClassKey, col: String): PropertyKey

    fun realm_get_value(obj: RealmObjectPointer, key: PropertyKey): RealmValue
    fun realm_set_value(obj: RealmObjectPointer, key: PropertyKey, value: RealmValue, isDefault: Boolean)
    fun realm_set_embedded(obj: RealmObjectPointer, key: PropertyKey): RealmObjectPointer

    // list
    fun realm_get_list(obj: RealmObjectPointer, key: PropertyKey): RealmListPointer
    fun realm_list_size(list: RealmListPointer): Long
    fun realm_list_get(list: RealmListPointer, index: Long): RealmValue
    fun realm_list_add(list: RealmListPointer, index: Long, value: RealmValue)
    fun realm_list_insert_embedded(list: RealmListPointer, index: Long): RealmObjectPointer
    // Returns the element previously at the specified position
    fun realm_list_set(list: RealmListPointer, index: Long, value: RealmValue): RealmValue
    // Returns the newly inserted element as the previous embedded element is automatically delete
    // by this operation
    fun realm_list_set_embedded(list: RealmListPointer, index: Long): RealmValue
    fun realm_list_clear(list: RealmListPointer)
    fun realm_list_remove_all(list: RealmListPointer)
    fun realm_list_erase(list: RealmListPointer, index: Long)
    fun realm_list_resolve_in(list: RealmListPointer, realm: RealmPointer): RealmListPointer?
    fun realm_list_is_valid(list: RealmListPointer): Boolean

    // set
    fun realm_get_set(obj: RealmObjectPointer, key: PropertyKey): RealmSetPointer
    fun realm_set_size(set: RealmSetPointer): Long
    fun realm_set_clear(set: RealmSetPointer)
    fun realm_set_insert(set: RealmSetPointer, value: RealmValue): Boolean
    fun realm_set_get(set: RealmSetPointer, index: Long): RealmValue
    fun realm_set_find(set: RealmSetPointer, value: RealmValue): Boolean
    fun realm_set_erase(set: RealmSetPointer, value: RealmValue): Boolean
    fun realm_set_remove_all(set: RealmSetPointer)
    fun realm_set_resolve_in(set: RealmSetPointer, realm: RealmPointer): RealmSetPointer?

    // query
    fun realm_query_parse(realm: RealmPointer, classKey: ClassKey, query: String, args: Array<RealmValue>): RealmQueryPointer
    fun realm_query_parse_for_results(results: RealmResultsPointer, query: String, args: Array<RealmValue>): RealmQueryPointer
    fun realm_query_find_first(query: RealmQueryPointer): Link?
    fun realm_query_find_all(query: RealmQueryPointer): RealmResultsPointer
    fun realm_query_count(query: RealmQueryPointer): Long
    fun realm_query_append_query(
        query: RealmQueryPointer,
        filter: String,
        args: Array<RealmValue>
    ): RealmQueryPointer
    fun realm_query_get_description(query: RealmQueryPointer): String
    // Not implemented in C-API yet
    // RLM_API bool realm_query_delete_all(const realm_query_t*);

    fun realm_results_resolve_in(results: RealmResultsPointer, realm: RealmPointer): RealmResultsPointer
    fun realm_results_count(results: RealmResultsPointer): Long
    fun realm_results_average(results: RealmResultsPointer, propertyKey: PropertyKey): Pair<Boolean, RealmValue>
    fun realm_results_sum(results: RealmResultsPointer, propertyKey: PropertyKey): RealmValue
    fun realm_results_max(results: RealmResultsPointer, propertyKey: PropertyKey): RealmValue
    fun realm_results_min(results: RealmResultsPointer, propertyKey: PropertyKey): RealmValue
    // FIXME OPTIMIZE Get many
    fun realm_results_get(results: RealmResultsPointer, index: Long): Link
    fun realm_results_delete_all(results: RealmResultsPointer)

    fun realm_get_object(realm: RealmPointer, link: Link): RealmObjectPointer

    fun realm_object_find_with_primary_key(realm: RealmPointer, classKey: ClassKey, primaryKey: RealmValue): RealmObjectPointer?
    fun realm_object_delete(obj: RealmObjectPointer)

    fun realm_object_add_notification_callback(
        obj: RealmObjectPointer,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer
    fun realm_results_add_notification_callback(
        results: RealmResultsPointer,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer
    fun realm_list_add_notification_callback(
        list: RealmListPointer,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer
    fun realm_set_add_notification_callback(
        set: RealmSetPointer,
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
    fun realm_clear_cached_apps()
    fun realm_app_sync_client_get_default_file_path_for_realm(
        app: RealmAppPointer,
        syncConfig: RealmSyncConfigurationPointer,
        overriddenName: String?
    ): String

    // User
    fun realm_user_get_identity(user: RealmUserPointer): String
    fun realm_user_is_logged_in(user: RealmUserPointer): Boolean
    fun realm_user_log_out(user: RealmUserPointer)
    fun realm_user_get_state(user: RealmUserPointer): CoreUserState

    // Sync client config
    fun realm_sync_client_config_new(): RealmSyncClientConfigurationPointer
    fun realm_sync_client_config_set_base_file_path(
        syncClientConfig: RealmSyncClientConfigurationPointer,
        basePath: String
    )

    fun realm_sync_client_config_set_log_callback(
        syncClientConfig: RealmSyncClientConfigurationPointer,
        callback: SyncLogCallback
    )
    fun realm_sync_client_config_set_log_level(syncClientConfig: RealmSyncClientConfigurationPointer, level: CoreLogLevel)

    fun realm_sync_client_config_set_metadata_mode(
        syncClientConfig: RealmSyncClientConfigurationPointer,
        metadataMode: MetadataMode
    )

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
    fun realm_sync_immediately_run_file_actions(app: RealmAppPointer, syncPath: String)

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
    fun realm_sync_session_pause(syncSession: RealmSyncSessionPointer)
    fun realm_sync_session_resume(syncSession: RealmSyncSessionPointer)
    fun realm_sync_session_handle_error_for_testing(
        syncSession: RealmSyncSessionPointer,
        errorCode: ProtocolClientErrorCode,
        category: SyncErrorCodeCategory,
        errorMessage: String,
        isFatal: Boolean
    )

    // AppConfig
    fun realm_network_transport_new(networkTransport: NetworkTransport): RealmNetworkTransportPointer
    fun realm_app_config_new(
        appId: String,
        networkTransport: RealmNetworkTransportPointer,
        baseUrl: String? = null,
        platform: String,
        platformVersion: String,
        sdkVersion: String
    ): RealmAppConfigurationPointer
    fun realm_app_config_set_base_url(appConfig: RealmAppConfigurationPointer, baseUrl: String)

    // Credentials
    fun realm_app_credentials_new_anonymous(): RealmCredentialsPointer
    fun realm_app_credentials_new_email_password(username: String, password: String): RealmCredentialsPointer
    fun realm_app_credentials_new_api_key(key: String): RealmCredentialsPointer
    fun realm_app_credentials_new_apple(idToken: String): RealmCredentialsPointer
    // fun realm_app_credentials_new_custom_function(document: Any): NativePointer
    fun realm_app_credentials_new_facebook(accessToken: String): RealmCredentialsPointer
    fun realm_app_credentials_new_google_id_token(idToken: String): RealmCredentialsPointer
    fun realm_app_credentials_new_google_auth_code(authCode: String): RealmCredentialsPointer
    fun realm_app_credentials_new_jwt(jwtToken: String): RealmCredentialsPointer
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

    // Sync config
    fun realm_config_set_sync_config(
        realmConfiguration: RealmConfigurationPointer,
        syncConfiguration: RealmSyncConfigurationPointer
    )

    // Flexible Sync
    fun realm_flx_sync_config_new(user: RealmUserPointer): RealmSyncConfigurationPointer

    // Flexible Sync Subscription
    fun realm_sync_subscription_id(subscription: RealmSubscriptionPointer): ObjectIdWrapper
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
        mutatableSubscriptionSet: RealmMutableSubscriptionSetPointer,
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
}
