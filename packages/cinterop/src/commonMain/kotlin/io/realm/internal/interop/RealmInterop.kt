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

import io.realm.internal.interop.sync.AuthProvider
import io.realm.internal.interop.sync.MetadataMode
import io.realm.internal.interop.sync.NetworkTransport
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.jvm.JvmInline

// FIXME API-INTERNAL Consider adding marker interfaces NativeRealm, NativeRealmConfig, etc. as type parameter
//  to NativePointer. NOTE Verify that it is supported for Kotlin Native!

// Wrapper for the C-API realm_class_key_t uniquely identifying the class/table in the schema
@JvmInline
value class ClassKey(val key: Long)
// Wrapper for the C-API realm_property_key_t uniquely identifying the property within a class/table
@JvmInline
value class ColumnKey(val key: Long)

@Suppress("FunctionNaming", "LongParameterList")
expect object RealmInterop {
    fun realm_get_version_id(realm: NativePointer): Long
    fun realm_get_library_version(): String
    fun realm_get_num_versions(realm: NativePointer): Long

    fun realm_schema_new(tables: List<Table>): NativePointer

    fun realm_config_new(): NativePointer
    fun realm_config_set_path(config: NativePointer, path: String)
    fun realm_config_set_schema_mode(config: NativePointer, mode: SchemaMode)
    fun realm_config_set_schema_version(config: NativePointer, version: Long)
    fun realm_config_set_schema(config: NativePointer, schema: NativePointer)
    fun realm_config_set_max_number_of_active_versions(config: NativePointer, maxNumberOfVersions: Long)
    fun realm_config_set_encryption_key(config: NativePointer, encryptionKey: ByteArray)
    fun realm_config_get_encryption_key(config: NativePointer): ByteArray?

    fun realm_schema_validate(schema: NativePointer, mode: SchemaValidationMode): Boolean

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
     */
    // The dispatcher argument is only used on Native to build a core scheduler dispatching to the
    // dispatcher. The realm itself must also be opened on the same thread
    fun realm_open(config: NativePointer, dispatcher: CoroutineDispatcher? = null): NativePointer
    fun realm_freeze(liveRealm: NativePointer): NativePointer
    fun realm_is_frozen(realm: NativePointer): Boolean
    fun realm_close(realm: NativePointer)

    fun realm_get_schema(realm: NativePointer): NativePointer
    fun realm_get_num_classes(realm: NativePointer): Long

    fun realm_release(p: NativePointer)

    fun realm_is_closed(realm: NativePointer): Boolean

    fun realm_begin_read(realm: NativePointer)
    fun realm_begin_write(realm: NativePointer)
    fun realm_commit(realm: NativePointer)
    fun realm_rollback(realm: NativePointer)
    fun realm_is_in_transaction(realm: NativePointer): Boolean

    // FIXME API-INTERNAL Maybe keep full realm_class_info_t/realm_property_info_t representation in Kotlin
    // FIXME API-INTERNAL How to return boolean 'found'? Currently throwing runtime exceptions
    fun realm_find_class(realm: NativePointer, name: String): ClassKey
    fun realm_object_create(realm: NativePointer, classKey: ClassKey): NativePointer
    fun realm_object_create_with_primary_key(realm: NativePointer, classKey: ClassKey, primaryKey: Any?): NativePointer
    fun realm_object_is_valid(obj: NativePointer): Boolean
    fun realm_object_resolve_in(obj: NativePointer, realm: NativePointer): NativePointer?

    fun realm_object_as_link(obj: NativePointer): Link

    fun realm_get_col_key(realm: NativePointer, table: String, col: String): ColumnKey

    fun <T> realm_get_value(obj: NativePointer, key: ColumnKey): T
    fun <T> realm_set_value(o: NativePointer, key: ColumnKey, value: T, isDefault: Boolean)

    // list
    fun realm_get_list(obj: NativePointer, key: ColumnKey): NativePointer
    fun realm_list_size(list: NativePointer): Long
    fun <T> realm_list_get(list: NativePointer, index: Long): T
    fun <T> realm_list_add(list: NativePointer, index: Long, value: T)
    fun <T> realm_list_set(list: NativePointer, index: Long, value: T): T
    fun realm_list_clear(list: NativePointer)
    fun realm_list_erase(list: NativePointer, index: Long)
    fun realm_list_resolve_in(list: NativePointer, realm: NativePointer): NativePointer?
    fun realm_list_is_valid(list: NativePointer): Boolean

    // query
    fun realm_query_parse(realm: NativePointer, table: String, query: String, vararg args: Any?): NativePointer

    fun realm_query_find_first(realm: NativePointer): Link?
    fun realm_query_find_all(query: NativePointer): NativePointer

    fun realm_results_resolve_in(results: NativePointer, realm: NativePointer): NativePointer
    fun realm_results_count(results: NativePointer): Long
    // FIXME OPTIMIZE Get many
    fun <T> realm_results_get(results: NativePointer, index: Long): Link

    fun realm_get_object(realm: NativePointer, link: Link): NativePointer

    fun realm_object_find_with_primary_key(realm: NativePointer, classKey: ClassKey, primaryKey: Any?): NativePointer?

    // delete
    fun realm_results_delete_all(results: NativePointer)
    fun realm_object_delete(obj: NativePointer)
    // FIXME Rest of delete calls are related to queries
    //  https://github.com/realm/realm-kotlin/issues/64
    // RLM_API bool realm_query_delete_all(const realm_query_t*);
    // RLM_API bool realm_results_delete_all(realm_results_t*);

    fun realm_object_add_notification_callback(obj: NativePointer, callback: Callback): NativePointer
    fun realm_results_add_notification_callback(results: NativePointer, callback: Callback): NativePointer
    fun realm_list_add_notification_callback(list: NativePointer, callback: Callback): NativePointer

    // App
    fun realm_app_get(
        appConfig: NativePointer,
        syncClientConfig: NativePointer,
        basePath: String,
    ): NativePointer
    fun realm_app_log_in_with_credentials(app: NativePointer, credentials: NativePointer, callback: CinteropCallback)

    // Sync client config
    fun realm_sync_client_config_new(): NativePointer
    fun realm_sync_client_config_set_metadata_mode(
        syncClientConfig: NativePointer,
        metadataMode: MetadataMode
    )
    fun realm_sync_client_config_set_log_level(syncClientConfig: NativePointer, level: Int)

    // AppConfig
    fun realm_network_transport_new(networkTransport: NetworkTransport): NativePointer
    fun realm_app_config_new(
        appId: String,
        networkTransport: NativePointer,
        baseUrl: String? = null
    ): NativePointer
    fun realm_app_config_set_base_url(appConfig: NativePointer, baseUrl: String)

    // Credentials
    fun realm_app_credentials_new_anonymous(): NativePointer
    fun realm_app_credentials_new_username_password(username: String, password: String): NativePointer
    fun realm_auth_credentials_get_provider(credentials: NativePointer): AuthProvider

    // Sync config
    fun realm_sync_config_new(user: NativePointer, partition: String): NativePointer
    fun realm_config_set_sync_config(realmConfiguration: NativePointer, syncConfiguration: NativePointer)
}
