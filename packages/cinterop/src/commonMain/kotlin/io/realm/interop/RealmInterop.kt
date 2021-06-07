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

import kotlinx.coroutines.CoroutineDispatcher

// FIXME API-INTERNAL Consider adding marker interfaces NativeRealm, NativeRealmConfig, etc. as type parameter
//  to NativePointer. NOTE Verify that it is supported for Kotlin Native!

inline class ColumnKey(val key: Long)

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

    fun realm_schema_validate(schema: NativePointer, mode: SchemaValidationMode): Boolean

    fun realm_open(config: NativePointer, dispatcher: CoroutineDispatcher? = null): NativePointer
    fun realm_freeze(liveRealm: NativePointer): NativePointer
    fun realm_thaw(frozenRealm: NativePointer): NativePointer
    fun realm_is_frozen(realm: NativePointer): Boolean
    fun realm_close(realm: NativePointer)

    fun realm_get_schema(realm: NativePointer): NativePointer
    fun realm_get_num_classes(realm: NativePointer): Long

    fun realm_release(p: NativePointer)

    fun realm_is_closed(realm: NativePointer): Boolean

    fun realm_begin_read(realm: NativePointer)
    fun realm_begin_write(realm: NativePointer)
    fun realm_is_in_transaction(realm: NativePointer): Boolean
    fun realm_commit(realm: NativePointer)
    fun realm_rollback(realm: NativePointer)

    // FIXME API-INTERNAL Maybe keep full realm_class_info_t/realm_property_info_t representation in Kotlin
    // FIXME API-INTERNAL How to return boolean 'found'? Currently throwing runtime exceptions
    fun realm_find_class(realm: NativePointer, name: String): Long
    fun realm_object_create(realm: NativePointer, key: Long): NativePointer
    fun realm_object_create_with_primary_key(realm: NativePointer, key: Long, primaryKey: Any?): NativePointer
    fun realm_object_freeze(liveObject: NativePointer, frozenRealm: NativePointer): NativePointer
    fun realm_object_thaw(frozenObject: NativePointer, liveRealm: NativePointer): NativePointer

    fun realm_object_as_link(obj: NativePointer): Link

    fun realm_get_col_key(realm: NativePointer, table: String, col: String): ColumnKey

    fun <T> realm_get_value(obj: NativePointer, key: ColumnKey): T
    fun <T> realm_set_value(o: NativePointer, key: ColumnKey, value: T, isDefault: Boolean)

    // query
    fun realm_query_parse(realm: NativePointer, table: String, query: String, vararg args: Any): NativePointer

    fun realm_query_find_first(realm: NativePointer): Link?
    fun realm_query_find_all(query: NativePointer): NativePointer

    fun realm_results_freeze(liveResults: NativePointer, frozenRealm: NativePointer): NativePointer
    fun realm_results_thaw(frozenResults: NativePointer, liveRealm: NativePointer): NativePointer
    fun realm_results_count(results: NativePointer): Long
    // FIXME OPTIMIZE Get many
    fun <T> realm_results_get(results: NativePointer, index: Long): Link

    fun realm_get_object(realm: NativePointer, link: Link): NativePointer

    // delete
    fun realm_results_delete_all(results: NativePointer)
    fun realm_object_delete(obj: NativePointer)
    // FIXME Rest of delete calls are related to queries
    //  https://github.com/realm/realm-kotlin/issues/64
    // RLM_API bool realm_query_delete_all(const realm_query_t*);
    // RLM_API bool realm_results_delete_all(realm_results_t*);

    fun realm_object_add_notification_callback(obj: NativePointer, callback: Callback): NativePointer
    fun realm_results_add_notification_callback(results: NativePointer, callback: Callback): NativePointer
}
