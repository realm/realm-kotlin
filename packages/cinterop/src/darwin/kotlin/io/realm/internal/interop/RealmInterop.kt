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
// TODO https://github.com/realm/realm-kotlin/issues/303
@file:Suppress("TooGenericExceptionThrown", "TooGenericExceptionCaught")

package io.realm.internal.interop

import io.realm.internal.interop.Constants.ENCRYPTION_KEY_LENGTH
import io.realm.internal.interop.RealmInterop.propertyInfo
import io.realm.internal.interop.RealmInterop.safeKString
import io.realm.internal.interop.sync.AuthProvider
import io.realm.internal.interop.sync.CoreUserState
import io.realm.internal.interop.sync.MetadataMode
import io.realm.internal.interop.sync.NetworkTransport
import io.realm.internal.interop.sync.Response
import io.realm.mongodb.AppException
import io.realm.mongodb.SyncException
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValue
import kotlinx.cinterop.CVariable
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.cValue
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.getBytes
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.refTo
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import platform.posix.posix_errno
import platform.posix.pthread_threadid_np
import platform.posix.size_tVar
import platform.posix.strerror
import platform.posix.uint64_t
import platform.posix.uint8_tVar
import realm_wrapper.realm_app_error_t
import realm_wrapper.realm_class_info_t
import realm_wrapper.realm_clear_last_error
import realm_wrapper.realm_clone
import realm_wrapper.realm_config_t
import realm_wrapper.realm_error_t
import realm_wrapper.realm_find_property
import realm_wrapper.realm_get_last_error
import realm_wrapper.realm_http_header_t
import realm_wrapper.realm_http_request_method
import realm_wrapper.realm_http_request_t
import realm_wrapper.realm_http_response_t
import realm_wrapper.realm_link_t
import realm_wrapper.realm_list_t
import realm_wrapper.realm_object_t
import realm_wrapper.realm_property_info_t
import realm_wrapper.realm_release
import realm_wrapper.realm_scheduler_notify_func_t
import realm_wrapper.realm_scheduler_perform_work
import realm_wrapper.realm_scheduler_t
import realm_wrapper.realm_string_t
import realm_wrapper.realm_sync_client_metadata_mode
import realm_wrapper.realm_t
import realm_wrapper.realm_user_t
import realm_wrapper.realm_value_t
import realm_wrapper.realm_value_type
import realm_wrapper.realm_version_id_t
import kotlin.native.concurrent.freeze
import kotlin.native.internal.createCleaner

actual val INVALID_CLASS_KEY: ClassKey by lazy { ClassKey(realm_wrapper.RLM_INVALID_CLASS_KEY.toLong()) }
actual val INVALID_PROPERTY_KEY: PropertyKey by lazy { PropertyKey(realm_wrapper.RLM_INVALID_PROPERTY_KEY) }

private fun throwOnError() {
    memScoped {
        val error = alloc<realm_error_t>()
        if (realm_get_last_error(error.ptr)) {
            val message = "[${error.error}]: ${error.message?.toKString()}"
            val exception = coreErrorAsThrowable(error.error, message)

            realm_clear_last_error()
            throw exception
        }
    }
}

private fun checkedBooleanResult(boolean: Boolean): Boolean {
    if (!boolean) throwOnError(); return boolean
}

private fun <T : CPointed> checkedPointerResult(pointer: CPointer<T>?): CPointer<T>? {
    if (pointer == null) throwOnError(); return pointer
}

// FIXME API-INTERNAL Consider making NativePointer/CPointerWrapper generic to enforce typing

class CPointerWrapper(ptr: CPointer<out CPointed>?, managed: Boolean = true) : NativePointer {
    val ptr: CPointer<out CPointed>? = checkedPointerResult(ptr)

    @OptIn(ExperimentalStdlibApi::class)
    val cleaner = if (managed) {
        createCleaner(ptr.freeze()) {
            realm_release(it)
        }
    } else null
}

// Convenience type cast
private inline fun <T : CPointed> NativePointer.cptr(): CPointer<T> {
    return (this as CPointerWrapper).ptr as CPointer<T>
}

fun realm_string_t.set(memScope: MemScope, s: String): realm_string_t {
    val cstr = s.cstr
    data = cstr.getPointer(memScope)
    size = cstr.getBytes().size.toULong() - 1UL // realm_string_t is not zero-terminated
    return this
}

fun realm_value_t.set(memScope: MemScope, value: Any?): realm_value_t {
    when (value) {
        null -> {
            type = realm_value_type.RLM_TYPE_NULL
        }
        is String -> {
            type = realm_value_type.RLM_TYPE_STRING
            string.set(memScope, value)
        }
        is Boolean -> {
            type = realm_value_type.RLM_TYPE_BOOL
            boolean = value
        }
        is Byte, is Short, is Int, is Long -> {
            type = realm_value_type.RLM_TYPE_INT
            integer = (value as Number).toLong()
        }
        is Char -> {
            type = realm_value_type.RLM_TYPE_INT
            integer = value.toLong()
        }
        is Float -> {
            type = realm_value_type.RLM_TYPE_FLOAT
            fnum = value
        }
        is Double -> {
            type = realm_value_type.RLM_TYPE_DOUBLE
            dnum = value
        }
        is Timestamp -> {
            type = realm_value_type.RLM_TYPE_TIMESTAMP
            timestamp.apply {
                seconds = value.seconds
                nanoseconds = value.nanoSeconds
            }
        }
        else ->
            TODO("Value conversion not yet implemented for : ${value::class.simpleName}")
    }
    return this
}

fun realm_string_t.toKString(): String {
    if (size == 0UL) {
        return ""
    }
    val data: CPointer<ByteVarOf<Byte>>? = this.data
    val readBytes: ByteArray? = data?.readBytes(this.size.toInt())
    return readBytes?.toKString()!!
}

fun String.toRString(memScope: MemScope) = cValue<realm_string_t> {
    set(memScope, this@toRString)
}

@Suppress("LargeClass", "FunctionNaming")
actual object RealmInterop {

    actual fun realm_get_version_id(realm: NativePointer): Long {
        memScoped {
            val info = alloc<realm_version_id_t>()
            val found = alloc<BooleanVar>()
            checkedBooleanResult(
                realm_wrapper.realm_get_version_id(
                    realm.cptr(),
                    found.ptr,
                    info.ptr
                )
            )
            return if (found.value) {
                info.version.toLong()
            } else {
                throw IllegalStateException("No VersionId was available. Reading the VersionId requires a valid read transaction.")
            }
        }
    }

    actual fun realm_get_num_versions(realm: NativePointer): Long {
        memScoped {
            val versionsCount = alloc<ULongVar>()
            checkedBooleanResult(
                realm_wrapper.realm_get_num_versions(
                    realm.cptr(),
                    versionsCount.ptr
                )
            )
            return versionsCount.value.toLong()
        }
    }

    actual fun realm_get_library_version(): String {
        return realm_wrapper.realm_get_library_version().safeKString("library_version")
    }

    actual fun realm_schema_new(schema: List<Pair<ClassInfo, List<PropertyInfo>>>): NativePointer {
        val count = schema.size

        memScoped {
            val cclasses = allocArray<realm_class_info_t>(count)
            val cproperties = allocArray<CPointerVar<realm_property_info_t>>(count)
            for ((i, entry) in schema.withIndex()) {
                val (clazz, properties) = entry
                // Class
                cclasses[i].apply {
                    name = clazz.name.cstr.ptr
                    primary_key = clazz.primaryKey.cstr.ptr
                    num_properties = properties.size.toULong()
                    num_computed_properties = 0U
                    flags = clazz.flags
                }
                cproperties[i] =
                    allocArray<realm_property_info_t>(properties.size).getPointer(memScope)
                for ((j, property) in properties.withIndex()) {
                    cproperties[i]!![j].apply {
                        name = property.name.cstr.ptr
                        public_name = SCHEMA_NO_VALUE.cstr.ptr
                        link_target = property.linkTarget.cstr.ptr
                        link_origin_property_name = SCHEMA_NO_VALUE.cstr.ptr
                        type = property.type.nativeValue
                        collection_type = property.collectionType.nativeValue
                        flags = property.flags
                    }
                }
            }
            return CPointerWrapper(
                realm_wrapper.realm_schema_new(
                    cclasses,
                    count.toULong(),
                    cproperties
                )
            )
        }
    }

    actual fun realm_config_new(): NativePointer {
        return CPointerWrapper(realm_wrapper.realm_config_new())
    }

    actual fun realm_config_set_path(config: NativePointer, path: String) {
        realm_wrapper.realm_config_set_path(config.cptr(), path)
    }

    actual fun realm_config_set_schema_mode(config: NativePointer, mode: SchemaMode) {
        realm_wrapper.realm_config_set_schema_mode(
            config.cptr(),
            mode.nativeValue
        )
    }

    actual fun realm_config_set_schema_version(config: NativePointer, version: Long) {
        realm_wrapper.realm_config_set_schema_version(
            config.cptr(),
            version.toULong()
        )
    }

    actual fun realm_config_set_max_number_of_active_versions(
        config: NativePointer,
        maxNumberOfVersions: Long
    ) {
        realm_wrapper.realm_config_set_max_number_of_active_versions(
            config.cptr(),
            maxNumberOfVersions.toULong()
        )
    }

    actual fun realm_config_set_encryption_key(config: NativePointer, encryptionKey: ByteArray) {
        memScoped {
            val encryptionKeyPointer = encryptionKey.refTo(0).getPointer(memScope)
            realm_wrapper.realm_config_set_encryption_key(
                config.cptr(),
                encryptionKeyPointer as CPointer<uint8_tVar>,
                encryptionKey.size.toULong()
            )
        }
    }

    actual fun realm_config_get_encryption_key(config: NativePointer): ByteArray? {
        memScoped {
            val encryptionKey = ByteArray(ENCRYPTION_KEY_LENGTH)
            val encryptionKeyPointer = encryptionKey.refTo(0).getPointer(memScope)

            val keyLength = realm_wrapper.realm_config_get_encryption_key(
                config.cptr(),
                encryptionKeyPointer as CPointer<uint8_tVar>
            )

            if (keyLength == ENCRYPTION_KEY_LENGTH.toULong()) {
                return encryptionKey
            }

            return null
        }
    }

    actual fun realm_config_set_should_compact_on_launch_function(
        config: NativePointer,
        callback: CompactOnLaunchCallback
    ) {
        // TODO This is currently leaking. See https://github.com/realm/realm-core/issues/5222
        realm_wrapper.realm_config_set_should_compact_on_launch_function(
            config.cptr(),
            staticCFunction<COpaquePointer?, uint64_t, uint64_t, Boolean> { userdata, total, used ->
                stableUserData<CompactOnLaunchCallback>(userdata).get().invoke(
                    total.toLong(),
                    used.toLong()
                )
            },
            StableRef.create(callback).asCPointer()
        )
    }

    actual fun realm_config_set_migration_function(
        config: NativePointer,
        callback: MigrationCallback
    ) {
        realm_wrapper.realm_config_set_migration_function(
            config.cptr(),
            staticCFunction { userData, oldRealm, newRealm, schema ->
                safeUserData<MigrationCallback>(userData).migrate(
                    // These realm/schema pointers are only valid for the duraction of the
                    // migration so don't let ownership follow the NativePointer-objects
                    CPointerWrapper(oldRealm, false),
                    CPointerWrapper(newRealm, false),
                    CPointerWrapper(schema, false),
                )
            },
            // Leaking - Await fix of https://github.com/realm/realm-core/issues/5222
            StableRef.create(callback).asCPointer()
        )
    }

    actual fun realm_config_set_schema(config: NativePointer, schema: NativePointer) {
        realm_wrapper.realm_config_set_schema(config.cptr(), schema.cptr())
    }

    actual fun realm_schema_validate(schema: NativePointer, mode: SchemaValidationMode): Boolean {
        return checkedBooleanResult(
            realm_wrapper.realm_schema_validate(
                schema.cptr(),
                mode.nativeValue.toULong()
            )
        )
    }

    actual fun realm_open(config: NativePointer, dispatcher: CoroutineDispatcher?): NativePointer {
        printlntid("opening")
        // TODO Consider just grabbing the current dispatcher by
        //      val dispatcher = runBlocking { coroutineContext[CoroutineDispatcher.Key] }
        //  but requires opting in for @ExperimentalStdlibApi, and have really gotten it to play
        //  for default cases.
        if (dispatcher != null) {
            val scheduler = checkedPointerResult(createSingleThreadDispatcherScheduler(dispatcher))
            realm_wrapper.realm_config_set_scheduler(config.cptr(), scheduler)
        } else {
            // If there is no notification dispatcher use the default scheduler.
            // Re-verify if this is actually needed when notification scheduler is fully in place.
            val scheduler = checkedPointerResult(realm_wrapper.realm_scheduler_make_default())
            realm_wrapper.realm_config_set_scheduler(config.cptr(), scheduler)
        }
        val realmPtr = CPointerWrapper(realm_wrapper.realm_open(config.cptr<realm_config_t>()))
        // Ensure that we can read version information, etc.
        realm_begin_read(realmPtr)
        return realmPtr
    }

    actual fun realm_add_realm_changed_callback(realm: NativePointer, block: () -> Unit): RegistrationToken {
        return RegistrationToken(
            realm_wrapper.realm_add_realm_changed_callback(
                realm.cptr(),
                staticCFunction { userData ->
                    safeUserData<() -> Unit>(userData)()
                },
                StableRef.create(block).asCPointer(),
                staticCFunction { userdata ->
                    disposeUserData<(NativePointer, SyncErrorCallback) -> Unit>(userdata)
                }
            ).toLong()
        )
    }

    actual fun realm_remove_realm_changed_callback(realm: NativePointer, token: RegistrationToken) {
        realm_wrapper.realm_remove_realm_changed_callback(realm.cptr(), token.value.toULong())
    }

    actual fun realm_add_schema_changed_callback(realm: NativePointer, block: (NativePointer) -> Unit): RegistrationToken {
        return RegistrationToken(
            realm_wrapper.realm_add_schema_changed_callback(
                realm.cptr(),
                staticCFunction { userData, schema ->
                    safeUserData<(NativePointer) -> Unit>(userData)(CPointerWrapper(realm_clone(schema)))
                },
                StableRef.create(block).asCPointer(),
                staticCFunction { userdata ->
                    disposeUserData<(NativePointer, SyncErrorCallback) -> Unit>(userdata)
                }
            ).toLong()
        )
    }

    actual fun realm_remove_schema_changed_callback(realm: NativePointer, token: RegistrationToken) {
        realm_wrapper.realm_remove_schema_changed_callback(realm.cptr(), token.value.toULong())
    }

    actual fun realm_freeze(liveRealm: NativePointer): NativePointer {
        return CPointerWrapper(realm_wrapper.realm_freeze(liveRealm.cptr<realm_t>()))
    }

    actual fun realm_is_frozen(realm: NativePointer): Boolean {
        return realm_wrapper.realm_is_frozen(realm.cptr<realm_t>())
    }

    actual fun realm_close(realm: NativePointer) {
        checkedBooleanResult(realm_wrapper.realm_close(realm.cptr()))
    }

    actual fun realm_get_schema(realm: NativePointer): NativePointer {
        return CPointerWrapper(realm_wrapper.realm_get_schema(realm.cptr()))
    }

    actual fun realm_get_schema_version(realm: NativePointer): Long {
        return realm_wrapper.realm_get_schema_version(realm.cptr()).toLong()
    }

    actual fun realm_get_num_classes(realm: NativePointer): Long {
        return realm_wrapper.realm_get_num_classes(realm.cptr()).toLong()
    }

    actual fun realm_get_class_keys(realm: NativePointer): List<ClassKey> {
        memScoped {
            val max = realm_get_num_classes(realm)
            val keys = allocArray<UIntVar>(max)
            val outCount = alloc<size_tVar>()
            checkedBooleanResult(realm_wrapper.realm_get_class_keys(realm.cptr(), keys, max.convert(), outCount.ptr))
            if (max != outCount.value.toLong()) {
                error("Invalid schema: Insufficient keys; got ${outCount.value}, expected $max")
            }
            return (0 until max).map { ClassKey(keys[it].toLong()) }
        }
    }

    actual fun realm_find_class(realm: NativePointer, name: String): ClassKey? {
        memScoped {
            val found = alloc<BooleanVar>()
            val classInfo = alloc<realm_class_info_t>()
            checkedBooleanResult(
                realm_wrapper.realm_find_class(
                    realm.cptr(),
                    name,
                    found.ptr,
                    classInfo.ptr
                )
            )
            return if (found.value) {
                ClassKey(classInfo.key.toLong())
            } else {
                null
            }
        }
    }

    actual fun realm_get_class(realm: NativePointer, classKey: ClassKey): ClassInfo {
        memScoped {
            val classInfo = alloc<realm_class_info_t>()
            realm_wrapper.realm_get_class(realm.cptr(), classKey.key.toUInt(), classInfo.ptr)
            return with(classInfo) {
                ClassInfo(
                    name.safeKString("name"),
                    primary_key?.toKString() ?: SCHEMA_NO_VALUE,
                    num_properties.convert(),
                    num_computed_properties.convert(),
                    ClassKey(key.toLong()),
                    flags
                )
            }
        }
    }

    actual fun realm_get_class_properties(
        realm: NativePointer,
        classKey: ClassKey,
        max: Long
    ): List<PropertyInfo> {
        memScoped {
            val properties = allocArray<realm_property_info_t>(max)
            val outCount = alloc<size_tVar>()
            realm_wrapper.realm_get_class_properties(
                realm.cptr(),
                classKey.key.convert(),
                properties,
                max.convert(),
                outCount.ptr
            )
            outCount.value.toLong().let { count ->
                return if (count > 0) {
                    (0 until outCount.value.toLong()).map {
                        with(properties[it]) {
                            PropertyInfo(
                                name.safeKString("name"),
                                public_name.safeKString("public_name"),
                                PropertyType.from(type.toInt()),
                                CollectionType.from(collection_type.toInt()),
                                link_target.safeKString("link_target"),
                                link_origin_property_name.safeKString("link_origin_property_name"),
                                PropertyKey(key),
                                flags
                            )
                        }
                    }
                } else {
                    emptyList()
                }
            }
        }
    }

    actual fun realm_release(p: NativePointer) {
        realm_wrapper.realm_release((p as CPointerWrapper).ptr)
    }

    actual fun realm_is_closed(realm: NativePointer): Boolean {
        return realm_wrapper.realm_is_closed(realm.cptr())
    }

    actual fun realm_begin_read(realm: NativePointer) {
        checkedBooleanResult(realm_wrapper.realm_begin_read(realm.cptr()))
    }

    actual fun realm_begin_write(realm: NativePointer) {
        checkedBooleanResult(realm_wrapper.realm_begin_write(realm.cptr()))
    }

    actual fun realm_commit(realm: NativePointer) {
        checkedBooleanResult(realm_wrapper.realm_commit(realm.cptr()))
    }

    actual fun realm_rollback(realm: NativePointer) {
        checkedBooleanResult(realm_wrapper.realm_rollback(realm.cptr()))
    }

    actual fun realm_is_in_transaction(realm: NativePointer): Boolean {
        return realm_wrapper.realm_is_writable(realm.cptr())
    }

    actual fun realm_update_schema(realm: NativePointer, schema: NativePointer) {
        checkedBooleanResult(realm_wrapper.realm_update_schema(realm.cptr(), schema.cptr()))
    }

    actual fun realm_object_create(realm: NativePointer, classKey: ClassKey): NativePointer {
        return CPointerWrapper(
            realm_wrapper.realm_object_create(
                realm.cptr(),
                classKey.key.toUInt()
            )
        )
    }

    actual fun realm_object_create_with_primary_key(
        realm: NativePointer,
        classKey: ClassKey,
        primaryKey: Any?
    ): NativePointer {
        memScoped {
            return CPointerWrapper(
                realm_wrapper.realm_object_create_with_primary_key_by_ref(
                    realm.cptr(),
                    classKey.key.toUInt(),
                    to_realm_value(primaryKey).ptr
                )
            )
        }
    }

    actual fun realm_object_get_or_create_with_primary_key(
        realm: NativePointer,
        classKey: ClassKey,
        primaryKey: Any?
    ): NativePointer {
        memScoped {
            val found = alloc<BooleanVar>()
            return CPointerWrapper(
                realm_wrapper.realm_object_get_or_create_with_primary_key_by_ref(
                    realm.cptr(),
                    classKey.key.toUInt(),
                    to_realm_value(primaryKey).ptr,
                    found.ptr
                )
            )
        }
    }

    actual fun realm_object_is_valid(obj: NativePointer): Boolean {
        return realm_wrapper.realm_object_is_valid(obj.cptr())
    }

    actual fun realm_object_get_key(obj: NativePointer): Long {
        return realm_wrapper.realm_object_get_key(obj.cptr())
    }

    actual fun realm_object_resolve_in(obj: NativePointer, realm: NativePointer): NativePointer? {
        memScoped {
            val objectPointer = allocArray<CPointerVar<realm_object_t>>(1)
            checkedBooleanResult(
                realm_wrapper.realm_object_resolve_in(obj.cptr(), realm.cptr(), objectPointer)
            )
            return objectPointer[0]?.let {
                return CPointerWrapper(it)
            }
        }
    }

    actual fun realm_object_as_link(obj: NativePointer): Link {
        val link: CValue<realm_link_t> =
            realm_wrapper.realm_object_as_link(obj.cptr())
        link.useContents {
            return Link(ClassKey(this.target_table.toLong()), this.target)
        }
    }

    actual fun realm_object_get_table(obj: NativePointer): ClassKey {
        return ClassKey(realm_wrapper.realm_object_get_table(obj.cptr()).toLong())
    }

    actual fun realm_get_col_key(realm: NativePointer, classKey: ClassKey, col: String): PropertyKey {
        memScoped {
            return PropertyKey(propertyInfo(realm, classKey, col).key)
        }
    }

    actual fun <T> realm_get_value(obj: NativePointer, key: PropertyKey): T {
        memScoped {
            val value: realm_value_t = alloc()
            checkedBooleanResult(realm_wrapper.realm_get_value(obj.cptr(), key.key, value.ptr))
            return from_realm_value(value)
        }
    }

    private fun <T> from_realm_value(value: realm_value_t): T {
        return when (value.type) {
            realm_value_type.RLM_TYPE_NULL ->
                null as T
            realm_value_type.RLM_TYPE_INT ->
                value.integer
            realm_value_type.RLM_TYPE_BOOL ->
                value.boolean
            realm_value_type.RLM_TYPE_STRING ->
                value.string.toKString()
            realm_value_type.RLM_TYPE_FLOAT ->
                value.fnum
            realm_value_type.RLM_TYPE_DOUBLE ->
                value.dnum
            realm_value_type.RLM_TYPE_TIMESTAMP ->
                value.asTimestamp()
            realm_value_type.RLM_TYPE_LINK ->
                value.asLink()
            else ->
                TODO("Unsupported type for from_realm_value ${value.type.name}")
        } as T
    }

    actual fun <T> realm_set_value(o: NativePointer, key: PropertyKey, value: T, isDefault: Boolean) {
        memScoped {
            checkedBooleanResult(
                realm_wrapper.realm_set_value_by_ref(
                    o.cptr(),
                    key.key,
                    to_realm_value(value).ptr,
                    isDefault
                )
            )
        }
    }

    actual fun realm_get_list(obj: NativePointer, key: PropertyKey): NativePointer {
        return CPointerWrapper(realm_wrapper.realm_get_list(obj.cptr(), key.key))
    }

    actual fun realm_list_size(list: NativePointer): Long {
        memScoped {
            val size = alloc<ULongVar>()
            checkedBooleanResult(realm_wrapper.realm_list_size(list.cptr(), size.ptr))
            return size.value.toLong()
        }
    }

    actual fun <T> realm_list_get(list: NativePointer, index: Long): T {
        memScoped {
            val cvalue = alloc<realm_value_t>()
            checkedBooleanResult(
                realm_wrapper.realm_list_get(list.cptr(), index.toULong(), cvalue.ptr)
            )
            return from_realm_value(cvalue)
        }
    }

    actual fun <T> realm_list_add(list: NativePointer, index: Long, value: T) {
        memScoped {
            checkedBooleanResult(
                realm_wrapper.realm_list_add_by_ref(
                    list.cptr(),
                    index.toULong(),
                    to_realm_value(value).ptr
                )
            )
        }
    }

    actual fun <T> realm_list_set(list: NativePointer, index: Long, value: T): T {
        return memScoped {
            realm_list_get<T>(list, index).also {
                checkedBooleanResult(
                    realm_wrapper.realm_list_set_by_ref(
                        list.cptr(),
                        index.toULong(),
                        to_realm_value(value).ptr
                    )
                )
            }
        }
    }

    actual fun realm_list_clear(list: NativePointer) {
        checkedBooleanResult(realm_wrapper.realm_list_clear(list.cptr()))
    }

    actual fun realm_list_remove_all(list: NativePointer) {
        checkedBooleanResult(realm_wrapper.realm_list_remove_all(list.cptr()))
    }

    actual fun realm_list_erase(list: NativePointer, index: Long) {
        checkedBooleanResult(realm_wrapper.realm_list_erase(list.cptr(), index.toULong()))
    }

    actual fun realm_list_resolve_in(list: NativePointer, realm: NativePointer): NativePointer? {
        memScoped {
            val listPointer = allocArray<CPointerVar<realm_list_t>>(1)
            checkedBooleanResult(
                realm_wrapper.realm_list_resolve_in(list.cptr(), realm.cptr(), listPointer)
            )
            return listPointer[0]?.let {
                CPointerWrapper(it)
            }
        }
    }

    actual fun realm_list_is_valid(list: NativePointer): Boolean {
        return realm_wrapper.realm_list_is_valid(list.cptr())
    }

    @Suppress("ComplexMethod", "LongMethod")
    private fun <T> MemScope.to_realm_value(value: T): realm_value_t {
        val cvalue: realm_value_t = alloc()
        when (value) {
            null -> {
                cvalue.type = realm_value_type.RLM_TYPE_NULL
            }
            is Byte -> {
                cvalue.type = realm_value_type.RLM_TYPE_INT
                cvalue.integer = value.toLong()
            }
            is Char -> {
                cvalue.type = realm_value_type.RLM_TYPE_INT
                cvalue.integer = value.toLong()
            }
            is Short -> {
                cvalue.type = realm_value_type.RLM_TYPE_INT
                cvalue.integer = value.toLong()
            }
            is Int -> {
                cvalue.type = realm_value_type.RLM_TYPE_INT
                cvalue.integer = value.toLong()
            }
            is Long -> {
                cvalue.type = realm_value_type.RLM_TYPE_INT
                cvalue.integer = value as Long
            }
            is Boolean -> {
                cvalue.type = realm_value_type.RLM_TYPE_BOOL
                cvalue.boolean = value as Boolean
            }
            is String -> {
                cvalue.type = realm_value_type.RLM_TYPE_STRING
                cvalue.string.set(this, value as String)
            }
            is Float -> {
                cvalue.type = realm_value_type.RLM_TYPE_FLOAT
                cvalue.fnum = value as Float
            }
            is Double -> {
                cvalue.type = realm_value_type.RLM_TYPE_DOUBLE
                cvalue.dnum = value as Double
            }
            is Timestamp -> {
                cvalue.type = realm_value_type.RLM_TYPE_TIMESTAMP
                cvalue.timestamp.apply {
                    seconds = value.seconds
                    nanoseconds = value.nanoSeconds
                }
            }
            is RealmObjectInterop -> {
                cvalue.type = realm_value_type.RLM_TYPE_LINK
                val nativePointer =
                    value.`$realm$ObjectPointer` ?: error("Cannot set unmanaged object")
                realm_wrapper.realm_object_as_link(nativePointer?.cptr()).useContents {
                    cvalue.link.apply {
                        target_table = this@useContents.target_table
                        target = this@useContents.target
                    }
                }
            }
            //    RLM_TYPE_BINARY,
            //    RLM_TYPE_TIMESTAMP,
            //    RLM_TYPE_DECIMAL128,
            //    RLM_TYPE_OBJECT_ID,
            //    RLM_TYPE_UUID,
            else -> {
                TODO("Unsupported type for to_realm_value `${value!!::class.simpleName}`")
            }
        }
        return cvalue
    }

    actual fun realm_query_parse(
        realm: NativePointer,
        classKey: ClassKey,
        query: String,
        vararg args: Any?
    ): NativePointer {
        memScoped {
            val count = args.size
            val cArgs = allocArray<realm_value_t>(count)
            args.mapIndexed { i, arg ->
                cArgs[i].apply {
                    set(memScope, arg)
                }
            }
            return CPointerWrapper(
                realm_wrapper.realm_query_parse(
                    realm.cptr(),
                    classKey.key.toUInt(),
                    query,
                    count.toULong(),
                    cArgs
                )
            )
        }
    }

    actual fun realm_query_parse_for_results(
        results: NativePointer,
        query: String,
        vararg args: Any?
    ): NativePointer {
        memScoped {
            val count = args.size
            val cArgs = allocArray<realm_value_t>(count)
            args.mapIndexed { i, arg ->
                cArgs[i].apply {
                    set(memScope, arg)
                }
            }
            return CPointerWrapper(
                realm_wrapper.realm_query_parse_for_results(
                    results.cptr(),
                    query,
                    count.toULong(),
                    cArgs
                )
            )
        }
    }

    actual fun realm_query_find_first(query: NativePointer): Link? {
        memScoped {
            val found = alloc<BooleanVar>()
            val value = alloc<realm_value_t>()
            checkedBooleanResult(
                realm_wrapper.realm_query_find_first(
                    query.cptr(),
                    value.ptr,
                    found.ptr
                )
            )
            if (!found.value) {
                return null
            }
            if (value.type != realm_value_type.RLM_TYPE_LINK) {
                error("Query did not return link but ${value.type}")
            }
            return Link(ClassKey(value.link.target_table.toLong()), value.link.target)
        }
    }

    actual fun realm_query_find_all(query: NativePointer): NativePointer {
        return CPointerWrapper(realm_wrapper.realm_query_find_all(query.cptr()))
    }

    actual fun realm_query_count(query: NativePointer): Long {
        memScoped {
            val count = alloc<ULongVar>()
            checkedBooleanResult(realm_wrapper.realm_query_count(query.cptr(), count.ptr))
            return count.value.toLong()
        }
    }

    actual fun realm_query_append_query(
        query: NativePointer,
        filter: String,
        vararg args: Any?
    ): NativePointer {
        memScoped {
            val count = args.size
            val cArgs = allocArray<realm_value_t>(count)
            args.mapIndexed { i, arg ->
                cArgs[i].apply {
                    set(memScope, arg)
                }
            }
            return CPointerWrapper(
                realm_wrapper.realm_query_append_query(
                    query.cptr(),
                    filter,
                    count.toULong(),
                    cArgs
                )
            )
        }
    }

    actual fun realm_results_resolve_in(
        results: NativePointer,
        realm: NativePointer
    ): NativePointer {
        return CPointerWrapper(
            realm_wrapper.realm_results_resolve_in(
                results.cptr(),
                realm.cptr()
            )
        )
    }

    actual fun realm_results_count(results: NativePointer): Long {
        memScoped {
            val count = alloc<ULongVar>()
            checkedBooleanResult(realm_wrapper.realm_results_count(results.cptr(), count.ptr))
            return count.value.toLong()
        }
    }

    actual fun <T> realm_results_average(
        results: NativePointer,
        propertyKey: PropertyKey
    ): Pair<Boolean, T> {
        memScoped {
            val found = cValue<BooleanVar>().ptr
            val average = alloc<realm_value_t>()
            checkedBooleanResult(
                realm_wrapper.realm_results_average(
                    results.cptr(),
                    propertyKey.key,
                    average.ptr,
                    found
                )
            )
            return found.pointed.value to from_realm_value(average)
        }
    }

    actual fun <T> realm_results_sum(results: NativePointer, propertyKey: PropertyKey): T {
        memScoped {
            val sum = alloc<realm_value_t>()
            checkedBooleanResult(
                realm_wrapper.realm_results_sum(
                    results.cptr(),
                    propertyKey.key,
                    sum.ptr,
                    null
                )
            )
            return from_realm_value(sum)
        }
    }

    actual fun <T> realm_results_max(results: NativePointer, propertyKey: PropertyKey): T {
        memScoped {
            val max = alloc<realm_value_t>()
            checkedBooleanResult(
                realm_wrapper.realm_results_max(
                    results.cptr(),
                    propertyKey.key,
                    max.ptr,
                    null
                )
            )
            return from_realm_value(max)
        }
    }

    actual fun <T> realm_results_min(results: NativePointer, propertyKey: PropertyKey): T {
        memScoped {
            val min = alloc<realm_value_t>()
            checkedBooleanResult(
                realm_wrapper.realm_results_min(
                    results.cptr(),
                    propertyKey.key,
                    min.ptr,
                    null
                )
            )
            return from_realm_value(min)
        }
    }

    actual fun realm_results_get(results: NativePointer, index: Long): Link {
        memScoped {
            val value = alloc<realm_value_t>()
            checkedBooleanResult(
                realm_wrapper.realm_results_get(
                    results.cptr(),
                    index.toULong(),
                    value.ptr
                )
            )
            return value.asLink()
        }
    }

    actual fun realm_get_object(realm: NativePointer, link: Link): NativePointer {
        val ptr = checkedPointerResult(
            realm_wrapper.realm_get_object(
                realm.cptr(),
                link.classKey.key.toUInt(),
                link.objKey
            )
        )
        return CPointerWrapper(ptr)
    }

    actual fun realm_object_find_with_primary_key(
        realm: NativePointer,
        classKey: ClassKey,
        primaryKey: Any?
    ): NativePointer? {
        val ptr = memScoped {
            val found = alloc<BooleanVar>()
            realm_wrapper.realm_object_find_with_primary_key_by_ref(
                realm.cptr(),
                classKey.key.toUInt(),
                to_realm_value(primaryKey).ptr,
                found.ptr
            )
        }
        val checkedPtr = checkedPointerResult(ptr)
        return if (checkedPtr != null) CPointerWrapper(checkedPtr) else null
    }

    actual fun realm_results_delete_all(results: NativePointer) {
        checkedBooleanResult(realm_wrapper.realm_results_delete_all(results.cptr()))
    }

    actual fun realm_object_delete(obj: NativePointer) {
        checkedBooleanResult(realm_wrapper.realm_object_delete(obj.cptr()))
    }

    actual fun realm_object_add_notification_callback(
        obj: NativePointer,
        callback: Callback
    ): NativePointer {
        return CPointerWrapper(
            realm_wrapper.realm_object_add_notification_callback(
                obj.cptr(),
                // Use the callback as user data
                StableRef.create(callback).asCPointer(),
                staticCFunction<COpaquePointer?, Unit> { userdata ->
                    userdata?.asStableRef<Callback>()?.dispose()
                        ?: error("Notification callback data should never be null")
                },
                // Change callback
                staticCFunction<COpaquePointer?, CPointer<realm_wrapper.realm_object_changes_t>?, Unit> { userdata, change ->
                    try {
                        userdata?.asStableRef<Callback>()?.get()?.onChange(
                            CPointerWrapper(
                                change,
                                managed = false
                            )
                        ) // FIXME use managed pointer https://github.com/realm/realm-kotlin/issues/147
                            ?: error("Notification callback data should never be null")
                    } catch (e: Exception) {
                        // TODO API-NOTIFICATION Consider catching errors and propagate to error
                        //  callback like the C-API error callback below
                        //  https://github.com/realm/realm-kotlin/issues/303
                        e.printStackTrace()
                    }
                },
                staticCFunction<COpaquePointer?, CPointer<realm_wrapper.realm_async_error_t>?, Unit> { userdata, asyncError ->
                    // TODO Propagate errors to callback
                    //  https://github.com/realm/realm-kotlin/issues/303
                },
                // C-API currently uses the realm's default scheduler no matter what passed here
                null
            ),
            managed = false
        )
    }

    actual fun realm_results_add_notification_callback(
        results: NativePointer,
        callback: Callback
    ): NativePointer {
        return CPointerWrapper(
            realm_wrapper.realm_results_add_notification_callback(
                results.cptr(),
                // Use the callback as user data
                StableRef.create(callback).asCPointer(),
                staticCFunction<COpaquePointer?, Unit> { userdata ->
                    userdata?.asStableRef<Callback>()?.dispose()
                        ?: error("Notification callback data should never be null")
                },
                // Change callback
                staticCFunction<COpaquePointer?, CPointer<realm_wrapper.realm_collection_changes_t>?, Unit> { userdata, change ->
                    try {
                        userdata?.asStableRef<Callback>()?.get()?.onChange(
                            CPointerWrapper(
                                change,
                                managed = false
                            )
                        ) // FIXME use managed pointer https://github.com/realm/realm-kotlin/issues/147
                            ?: error("Notification callback data should never be null")
                    } catch (e: Exception) {
                        // TODO API-NOTIFICATION Consider catching errors and propagate to error
                        //  callback like the C-API error callback below
                        //  https://github.com/realm/realm-kotlin/issues/303
                        e.printStackTrace()
                    }
                },
                staticCFunction<COpaquePointer?, CPointer<realm_wrapper.realm_async_error_t>?, Unit> { userdata, asyncError ->
                    // TODO Propagate errors to callback
                    //  https://github.com/realm/realm-kotlin/issues/303
                },
                // C-API currently uses the realm's default scheduler no matter what passed here
                null
            ),
            managed = false
        )
    }

    actual fun realm_list_add_notification_callback(
        list: NativePointer,
        callback: Callback
    ): NativePointer {
        return CPointerWrapper(
            realm_wrapper.realm_list_add_notification_callback(
                list.cptr(),
                // Use the callback as user data
                StableRef.create(callback).asCPointer(),
                staticCFunction<COpaquePointer?, Unit> { userdata ->
                    userdata?.asStableRef<Callback>()?.dispose()
                        ?: error("Notification callback data should never be null")
                },
                // Change callback
                staticCFunction { userdata, change ->
                    try {
                        userdata?.asStableRef<Callback>()?.get()?.onChange(
                            CPointerWrapper(
                                change,
                                managed = false
                            )
                        ) // FIXME use managed pointer https://github.com/realm/realm-kotlin/issues/147
                            ?: error("Notification callback data should never be null")
                    } catch (e: Exception) {
                        // TODO API-NOTIFICATION Consider catching errors and propagate to error
                        //  callback like the C-API error callback below
                        //  https://github.com/realm/realm-kotlin/issues/303
                        e.printStackTrace()
                    }
                },
                staticCFunction<COpaquePointer?, CPointer<realm_wrapper.realm_async_error_t>?, Unit> { userdata, asyncError ->
                    // TODO Propagate errors to callback
                    //  https://github.com/realm/realm-kotlin/issues/303
                },
                // C-API currently uses the realm's default scheduler no matter what passed here
                null
            ),
            managed = false
        )
    }

    actual fun realm_object_changes_get_modified_properties(change: NativePointer): List<PropertyKey> {
        val propertyCount = realm_wrapper.realm_object_changes_get_num_modified_properties(change.cptr())

        memScoped {
            val propertyKeys = allocArray<LongVar>(propertyCount.toLong())
            realm_wrapper.realm_object_changes_get_modified_properties(change.cptr(), propertyKeys, propertyCount)
            return (0 until propertyCount.toInt()).map { PropertyKey(propertyKeys[it].toLong()) }
        }
    }

    private inline fun <reified T : CVariable> MemScope.initArray(size: CArrayPointer<ULongVar>) = allocArray<T>(size[0].toInt())

    actual fun <T, R> realm_collection_changes_get_indices(change: NativePointer, builder: ListChangeSetBuilder<T, R>) {
        memScoped {
            val insertionCount = allocArray<ULongVar>(1)
            val deletionCount = allocArray<ULongVar>(1)
            val modificationCount = allocArray<ULongVar>(1)
            val movesCount = allocArray<ULongVar>(1)

            realm_wrapper.realm_collection_changes_get_num_changes(change.cptr(), deletionCount, insertionCount, modificationCount, movesCount)

            val deletionIndices = initArray<ULongVar>(deletionCount)
            val insertionIndices = initArray<ULongVar>(insertionCount)
            val modificationIndices = initArray<ULongVar>(modificationCount)
            val modificationIndicesAfter = initArray<ULongVar>(modificationCount)
            val moves = initArray<realm_wrapper.realm_collection_move_t>(movesCount)

            realm_wrapper.realm_collection_changes_get_changes(
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

            builder.initIndicesArray(builder::insertionIndices, insertionCount, insertionIndices)
            builder.initIndicesArray(builder::deletionIndices, deletionCount, deletionIndices)
            builder.initIndicesArray(builder::modificationIndices, modificationCount, modificationIndices)
            builder.initIndicesArray(builder::modificationIndicesAfter, modificationCount, modificationIndicesAfter)
            builder.movesCount = movesCount[0].toInt()
        }
    }

    actual fun <T, R> realm_collection_changes_get_ranges(change: NativePointer, builder: ListChangeSetBuilder<T, R>) {
        memScoped {
            val insertRangesCount = allocArray<ULongVar>(1)
            val deleteRangesCount = allocArray<ULongVar>(1)
            val modificationRangesCount = allocArray<ULongVar>(1)
            val movesCount = allocArray<ULongVar>(1)

            realm_wrapper.realm_collection_changes_get_num_ranges(
                change.cptr(),
                deleteRangesCount,
                insertRangesCount,
                modificationRangesCount,
                movesCount
            )

            val insertionRanges = initArray<realm_wrapper.realm_index_range_t>(insertRangesCount)
            val modificationRanges = initArray<realm_wrapper.realm_index_range_t>(modificationRangesCount)
            val modificationRangesAfter = initArray<realm_wrapper.realm_index_range_t>(modificationRangesCount)
            val deletionRanges = initArray<realm_wrapper.realm_index_range_t>(deleteRangesCount)
            val moves = initArray<realm_wrapper.realm_collection_move_t>(movesCount)

            realm_wrapper.realm_collection_changes_get_ranges(
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

            builder.initRangesArray(builder::deletionRanges, deleteRangesCount, deletionRanges)
            builder.initRangesArray(builder::insertionRanges, insertRangesCount, insertionRanges)
            builder.initRangesArray(builder::modificationRanges, modificationRangesCount, modificationRanges)
            builder.initRangesArray(builder::modificationRangesAfter, modificationRangesCount, modificationRangesAfter)
        }
    }

    // TODO sync config shouldn't be null
    actual fun realm_app_get(
        appConfig: NativePointer,
        syncClientConfig: NativePointer,
        basePath: String
    ): NativePointer {
        realm_wrapper.realm_sync_client_config_set_base_file_path(
            syncClientConfig.cptr(), basePath
        )
        return CPointerWrapper(realm_wrapper.realm_app_get(appConfig.cptr(), syncClientConfig.cptr()))
    }

    actual fun realm_app_get_current_user(app: NativePointer): NativePointer? {
        val currentUserPtr: CPointer<realm_user_t>? = realm_wrapper.realm_app_get_current_user(app.cptr())
        return nativePointerOrNull(currentUserPtr)
    }

    actual fun realm_app_log_in_with_credentials(
        app: NativePointer,
        credentials: NativePointer,
        callback: AppCallback<NativePointer>
    ) {
        realm_wrapper.realm_app_log_in_with_credentials(
            app.cptr(),
            credentials.cptr(),
            staticCFunction { userData, user, error: CPointer<realm_app_error_t>? ->
                // Remember to clone user object or else it will go out of scope right after we leave this callback
                handleAppCallback(userData, error) { CPointerWrapper(realm_clone(user)) }
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userdata -> disposeUserData<AppCallback<NativePointer>>(userdata) }
        )
    }

    actual fun realm_app_log_out(
        app: NativePointer,
        user: NativePointer,
        callback: AppCallback<Unit>
    ) {
        checkedBooleanResult(
            realm_wrapper.realm_app_log_out(
                app.cptr(),
                user.cptr(),
                staticCFunction { userData, error ->
                    handleAppCallback(userData, error) { /* No-op, returns Unit */ }
                },
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata -> disposeUserData<AppCallback<NativePointer>>(userdata) }
            )
        )
    }

    actual fun realm_clear_cached_apps() {
        realm_wrapper.realm_clear_cached_apps()
    }

    actual fun realm_user_get_identity(user: NativePointer): String {
        return realm_wrapper.realm_user_get_identity(user.cptr()).safeKString("identity")
    }

    actual fun realm_user_is_logged_in(user: NativePointer): Boolean {
        return realm_wrapper.realm_user_is_logged_in(user.cptr())
    }

    actual fun realm_user_log_out(user: NativePointer) {
        checkedBooleanResult(realm_wrapper.realm_user_log_out(user.cptr()))
    }

    actual fun realm_user_get_state(user: NativePointer): CoreUserState {
        return CoreUserState.of(realm_wrapper.realm_user_get_state(user.cptr()))
    }

    actual fun realm_sync_client_config_new(): NativePointer {
        return CPointerWrapper(realm_wrapper.realm_sync_client_config_new())
    }

    actual fun realm_sync_client_config_set_log_callback(
        syncClientConfig: NativePointer,
        callback: SyncLogCallback
    ) {
        realm_wrapper.realm_sync_client_config_set_log_callback(
            syncClientConfig.cptr(),
            staticCFunction { userData, logLevel, message ->
                val userDataLogCallback = safeUserData<SyncLogCallback>(userData)
                userDataLogCallback.log(logLevel.toShort(), message?.toKString())
            },
            StableRef.create(callback.freeze()).asCPointer(),
            staticCFunction { userData -> disposeUserData<() -> SyncLogCallback>(userData) }
        )
    }

    actual fun realm_sync_client_config_set_log_level(
        syncClientConfig: NativePointer,
        level: CoreLogLevel
    ) {
        realm_wrapper.realm_sync_client_config_set_log_level(
            syncClientConfig.cptr(),
            level.priority.toUInt()
        )
    }

    actual fun realm_sync_client_config_set_metadata_mode(
        syncClientConfig: NativePointer,
        metadataMode: MetadataMode
    ) {
        realm_wrapper.realm_sync_client_config_set_metadata_mode(
            syncClientConfig.cptr(),
            realm_sync_client_metadata_mode.byValue(metadataMode.metadataValue.toUInt())
        )
    }

    actual fun realm_sync_set_error_handler(
        syncConfig: NativePointer,
        errorHandler: SyncErrorCallback
    ) {
        realm_wrapper.realm_sync_config_set_error_handler(
            syncConfig.cptr(),
            staticCFunction { userData, syncSession, error ->
                val syncException = error.useContents {
                    val message = "${this.detailed_message} [" +
                        "error_code.category='${this.error_code.category}', " +
                        "error_code.value='${this.error_code.value}', " +
                        "error_code.message='${this.error_code.message}', " +
                        "is_fatal='${this.is_fatal}', " +
                        "is_unrecognized_by_client='${this.is_unrecognized_by_client}'" +
                        "]"
                    SyncException(message)
                }
                val errorCallback = safeUserData<SyncErrorCallback>(userData)
                val session = CPointerWrapper(realm_clone(syncSession))
                errorCallback.onSyncError(session, syncException)
            },
            StableRef.create(errorHandler).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<(NativePointer, SyncErrorCallback) -> Unit>(userdata)
            }
        )
    }

    actual fun realm_network_transport_new(networkTransport: NetworkTransport): NativePointer {
        return CPointerWrapper(
            realm_wrapper.realm_http_transport_new(
                newRequestLambda,
                StableRef.create(networkTransport).asCPointer(),
                staticCFunction { userdata: CPointer<out CPointed>? ->
                    disposeUserData<NetworkTransport>(userdata)
                }
            )
        )
    }

    @Suppress("LongParameterList")
    actual fun realm_app_config_new(
        appId: String,
        networkTransport: NativePointer,
        baseUrl: String?,
        platform: String,
        platformVersion: String,
        sdkVersion: String
    ): NativePointer {
        val appConfig = realm_wrapper.realm_app_config_new(appId, networkTransport.cptr())

        realm_wrapper.realm_app_config_set_platform(appConfig, platform)
        realm_wrapper.realm_app_config_set_platform_version(appConfig, platformVersion)
        realm_wrapper.realm_app_config_set_sdk_version(appConfig, sdkVersion)

        // TODO Fill in appropriate app meta data
        //  https://github.com/realm/realm-kotlin/issues/407
        realm_wrapper.realm_app_config_set_local_app_version(appConfig, "APP_VERSION")

        baseUrl?.let { realm_wrapper.realm_app_config_set_base_url(appConfig, it) }

        return CPointerWrapper(appConfig)
    }

    actual fun realm_app_config_set_base_url(appConfig: NativePointer, baseUrl: String) {
        realm_wrapper.realm_app_config_set_base_url(appConfig.cptr(), baseUrl)
    }

    actual fun realm_app_credentials_new_anonymous(): NativePointer {
        return CPointerWrapper(realm_wrapper.realm_app_credentials_new_anonymous())
    }

    actual fun realm_app_credentials_new_email_password(
        username: String,
        password: String
    ): NativePointer {
        memScoped {
            val realmStringPassword = password.toRString(this)
            return CPointerWrapper(
                realm_wrapper.realm_app_credentials_new_email_password(
                    username,
                    realmStringPassword
                )
            )
        }
    }

    actual fun realm_auth_credentials_get_provider(credentials: NativePointer): AuthProvider {
        return AuthProvider.of(realm_wrapper.realm_auth_credentials_get_provider(credentials.cptr()))
    }

    actual fun realm_app_email_password_provider_client_register_email(
        app: NativePointer,
        email: String,
        password: String,
        callback: AppCallback<Unit>
    ) {
        memScoped {
            checkedBooleanResult(
                realm_wrapper.realm_app_email_password_provider_client_register_email(
                    app.cptr(),
                    email,
                    password.toRString(this),
                    staticCFunction { userData, error ->
                        handleAppCallback(userData, error) { /* No-op, returns Unit */ }
                    },
                    StableRef.create(callback).asCPointer(),
                    staticCFunction { userData -> disposeUserData<AppCallback<Unit>>(userData) }
                )
            )
        }
    }

    actual fun realm_sync_config_new(
        user: NativePointer,
        partition: String
    ): NativePointer {
        return CPointerWrapper(realm_wrapper.realm_sync_config_new(user.cptr(), partition))
    }

    actual fun realm_config_set_sync_config(realmConfiguration: NativePointer, syncConfiguration: NativePointer) {
        realm_wrapper.realm_config_set_sync_config(realmConfiguration.cptr(), syncConfiguration.cptr())
    }

    private fun nativePointerOrNull(ptr: CPointer<*>?, managed: Boolean = true): NativePointer? {
        return if (ptr != null) {
            CPointerWrapper(ptr, managed)
        } else {
            null
        }
    }

    private fun MemScope.classInfo(
        realm: NativePointer,
        table: String
    ): realm_class_info_t {
        val found = alloc<BooleanVar>()
        val classInfo = alloc<realm_class_info_t>()
        checkedBooleanResult(
            realm_wrapper.realm_find_class(
                realm.cptr(),
                table,
                found.ptr,
                classInfo.ptr
            )
        )
        return classInfo
    }

    private fun MemScope.propertyInfo(
        realm: NativePointer,
        classKey: ClassKey,
        col: String
    ): realm_property_info_t {
        val found = alloc<BooleanVar>()
        val propertyInfo = alloc<realm_property_info_t>()
        checkedBooleanResult(
            realm_find_property(
                realm.cptr(),
                classKey.key.toUInt(),
                col,
                found.ptr,
                propertyInfo.ptr
            )
        )
        return propertyInfo
    }

    private fun realm_value_t.asTimestamp(): Timestamp {
        if (this.type != realm_value_type.RLM_TYPE_TIMESTAMP) {
            error("Value is not of type Timestamp: $this.type")
        }
        return TimestampImpl(this.timestamp.seconds, this.timestamp.nanoseconds)
    }

    private fun realm_value_t.asLink(): Link {
        if (this.type != realm_value_type.RLM_TYPE_LINK) {
            error("Value is not of type link: $this.type")
        }
        return Link(ClassKey(this.link.target_table.toLong()), this.link.target)
    }

    private fun CPointer<ByteVar>?.safeKString(identifier: String? = null): String {
        return this?.toKString()
            ?: throw NullPointerException(identifier?.let { "'$identifier' shouldn't be null." })
    }

    private fun createSingleThreadDispatcherScheduler(
        dispatcher: CoroutineDispatcher
    ): CPointer<realm_scheduler_t> {
        printlntid("createSingleThreadDispatcherScheduler")
        val scheduler = SingleThreadDispatcherScheduler(tid(), dispatcher)

        val capi_scheduler: CPointer<realm_scheduler_t> = checkedPointerResult(
            realm_wrapper.realm_scheduler_new(
                // userdata: kotlinx.cinterop.CValuesRef<*>?,
                scheduler.ref,

                // free: realm_wrapper.realm_free_userdata_func_t? /* = kotlinx.cinterop.CPointer<kotlinx.cinterop.CFunction<(kotlinx.cinterop.COpaquePointer? /* = kotlinx.cinterop.CPointer<out kotlinx.cinterop.CPointed>? */) -> kotlin.Unit>>? */,
                staticCFunction<COpaquePointer?, Unit> { userdata ->
                    printlntid("free")
                    userdata?.asStableRef<SingleThreadDispatcherScheduler>()?.dispose()
                },

                // notify: realm_wrapper.realm_scheduler_notify_func_t? /* = kotlinx.cinterop.CPointer<kotlinx.cinterop.CFunction<(kotlinx.cinterop.COpaquePointer? /* = kotlinx.cinterop.CPointer<out kotlinx.cinterop.CPointed>? */) -> kotlin.Unit>>? */,
                staticCFunction<COpaquePointer?, Unit> { userdata ->
                    // Must be thread safe
                    val scheduler =
                        userdata!!.asStableRef<SingleThreadDispatcherScheduler>().get()
                    printlntid("$scheduler notify")
                    try {
                        scheduler.notify()
                    } catch (e: Exception) {
                        // Should never happen, but is included for development to get some indicators
                        // on errors instead of silent crashes.
                        e.printStackTrace()
                    }
                },

                // is_on_thread: realm_wrapper.realm_scheduler_is_on_thread_func_t? /* = kotlinx.cinterop.CPointer<kotlinx.cinterop.CFunction<(kotlinx.cinterop.COpaquePointer? /* = kotlinx.cinterop.CPointer<out kotlinx.cinterop.CPointed>? */) -> kotlin.Boolean>>? */,
                staticCFunction<COpaquePointer?, Boolean> { userdata ->
                    // Must be thread safe
                    val scheduler =
                        userdata!!.asStableRef<SingleThreadDispatcherScheduler>().get()
                    printlntid("is_on_thread[$scheduler] ${scheduler.threadId} " + tid())
                    scheduler.threadId == tid()
                },

                // is_same_as: realm_wrapper.realm_scheduler_is_same_as_func_t? /* = kotlinx.cinterop.CPointer<kotlinx.cinterop.CFunction<(kotlinx.cinterop.COpaquePointer? /* = kotlinx.cinterop.CPointer<out kotlinx.cinterop.CPointed>? */, kotlinx.cinterop.COpaquePointer? /* = kotlinx.cinterop.CPointer<out kotlinx.cinterop.CPointed>? */) -> kotlin.Boolean>>? */,
                staticCFunction<COpaquePointer?, COpaquePointer?, Boolean> { userdata, other ->
                    userdata == other
                },

                // can_deliver_notifications: realm_wrapper.realm_scheduler_can_deliver_notifications_func_t? /* = kotlinx.cinterop.CPointer<kotlinx.cinterop.CFunction<(kotlinx.cinterop.COpaquePointer? /* = kotlinx.cinterop.CPointer<out kotlinx.cinterop.CPointed>? */) -> kotlin.Boolean>>? */,
                staticCFunction<COpaquePointer?, Boolean> { userdata -> true },
            )
        ) ?: error("Couldn't create scheduler")
        scheduler.set_scheduler(capi_scheduler)
        scheduler.freeze()
        return capi_scheduler
    }

    private fun <R> handleAppCallback(
        userData: COpaquePointer?,
        error: CPointer<realm_app_error_t>?,
        getValue: () -> R
    ) {
        val userDataCallback = safeUserData<AppCallback<R>>(userData)
        if (error == null) {
            userDataCallback.onSuccess(getValue())
        } else {
            val message = with(error.pointed) {
                "${message?.toKString()} [error_category=${error_category.value}, error_code=$error_code, link_to_server_logs=$link_to_server_logs]"
            }
            userDataCallback.onError(AppException(message))
        }
    }

    private val newRequestLambda = staticCFunction<COpaquePointer?,
        CValue<realm_http_request_t>,
        COpaquePointer?,
        Unit>
    { userdata, request, requestContext ->
        safeUserData<NetworkTransport>(userdata).let { networkTransport ->
            request.useContents { // this : realm_http_request_t ->
                val headerMap = mutableMapOf<String, String>()
                for (i in 0 until num_headers.toInt()) {
                    headers?.get(i)?.let { header ->
                        headerMap[header.name!!.toKString()] = header.value!!.toKString()
                    } ?: error("Header at index $i within range ${num_headers.toInt()} should not be null")
                }

                networkTransport.sendRequest(
                    method = when (method) {
                        realm_http_request_method.RLM_HTTP_REQUEST_METHOD_GET -> NetworkTransport.GET
                        realm_http_request_method.RLM_HTTP_REQUEST_METHOD_POST -> NetworkTransport.POST
                        realm_http_request_method.RLM_HTTP_REQUEST_METHOD_PATCH -> NetworkTransport.PATCH
                        realm_http_request_method.RLM_HTTP_REQUEST_METHOD_PUT -> NetworkTransport.PUT
                        realm_http_request_method.RLM_HTTP_REQUEST_METHOD_DELETE -> NetworkTransport.DELETE
                    },
                    url = url!!.toKString(),
                    headers = headerMap,
                    body = body!!.toKString()
                ) { response: Response ->
                    memScoped {
                        val headersSize = response.headers.entries.size
                        val cResponseHeaders =
                            allocArray<realm_http_header_t>(headersSize)

                        response.headers.entries.forEachIndexed { i, entry ->
                            cResponseHeaders[i].let { header ->
                                header.name = entry.key.cstr.getPointer(memScope)
                                header.value = entry.value.cstr.getPointer(memScope)
                            }
                        }

                        val cResponse =
                            alloc<realm_http_response_t> {
                                body = response.body.cstr.getPointer(memScope)
                                body_size = response.body.cstr.getBytes().size.toULong()
                                custom_status_code = response.customResponseCode
                                status_code = response.httpResponseCode
                                num_headers = response.headers.entries.size.toULong()
                                headers = cResponseHeaders
                            }
                        realm_wrapper.realm_http_transport_complete_request(
                            requestContext,
                            cResponse.ptr
                        )
                    }
                }
            }
        }
    }

    data class CoreCallback(
        val callback: realm_scheduler_notify_func_t,
        val callbackUserdata: CPointer<out CPointed>,
    )

    interface Scheduler {
        fun notify()
    }

    class SingleThreadDispatcherScheduler(
        val threadId: ULong,
        dispatcher: CoroutineDispatcher
    ) : Scheduler {
        val scope: CoroutineScope = CoroutineScope(dispatcher)
        val ref: CPointer<out CPointed>
        lateinit var scheduler: CPointer<realm_scheduler_t>

        init {
            ref = StableRef.create(this).asCPointer()
        }

        fun set_scheduler(scheduler: CPointer<realm_scheduler_t>) {
            this.scheduler = scheduler
        }

        override fun notify() {
            val function: suspend CoroutineScope.() -> Unit = {
                try {
                    printlntid("on dispatcher")
                    realm_wrapper.realm_scheduler_perform_work(scheduler)
                } catch (e: Exception) {
                    // Should never happen, but is included for development to get some indicators
                    // on errors instead of silent crashes.
                    e.printStackTrace()
                }
            }
            scope.launch(
                scope.coroutineContext,
                CoroutineStart.DEFAULT,
                function.freeze()
            )
        }
    }
}

private inline fun <reified T : Any> stableUserData(userdata: COpaquePointer?) =
    userdata?.asStableRef<T>()
        ?: error("User data (${T::class.simpleName}) should never be null")

private inline fun <reified T : Any> safeUserData(userdata: COpaquePointer?) =
    stableUserData<T>(userdata).get()

private inline fun <reified T : Any> disposeUserData(userdata: COpaquePointer?) {
    stableUserData<T>(userdata).dispose()
}

// Development debugging methods
// TODO Consider consolidating into platform abstract methods!?
// private inline fun printlntid(s: String) = printlnWithTid(s)
private inline fun printlntid(s: String) = Unit

private fun printlnWithTid(s: String) {
    // Don't try to optimize. Putting tid() call directly in formatted string causes crashes
    // (probably some compiler optimizations that causes references to be collected to early)
    val tid = tid()
    println("<" + tid.toString() + "> $s")
}

private fun tid(): ULong {
    memScoped {
        initRuntimeIfNeeded()
        val tidVar = alloc<ULongVar>()
        pthread_threadid_np(null, tidVar.ptr).ensureUnixCallResult("pthread_threadid_np")
        return tidVar.value
    }
}

private fun getUnixError() = strerror(posix_errno())!!.toKString()
private inline fun Int.ensureUnixCallResult(s: String): Int {
    if (this != 0) {
        throw Error("$s ${getUnixError()}")
    }
    return this
}
