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

// FIXME Consider adding marker interfaces NativeRealm, NativeRealmConfig, etc. as type parameter
//  to NativePointer. NOTE Verify that it is supported for Kotlin Native!
import io.realm.runtimeapi.NativePointer

@Suppress("FunctionNaming", "LongParameterList")
expect object RealmInterop {

    fun realm_get_library_version(): String

    fun realm_schema_new(tables: List<Table>): NativePointer

    fun realm_config_new(): NativePointer
    fun realm_config_set_path(config: NativePointer, path: String)
    fun realm_config_set_schema_mode(config: NativePointer, mode: SchemaMode)
    fun realm_config_set_schema_version(config: NativePointer, version: Long)
    fun realm_config_set_schema(config: NativePointer, schema: NativePointer)

    fun realm_schema_validate(schema: NativePointer): Boolean

    fun realm_open(config: NativePointer): NativePointer
    fun realm_close(realm: NativePointer)

    fun realm_get_schema(realm: NativePointer): NativePointer
    fun realm_get_num_classes(realm: NativePointer): Long

    fun realm_release(o: NativePointer)

    fun realm_begin_write(realm: NativePointer)
    fun realm_commit(realm: NativePointer)

    // FIXME Maybe keep full realm_class_info_t/realm_property_info_t representation in Kotlin
    // FIXME Only operating on key/Long to get going
    // FIXME How to return boolean 'found'? Currently throwing runtime exceptions
    fun realm_find_class(realm: NativePointer, name: String): Long
    fun realm_object_create(realm: NativePointer, key: Long): NativePointer
    // FIXME Optimize with direct paths instead of generic type parameter. Currently wrapping
    //  type and key-lookups internally
    fun <T> realm_set_value(realm: NativePointer?, o: NativePointer?, table: String, col: String, value: T, isDefault: Boolean)
    fun <T> realm_get_value(realm: NativePointer?, o: NativePointer?, table: String, col: String, type: PropertyType): T

    // Typed convenience methods
    fun objectGetString(realm: NativePointer?, o: NativePointer?, table: String, col: String): String
    fun objectSetString(realm: NativePointer?, o: NativePointer?, table: String, col: String, value: String)

//    override fun objectGetString(pointer: NativePointer, propertyName: String): String? {
//        TODO("Not yet implemented")
//    }
//
//    override fun objectSetString(pointer: NativePointer, propertyName: String, value: String?) {
//        TODO("Not yet implemented")
//    }
//
//    override fun objectGetInt64(pointer: NativePointer, propertyName: String): Long? {
//        TODO("Not yet implemented")
//    }
//
//    override fun objectSetInt64(pointer: NativePointer, propertyName: String, value: Long) {
//        TODO("Not yet implemented")
//    }

    fun realm_object_delete(obj: NativePointer)
    // FIXME Rest of delete calls are related to queries
    //  https://github.com/realm/realm-kotlin/issues/64
    // RLM_API bool realm_query_delete_all(const realm_query_t*);
    // RLM_API bool realm_results_delete_all(realm_results_t*);
}
