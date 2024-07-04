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
// TODO https://github.com/realm/realm-kotlin/issues/889
@file:Suppress("TooGenericExceptionThrown", "TooGenericExceptionCaught")

package io.realm.kotlin.internal.interop

import io.realm.kotlin.internal.interop.Constants.ENCRYPTION_KEY_LENGTH
import io.realm.kotlin.internal.interop.sync.ApiKeyWrapper
import io.realm.kotlin.internal.interop.sync.AppError
import io.realm.kotlin.internal.interop.sync.AuthProvider
import io.realm.kotlin.internal.interop.sync.CancellableTimer
import io.realm.kotlin.internal.interop.sync.CoreCompensatingWriteInfo
import io.realm.kotlin.internal.interop.sync.CoreConnectionState
import io.realm.kotlin.internal.interop.sync.CoreSubscriptionSetState
import io.realm.kotlin.internal.interop.sync.CoreSyncSessionState
import io.realm.kotlin.internal.interop.sync.CoreUserState
import io.realm.kotlin.internal.interop.sync.MetadataMode
import io.realm.kotlin.internal.interop.sync.NetworkTransport
import io.realm.kotlin.internal.interop.sync.ProgressDirection
import io.realm.kotlin.internal.interop.sync.Response
import io.realm.kotlin.internal.interop.sync.SyncError
import io.realm.kotlin.internal.interop.sync.SyncSessionResyncMode
import io.realm.kotlin.internal.interop.sync.SyncUserIdentity
import io.realm.kotlin.internal.interop.sync.WebSocketClient
import io.realm.kotlin.internal.interop.sync.WebSocketObserver
import io.realm.kotlin.internal.interop.sync.WebSocketTransport
import io.realm.kotlin.internal.interop.sync.WebsocketCallbackResult
import io.realm.kotlin.internal.interop.sync.WebsocketErrorCode
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.AutofreeScope
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ByteVarOf
import kotlinx.cinterop.CArrayPointer
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CPointerVarOf
import kotlinx.cinterop.CValue
import kotlinx.cinterop.CVariable
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.ULongVarOf
import kotlinx.cinterop.addressOf
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
import kotlinx.cinterop.readValue
import kotlinx.cinterop.refTo
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCStringArray
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKString
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.ObjectId
import platform.posix.memcpy
import platform.posix.posix_errno
import platform.posix.pthread_threadid_np
import platform.posix.size_t
import platform.posix.size_tVar
import platform.posix.strerror
import platform.posix.uint64_t
import platform.posix.uint8_tVar
import realm_wrapper.realm_app_error_t
import realm_wrapper.realm_binary_t
import realm_wrapper.realm_class_info_t
import realm_wrapper.realm_class_key_tVar
import realm_wrapper.realm_clear_last_error
import realm_wrapper.realm_clone
import realm_wrapper.realm_dictionary_t
import realm_wrapper.realm_error_t
import realm_wrapper.realm_find_property
import realm_wrapper.realm_flx_sync_subscription_set_state_e
import realm_wrapper.realm_get_last_error
import realm_wrapper.realm_http_header_t
import realm_wrapper.realm_http_request_method
import realm_wrapper.realm_http_request_t
import realm_wrapper.realm_http_response_t
import realm_wrapper.realm_link_t
import realm_wrapper.realm_list_t
import realm_wrapper.realm_object_id_t
import realm_wrapper.realm_object_t
import realm_wrapper.realm_property_info_t
import realm_wrapper.realm_query_arg_t
import realm_wrapper.realm_release
import realm_wrapper.realm_results_t
import realm_wrapper.realm_scheduler_t
import realm_wrapper.realm_set_t
import realm_wrapper.realm_string_t
import realm_wrapper.realm_sync_session_state_e
import realm_wrapper.realm_sync_session_stop_policy_e
import realm_wrapper.realm_sync_socket_post_callback_t
import realm_wrapper.realm_sync_socket_t
import realm_wrapper.realm_sync_socket_timer_callback_t
import realm_wrapper.realm_sync_socket_timer_t
import realm_wrapper.realm_sync_socket_websocket_t
import realm_wrapper.realm_sync_socket_write_callback_t
import realm_wrapper.realm_t
import realm_wrapper.realm_user_t
import realm_wrapper.realm_value_t
import realm_wrapper.realm_value_type
import realm_wrapper.realm_version_id_t
import realm_wrapper.realm_work_queue_t
import kotlin.collections.set
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

actual val INVALID_CLASS_KEY: ClassKey by lazy { ClassKey(realm_wrapper.RLM_INVALID_CLASS_KEY.toLong()) }

actual val INVALID_PROPERTY_KEY: PropertyKey by lazy { PropertyKey(realm_wrapper.RLM_INVALID_PROPERTY_KEY) }

private fun throwOnError() {
    memScoped {
        val error = alloc<realm_error_t>()
        if (realm_get_last_error(error.ptr)) {

            throw CoreErrorConverter.asThrowable(
                categoriesNativeValue = error.categories.toInt(),
                errorCodeNativeValue = error.error.value.toInt(),
                messageNativeValue = error.message?.toKString(),
                path = error.path?.toKString(),
                userError = error.user_code_error?.asStableRef<Throwable>()?.get()
            ).also {
                error.user_code_error?.let { disposeUserData<Throwable>(it) }
                realm_clear_last_error()
            }
        }
    }
}

private fun checkedBooleanResult(boolean: Boolean): Boolean {
    if (!boolean) throwOnError(); return boolean
}

private fun <T : CPointed> checkedPointerResult(pointer: CPointer<T>?): CPointer<T>? {
    if (pointer == null) throwOnError(); return pointer
}

/**
 * Class with a pointer reference and its status. It breaks the reference cycle between CPointerWrapper
 * and its GC cleaner, otherwise the cleaner would never be invoked.
 *
 * See leaking cleaner: https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.native.ref/create-cleaner.html
 */
data class ReleasablePointer(
    private val _ptr: CPointer<out CPointed>?,
    val released: AtomicBoolean = atomic(false)
) {
    fun release() {
        if (released.compareAndSet(expect = false, update = true)) {
            realm_release(_ptr)
        }
    }

    val ptr: CPointer<out CPointed>?
        get() {
            return if (!released.value) {
                _ptr
            } else {
                throw POINTER_DELETED_ERROR
            }
        }
}

// FIXME API-INTERNAL Consider making NativePointer/CPointerWrapper generic to enforce typing
class CPointerWrapper<T : CapiT>(ptr: CPointer<out CPointed>?, managed: Boolean = true) : NativePointer<T> {
    val _ptr = ReleasablePointer(
        checkedPointerResult(ptr)
    )

    val ptr: CPointer<out CPointed>? = _ptr.ptr

    @OptIn(ExperimentalNativeApi::class)
    val cleaner = if (managed) {
        createCleaner(_ptr) {
            it.release()
        }
    } else null

    override fun release() {
        _ptr.release()
    }

    override fun isReleased(): Boolean = _ptr.released.value
}

// Convenience type cast
@Suppress("NOTHING_TO_INLINE")
inline fun <S : CapiT, T : CPointed> NativePointer<out S>.cptr(): CPointer<T> {
    @Suppress("UNCHECKED_CAST")
    return (this as CPointerWrapper<out S>).ptr as CPointer<T>
}

fun realm_binary_t.set(memScope: AutofreeScope, binary: ByteArray): realm_binary_t {
    size = binary.size.toULong()
    data = memScope.allocArray(binary.size)
    binary.forEachIndexed { index, byte ->
        data!![index] = byte.toUByte()
    }
    return this
}

fun realm_string_t.set(memScope: AutofreeScope, s: String): realm_string_t {
    val cstr = s.cstr
    data = cstr.getPointer(memScope)
    size = cstr.getBytes().size.toULong() - 1UL // realm_string_t is not zero-terminated
    return this
}

/**
 * Note that `realm_string_t` is allowed to represent `null`, so only use this extension
 * method if there is an invariant guaranteeing it won't be.
 *
 * @throws NullPointerException if `realm_string_t` is null.
 */
fun realm_string_t.toKotlinString(): String {
    if (size == 0UL) {
        return ""
    }
    val data: CPointer<ByteVarOf<Byte>>? = this.data
    val readBytes: ByteArray? = data?.readBytes(this.size.toInt())
    return readBytes?.decodeToString(0, size.toInt(), throwOnInvalidSequence = false)!!
}

fun realm_string_t.toNullableKotlinString(): String? {
    return if (data == null) {
        null
    } else {
        return toKotlinString()
    }
}

fun String.toRString(memScope: MemScope) = cValue<realm_string_t> {
    set(memScope, this@toRString)
}

@Suppress("LargeClass", "FunctionNaming")
actual object RealmInterop {

    private inline fun <reified T : Any> stableUserDataWithErrorPropagation(
        userdata: CPointer<out CPointed>?,
        block: T.() -> Boolean
    ): Boolean = try {
        block(stableUserData<T>(userdata).get())
    } catch (e: Throwable) {
        // register the error so it is accessible later
        realm_wrapper.realm_register_user_code_callback_error(StableRef.create(e).asCPointer())
        false // indicates the callback failed
    }

    actual fun realm_value_get(value: RealmValue): Any? = value.value

    actual fun realm_get_version_id(realm: RealmPointer): Long {
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

    actual fun realm_get_num_versions(realm: RealmPointer): Long {
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

    actual fun realm_refresh(realm: RealmPointer) {
        memScoped {
            // Only returns `true` if the version changed, `false` if the version
            // was already at the latest. Errors will be represented by the actual
            // return value, so just ignore this out parameter.
            val didRefresh = alloc<BooleanVar>()
            checkedBooleanResult(realm_wrapper.realm_refresh(realm.cptr(), didRefresh.ptr))
        }
    }

    actual fun realm_get_library_version(): String {
        return realm_wrapper.realm_get_library_version().safeKString("library_version")
    }

    actual fun realm_schema_new(schema: List<Pair<ClassInfo, List<PropertyInfo>>>): RealmSchemaPointer {
        val count = schema.size

        memScoped {
            val cclasses = allocArray<realm_class_info_t>(count)
            val cproperties = allocArray<CPointerVar<realm_property_info_t>>(count)
            for ((i, entry) in schema.withIndex()) {
                val (clazz, properties) = entry

                val computedCount = properties.count { it.isComputed }

                // Class
                cclasses[i].apply {
                    name = clazz.name.cstr.ptr
                    primary_key = clazz.primaryKey.cstr.ptr
                    num_properties = (properties.size - computedCount).toULong()
                    num_computed_properties = computedCount.toULong()
                    flags = clazz.flags
                }
                cproperties[i] =
                    allocArray<realm_property_info_t>(properties.size).getPointer(memScope)
                for ((j, property) in properties.withIndex()) {
                    cproperties[i]!![j].apply {
                        name = property.name.cstr.ptr
                        public_name = property.publicName.cstr.ptr
                        link_target = property.linkTarget.cstr.ptr
                        link_origin_property_name = property.linkOriginPropertyName.cstr.ptr
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

    actual fun realm_config_new(): RealmConfigurationPointer {
        return CPointerWrapper(realm_wrapper.realm_config_new())
    }

    actual fun realm_config_set_path(config: RealmConfigurationPointer, path: String) {
        realm_wrapper.realm_config_set_path(config.cptr(), path)
    }

    actual fun realm_config_set_schema_mode(config: RealmConfigurationPointer, mode: SchemaMode) {
        realm_wrapper.realm_config_set_schema_mode(
            config.cptr(),
            mode.nativeValue
        )
    }

    actual fun realm_config_set_schema_version(config: RealmConfigurationPointer, version: Long) {
        realm_wrapper.realm_config_set_schema_version(
            config.cptr(),
            version.toULong()
        )
    }

    actual fun realm_config_set_max_number_of_active_versions(
        config: RealmConfigurationPointer,
        maxNumberOfVersions: Long
    ) {
        realm_wrapper.realm_config_set_max_number_of_active_versions(
            config.cptr(),
            maxNumberOfVersions.toULong()
        )
    }

    actual fun realm_config_set_encryption_key(config: RealmConfigurationPointer, encryptionKey: ByteArray) {
        memScoped {
            val encryptionKeyPointer = encryptionKey.refTo(0).getPointer(memScope)
            @Suppress("UNCHECKED_CAST")
            realm_wrapper.realm_config_set_encryption_key(
                config.cptr(),
                encryptionKeyPointer as CPointer<uint8_tVar>,
                encryptionKey.size.toULong()
            )
        }
    }

    actual fun realm_config_get_encryption_key(config: RealmConfigurationPointer): ByteArray? {
        memScoped {
            val encryptionKey = ByteArray(ENCRYPTION_KEY_LENGTH)
            val encryptionKeyPointer = encryptionKey.refTo(0).getPointer(memScope)

            @Suppress("UNCHECKED_CAST") val keyLength = realm_wrapper.realm_config_get_encryption_key(
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
        config: RealmConfigurationPointer,
        callback: CompactOnLaunchCallback
    ) {
        realm_wrapper.realm_config_set_should_compact_on_launch_function(
            config.cptr(),
            staticCFunction<COpaquePointer?, uint64_t, uint64_t, Boolean> { userdata, total, used ->
                stableUserDataWithErrorPropagation<CompactOnLaunchCallback>(userdata) {
                    invoke(
                        total.toLong(),
                        used.toLong()
                    )
                }
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<CompactOnLaunchCallback>(userdata)
            }
        )
    }

    actual fun realm_config_set_automatic_backlink_handling(
        config: RealmConfigurationPointer,
        enabled: Boolean
    ) {
        realm_wrapper.realm_config_set_automatic_backlink_handling(
            config.cptr(),
            enabled,
        )
    }
    actual fun realm_config_set_migration_function(
        config: RealmConfigurationPointer,
        callback: MigrationCallback
    ) {
        realm_wrapper.realm_config_set_migration_function(
            config.cptr(),
            staticCFunction { userData, oldRealm, newRealm, schema ->
                stableUserDataWithErrorPropagation<MigrationCallback>(userData) {
                    migrate(
                        // These realm/schema pointers are only valid for the duraction of the
                        // migration so don't let ownership follow the NativePointer-objects
                        CPointerWrapper(oldRealm, false),
                        CPointerWrapper(newRealm, false),
                        CPointerWrapper(schema, false),
                    )
                    true
                }
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<MigrationCallback>(userdata)
            }
        )
    }

    actual fun realm_config_set_data_initialization_function(
        config: RealmConfigurationPointer,
        callback: DataInitializationCallback
    ) {
        realm_wrapper.realm_config_set_data_initialization_function(
            config.cptr(),
            staticCFunction { userData, _ ->
                stableUserDataWithErrorPropagation<DataInitializationCallback>(userData) {
                    invoke()
                    true
                }
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<DataInitializationCallback>(userdata)
            }
        )
    }

    actual fun realm_config_set_in_memory(config: RealmConfigurationPointer, inMemory: Boolean) {
        realm_wrapper.realm_config_set_in_memory(config.cptr(), inMemory)
    }

    actual fun realm_config_set_schema(config: RealmConfigurationPointer, schema: RealmSchemaPointer) {
        realm_wrapper.realm_config_set_schema(config.cptr(), schema.cptr())
    }

    actual fun realm_schema_validate(schema: RealmSchemaPointer, mode: SchemaValidationMode): Boolean {
        return checkedBooleanResult(
            realm_wrapper.realm_schema_validate(
                schema.cptr(),
                mode.nativeValue.toULong()
            )
        )
    }

    actual fun realm_open(config: RealmConfigurationPointer, scheduler: RealmSchedulerPointer): Pair<LiveRealmPointer, Boolean> {
        val fileCreated = atomic(false)
        val callback = DataInitializationCallback {
            fileCreated.value = true
        }
        realm_wrapper.realm_config_set_data_initialization_function(
            config.cptr(),
            staticCFunction { userdata, _ ->
                stableUserData<DataInitializationCallback>(userdata).get().invoke()
                true
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<DataInitializationCallback>(userdata)
            }
        )

        // TODO Consider just grabbing the current dispatcher by
        //      val dispatcher = runBlocking { coroutineContext[CoroutineDispatcher.Key] }
        //  but requires opting in for @ExperimentalStdlibApi, and have really gotten it to play
        //  for default cases.
        realm_wrapper.realm_config_set_scheduler(config.cptr(), scheduler.cptr())

        val realmPtr = CPointerWrapper<LiveRealmT>(realm_wrapper.realm_open(config.cptr()))
        // Ensure that we can read version information, etc.
        realm_begin_read(realmPtr)
        return Pair(realmPtr, fileCreated.value)
    }

    actual fun realm_create_scheduler(): RealmSchedulerPointer {
        // If there is no notification dispatcher use the default scheduler.
        // Re-verify if this is actually needed when notification scheduler is fully in place.
        val scheduler = checkedPointerResult(realm_wrapper.realm_scheduler_make_default())
        return CPointerWrapper<RealmSchedulerT>(scheduler)
    }

    actual fun realm_create_scheduler(dispatcher: CoroutineDispatcher): RealmSchedulerPointer {
        printlntid("createSingleThreadDispatcherScheduler")
        val scheduler = SingleThreadDispatcherScheduler(tid(), dispatcher)

        val capi_scheduler: CPointer<realm_scheduler_t> = checkedPointerResult(
            realm_wrapper.realm_scheduler_new(
                // userdata: kotlinx.cinterop.CValuesRef<*>?,
                scheduler.ref,

                // free: realm_wrapper.realm_free_userdata_func_t? /* = kotlinx.cinterop.CPointer<kotlinx.cinterop.CFunction<(kotlinx.cinterop.COpaquePointer? /* = kotlinx.cinterop.CPointer<out kotlinx.cinterop.CPointed>? */) -> kotlin.Unit>>? */,
                staticCFunction<COpaquePointer?, Unit> { userdata ->
                    printlntid("free")
                    val stableSchedulerRef: StableRef<SingleThreadDispatcherScheduler>? = userdata?.asStableRef<SingleThreadDispatcherScheduler>()
                    stableSchedulerRef?.get()?.cancel()
                    stableSchedulerRef?.dispose()
                },

                // notify: realm_wrapper.realm_scheduler_notify_func_t? /* = kotlinx.cinterop.CPointer<kotlinx.cinterop.CFunction<(kotlinx.cinterop.COpaquePointer? /* = kotlinx.cinterop.CPointer<out kotlinx.cinterop.CPointed>? */) -> kotlin.Unit>>? */,
                staticCFunction<COpaquePointer?, CPointer<realm_work_queue_t>?, Unit> { userdata, work_queue ->
                    // Must be thread safe
                    val scheduler =
                        userdata!!.asStableRef<SingleThreadDispatcherScheduler>().get()
                    printlntid("$scheduler notify")
                    try {
                        scheduler.notify(work_queue)
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
                staticCFunction<COpaquePointer?, Boolean> { _ -> true },
            )
        ) ?: error("Couldn't create scheduler")

        scheduler.setScheduler(capi_scheduler)

        return CPointerWrapper<RealmSchedulerT>(capi_scheduler)
    }

    actual fun realm_open_synchronized(config: RealmConfigurationPointer): RealmAsyncOpenTaskPointer {
        return CPointerWrapper(realm_wrapper.realm_open_synchronized(config.cptr()))
    }

    actual fun realm_async_open_task_start(task: RealmAsyncOpenTaskPointer, callback: AsyncOpenCallback) {
        realm_wrapper.realm_async_open_task_start(
            task.cptr(),
            staticCFunction { userData, realm, error ->
                memScoped {
                    var exception: Throwable? = null
                    if (error != null) {
                        val err = alloc<realm_error_t>()
                        realm_wrapper.realm_get_async_error(error, err.ptr)
                        exception = CoreErrorConverter.asThrowable(
                            categoriesNativeValue = err.categories.toInt(),
                            errorCodeNativeValue = err.error.value.toInt(),
                            messageNativeValue = err.message?.toKString(),
                            path = err.path?.toKString(),
                            userError = err.user_code_error?.asStableRef<Throwable>()?.get()
                        )
                        err.user_code_error?.let { disposeUserData<Throwable>(it) }
                    } else {
                        realm_release(realm)
                    }
                    safeUserData<AsyncOpenCallback>(userData).invoke(exception)
                }
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userData ->
                disposeUserData<AsyncOpenCallback>(userData)
            }
        )
    }

    actual fun realm_async_open_task_cancel(task: RealmAsyncOpenTaskPointer) {
        realm_wrapper.realm_async_open_task_cancel(task.cptr())
    }

    actual fun realm_add_realm_changed_callback(realm: LiveRealmPointer, block: () -> Unit): RealmCallbackTokenPointer {
        return CPointerWrapper(
            realm_wrapper.realm_add_realm_changed_callback(
                realm.cptr(),
                staticCFunction { userData ->
                    safeUserData<() -> Unit>(userData)()
                },
                StableRef.create(block).asCPointer(),
                staticCFunction { userdata ->
                    disposeUserData<(LiveRealmPointer, SyncErrorCallback) -> Unit>(userdata)
                }
            ),
            managed = false
        )
    }

    actual fun realm_add_schema_changed_callback(realm: LiveRealmPointer, block: (RealmSchemaPointer) -> Unit): RealmCallbackTokenPointer {
        return CPointerWrapper(
            realm_wrapper.realm_add_schema_changed_callback(
                realm.cptr(),
                staticCFunction { userData, schema ->
                    safeUserData<(RealmSchemaPointer) -> Unit>(userData)(CPointerWrapper(realm_clone(schema)))
                },
                StableRef.create(block).asCPointer(),
                staticCFunction { userdata ->
                    disposeUserData<(RealmSchemaT, SyncErrorCallback) -> Unit>(userdata)
                }
            ),
            managed = false
        )
    }

    actual fun realm_freeze(liveRealm: LiveRealmPointer): FrozenRealmPointer {
        return CPointerWrapper(realm_wrapper.realm_freeze(liveRealm.cptr<LiveRealmT, realm_t>()))
    }

    actual fun realm_is_frozen(realm: RealmPointer): Boolean {
        return realm_wrapper.realm_is_frozen(realm.cptr<RealmT, realm_t>())
    }

    actual fun realm_close(realm: RealmPointer) {
        checkedBooleanResult(realm_wrapper.realm_close(realm.cptr()))
    }

    actual fun realm_delete_files(path: String) {
        memScoped {
            val deleted = alloc<BooleanVar>()
            checkedBooleanResult(realm_wrapper.realm_delete_files(path, deleted.ptr))
            if (!deleted.value) {
                throw IllegalStateException("It's not allowed to delete the file associated with an open Realm. Remember to call 'close()' on the instances of the realm before deleting its file: $path")
            }
        }
    }

    actual fun realm_compact(realm: RealmPointer): Boolean {
        memScoped {
            val compacted = alloc<BooleanVar>()
            checkedBooleanResult(realm_wrapper.realm_compact(realm.cptr(), compacted.ptr))
            return compacted.value
        }
    }

    actual fun realm_convert_with_config(
        realm: RealmPointer,
        config: RealmConfigurationPointer,
        mergeWithExisting: Boolean
    ) {
        memScoped {
            checkedBooleanResult(
                realm_wrapper.realm_convert_with_config(
                    realm.cptr(),
                    config.cptr(),
                    mergeWithExisting
                )
            )
        }
    }

    actual fun realm_get_schema(realm: RealmPointer): RealmSchemaPointer {
        return CPointerWrapper(realm_wrapper.realm_get_schema(realm.cptr()))
    }

    actual fun realm_get_schema_version(realm: RealmPointer): Long {
        return realm_wrapper.realm_get_schema_version(realm.cptr()).toLong()
    }

    actual fun realm_get_num_classes(realm: RealmPointer): Long {
        return realm_wrapper.realm_get_num_classes(realm.cptr()).toLong()
    }

    actual fun realm_get_class_keys(realm: RealmPointer): List<ClassKey> {
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

    actual fun realm_find_class(realm: RealmPointer, name: String): ClassKey? {
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

    actual fun realm_get_class(realm: RealmPointer, classKey: ClassKey): ClassInfo {
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
        realm: RealmPointer,
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

    internal actual fun realm_release(p: RealmNativePointer) {
        realm_wrapper.realm_release((p as CPointerWrapper).ptr)
    }

    actual fun realm_equals(p1: RealmNativePointer, p2: RealmNativePointer): Boolean {
        return realm_wrapper.realm_equals((p1 as CPointerWrapper).ptr, (p2 as CPointerWrapper).ptr)
    }

    actual fun realm_is_closed(realm: RealmPointer): Boolean {
        return realm_wrapper.realm_is_closed(realm.cptr())
    }

    actual fun realm_begin_read(realm: RealmPointer) {
        checkedBooleanResult(realm_wrapper.realm_begin_read(realm.cptr()))
    }

    actual fun realm_begin_write(realm: LiveRealmPointer) {
        checkedBooleanResult(realm_wrapper.realm_begin_write(realm.cptr()))
    }

    actual fun realm_commit(realm: LiveRealmPointer) {
        checkedBooleanResult(realm_wrapper.realm_commit(realm.cptr()))
    }

    actual fun realm_rollback(realm: LiveRealmPointer) {
        checkedBooleanResult(realm_wrapper.realm_rollback(realm.cptr()))
    }

    actual fun realm_is_in_transaction(realm: RealmPointer): Boolean {
        return realm_wrapper.realm_is_writable(realm.cptr())
    }

    actual fun realm_update_schema(realm: LiveRealmPointer, schema: RealmSchemaPointer) {
        checkedBooleanResult(realm_wrapper.realm_update_schema(realm.cptr(), schema.cptr()))
    }

    actual fun realm_object_create(realm: LiveRealmPointer, classKey: ClassKey): RealmObjectPointer {
        return CPointerWrapper(
            realm_wrapper.realm_object_create(
                realm.cptr(),
                classKey.key.toUInt()
            )
        )
    }

    actual fun realm_object_create_with_primary_key(
        realm: LiveRealmPointer,
        classKey: ClassKey,
        primaryKeyTransport: RealmValue
    ): RealmObjectPointer {
        return CPointerWrapper(
            realm_wrapper.realm_object_create_with_primary_key(
                realm.cptr(),
                classKey.key.toUInt(),
                primaryKeyTransport.value.readValue()
            )
        )
    }

    actual fun realm_object_get_or_create_with_primary_key(
        realm: LiveRealmPointer,
        classKey: ClassKey,
        primaryKeyTransport: RealmValue
    ): RealmObjectPointer {
        memScoped {
            val found = alloc<BooleanVar>()
            return CPointerWrapper(
                realm_wrapper.realm_object_get_or_create_with_primary_key(
                    realm.cptr(),
                    classKey.key.toUInt(),
                    primaryKeyTransport.value.readValue(),
                    found.ptr
                )
            )
        }
    }

    actual fun realm_object_is_valid(obj: RealmObjectPointer): Boolean {
        return realm_wrapper.realm_object_is_valid(obj.cptr())
    }

    actual fun realm_object_get_key(obj: RealmObjectPointer): ObjectKey {
        return ObjectKey(realm_wrapper.realm_object_get_key(obj.cptr()))
    }

    actual fun realm_object_resolve_in(obj: RealmObjectPointer, realm: RealmPointer): RealmObjectPointer? {
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

    actual fun realm_object_as_link(obj: RealmObjectPointer): Link {
        val link: CValue<realm_link_t> = realm_wrapper.realm_object_as_link(obj.cptr())
        link.useContents {
            return Link(ClassKey(this.target_table.toLong()), this.target)
        }
    }

    actual fun realm_object_get_table(obj: RealmObjectPointer): ClassKey {
        return ClassKey(realm_wrapper.realm_object_get_table(obj.cptr()).toLong())
    }

    actual fun realm_get_col_key(realm: RealmPointer, classKey: ClassKey, col: String): PropertyKey {
        memScoped {
            return PropertyKey(propertyInfo(realm, classKey, col).key)
        }
    }

    actual fun MemAllocator.realm_get_value(
        obj: RealmObjectPointer,
        key: PropertyKey
    ): RealmValue {
        val struct = allocRealmValueT()
        checkedBooleanResult(realm_wrapper.realm_get_value(obj.cptr(), key.key, struct.ptr))
        return RealmValue(struct)
    }

    actual fun realm_set_value(
        obj: RealmObjectPointer,
        key: PropertyKey,
        value: RealmValue,
        isDefault: Boolean
    ) {
        checkedBooleanResult(
            realm_wrapper.realm_set_value(
                obj.cptr(),
                key.key,
                value.value.readValue(),
                isDefault
            )
        )
    }

    actual fun realm_set_embedded(obj: RealmObjectPointer, key: PropertyKey): RealmObjectPointer {
        return CPointerWrapper(realm_wrapper.realm_set_embedded(obj.cptr(), key.key))
    }

    actual fun realm_set_list(obj: RealmObjectPointer, key: PropertyKey): RealmListPointer {
        return CPointerWrapper(realm_wrapper.realm_set_list(obj.cptr(), key.key))
    }
    actual fun realm_set_dictionary(obj: RealmObjectPointer, key: PropertyKey): RealmMapPointer {
        return CPointerWrapper(realm_wrapper.realm_set_dictionary(obj.cptr(), key.key))
    }

    actual fun realm_object_add_int(obj: RealmObjectPointer, key: PropertyKey, value: Long) {
        checkedBooleanResult(realm_wrapper.realm_object_add_int(obj.cptr(), key.key, value))
    }

    actual fun <T> realm_object_get_parent(
        obj: RealmObjectPointer,
        block: (ClassKey, RealmObjectPointer) -> T
    ): T {
        memScoped {
            val objectPointerArray = allocArray<CPointerVar<realm_object_t>>(1)
            val classKeyPointerArray = allocArray<realm_class_key_tVar>(1)

            checkedBooleanResult(
                realm_wrapper.realm_object_get_parent(
                    `object` = obj.cptr(),
                    parent = objectPointerArray,
                    class_key = classKeyPointerArray
                )
            )

            val classKey = ClassKey(classKeyPointerArray[0].toLong())
            val objectPointer = CPointerWrapper<RealmObjectT>(objectPointerArray[0])

            return block(classKey, objectPointer)
        }
    }

    actual fun realm_get_list(obj: RealmObjectPointer, key: PropertyKey): RealmListPointer {
        return CPointerWrapper(realm_wrapper.realm_get_list(obj.cptr(), key.key))
    }

    actual fun realm_get_backlinks(obj: RealmObjectPointer, sourceClassKey: ClassKey, sourcePropertyKey: PropertyKey): RealmResultsPointer {
        return CPointerWrapper(realm_wrapper.realm_get_backlinks(obj.cptr(), sourceClassKey.key.toUInt(), sourcePropertyKey.key))
    }

    actual fun realm_list_size(list: RealmListPointer): Long {
        memScoped {
            val size = alloc<ULongVar>()
            checkedBooleanResult(realm_wrapper.realm_list_size(list.cptr(), size.ptr))
            return size.value.toLong()
        }
    }

    actual fun MemAllocator.realm_list_get(
        list: RealmListPointer,
        index: Long
    ): RealmValue {
        val struct = allocRealmValueT()
        checkedBooleanResult(realm_wrapper.realm_list_get(list.cptr(), index.toULong(), struct.ptr))
        return RealmValue(struct)
    }

    actual fun realm_list_find(list: RealmListPointer, value: RealmValue): Long {
        memScoped {
            val index = alloc<ULongVar>()
            val found = alloc<BooleanVar>()
            checkedBooleanResult(realm_wrapper.realm_list_find(list.cptr(), value.value.readValue(), index.ptr, found.ptr))
            return if (found.value) {
                index.value.toLong()
            } else {
                INDEX_NOT_FOUND
            }
        }
    }

    actual fun realm_list_get_list(list: RealmListPointer, index: Long): RealmListPointer =
        CPointerWrapper(realm_wrapper.realm_list_get_list(list.cptr(), index.toULong()))

    actual fun realm_list_get_dictionary(list: RealmListPointer, index: Long): RealmMapPointer =
        CPointerWrapper(realm_wrapper.realm_list_get_dictionary(list.cptr(), index.toULong()))

    actual fun realm_list_add(list: RealmListPointer, index: Long, transport: RealmValue) {
        checkedBooleanResult(
            realm_wrapper.realm_list_insert(
                list.cptr(),
                index.toULong(),
                transport.value.readValue()
            )
        )
    }
    actual fun realm_list_insert_list(list: RealmListPointer, index: Long): RealmListPointer {
        return CPointerWrapper(realm_wrapper.realm_list_insert_list(list.cptr(), index.toULong()))
    }
    actual fun realm_list_insert_dictionary(list: RealmListPointer, index: Long): RealmMapPointer {
        return CPointerWrapper(realm_wrapper.realm_list_insert_dictionary(list.cptr(), index.toULong()))
    }
    actual fun realm_list_set_list(list: RealmListPointer, index: Long): RealmListPointer {
        return CPointerWrapper(realm_wrapper.realm_list_set_list(list.cptr(), index.toULong()))
    }
    actual fun realm_list_set_dictionary(list: RealmListPointer, index: Long): RealmMapPointer {
        return CPointerWrapper(realm_wrapper.realm_list_set_dictionary(list.cptr(), index.toULong()))
    }

    actual fun realm_list_insert_embedded(list: RealmListPointer, index: Long): RealmObjectPointer {
        return CPointerWrapper(realm_wrapper.realm_list_insert_embedded(list.cptr(), index.toULong()))
    }

    actual fun realm_list_set(
        list: RealmListPointer,
        index: Long,
        inputTransport: RealmValue
    ) {
        checkedBooleanResult(
            realm_wrapper.realm_list_set(
                list.cptr(),
                index.toULong(),
                inputTransport.value.readValue()
            )
        )
    }

    actual fun MemAllocator.realm_list_set_embedded(
        list: RealmListPointer,
        index: Long
    ): RealmValue {
        val struct = allocRealmValueT()

        // Returns the new object as a Link to follow convention of other getters and allow to
        // reuse the converter infrastructure
        val embedded = realm_wrapper.realm_list_set_embedded(list.cptr(), index.toULong())
        val outputStruct = realm_wrapper.realm_object_as_link(embedded).useContents {
            struct.type = realm_value_type.RLM_TYPE_LINK
            struct.link.apply {
                this.target_table = this@useContents.target_table
                this.target = this@useContents.target
            }
            struct
        }
        return RealmValue(outputStruct)
    }

    actual fun realm_list_clear(list: RealmListPointer) {
        checkedBooleanResult(realm_wrapper.realm_list_clear(list.cptr()))
    }

    actual fun realm_list_remove_all(list: RealmListPointer) {
        checkedBooleanResult(realm_wrapper.realm_list_remove_all(list.cptr()))
    }

    actual fun realm_list_erase(list: RealmListPointer, index: Long) {
        checkedBooleanResult(realm_wrapper.realm_list_erase(list.cptr(), index.toULong()))
    }

    actual fun realm_list_resolve_in(list: RealmListPointer, realm: RealmPointer): RealmListPointer? {
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

    actual fun realm_list_is_valid(list: RealmListPointer): Boolean {
        return realm_wrapper.realm_list_is_valid(list.cptr())
    }

    actual fun realm_get_set(obj: RealmObjectPointer, key: PropertyKey): RealmSetPointer {
        return CPointerWrapper(realm_wrapper.realm_get_set(obj.cptr(), key.key))
    }

    actual fun realm_set_size(set: RealmSetPointer): Long {
        memScoped {
            val size = alloc<ULongVar>()
            checkedBooleanResult(realm_wrapper.realm_set_size(set.cptr(), size.ptr))
            return size.value.toLong()
        }
    }

    actual fun realm_set_clear(set: RealmSetPointer) {
        checkedBooleanResult(realm_wrapper.realm_set_clear(set.cptr()))
    }

    actual fun realm_set_insert(set: RealmSetPointer, transport: RealmValue): Boolean {
        memScoped {
            val inserted = alloc<BooleanVar>()
            checkedBooleanResult(
                realm_wrapper.realm_set_insert(
                    set.cptr(),
                    transport.value.readValue(),
                    null,
                    inserted.ptr
                )
            )
            return inserted.value
        }
    }

    // Returning a non-nullable transport here goes against the approach that increases
    // performance (since we need to call getType on the transport object). This is needed though
    // because this function is called when calling 'iterator.remove' and causes issues when telling
    // the C-API to delete a null transport created within the scope. We need to investigate further
    // how to improve this.
    actual fun MemAllocator.realm_set_get(set: RealmSetPointer, index: Long): RealmValue {
        val struct = allocRealmValueT()
        checkedBooleanResult(realm_wrapper.realm_set_get(set.cptr(), index.toULong(), struct.ptr))
        return RealmValue(struct)
    }

    actual fun realm_set_find(set: RealmSetPointer, transport: RealmValue): Boolean {
        // TODO optimize: use MemAllocator
        memScoped {
            val index = alloc<ULongVar>()
            val found = alloc<BooleanVar>()
            checkedBooleanResult(
                realm_wrapper.realm_set_find(
                    set.cptr(),
                    transport.value.readValue(),
                    index.ptr,
                    found.ptr
                )
            )
            return found.value
        }
    }

    actual fun realm_set_erase(set: RealmSetPointer, transport: RealmValue): Boolean {
        // TODO optimize: use MemAllocator
        memScoped {
            val erased = alloc<BooleanVar>()
            checkedBooleanResult(
                realm_wrapper.realm_set_erase(
                    set.cptr(),
                    transport.value.readValue(),
                    erased.ptr
                )
            )
            return erased.value
        }
    }

    actual fun realm_set_remove_all(set: RealmSetPointer) {
        checkedBooleanResult(realm_wrapper.realm_set_remove_all(set.cptr()))
    }

    actual fun realm_set_resolve_in(set: RealmSetPointer, realm: RealmPointer): RealmSetPointer? {
        memScoped {
            val setPointer = allocArray<CPointerVar<realm_set_t>>(1)
            checkedBooleanResult(
                realm_wrapper.realm_set_resolve_in(set.cptr(), realm.cptr(), setPointer)
            )
            return setPointer[0]?.let {
                CPointerWrapper(it)
            }
        }
    }

    actual fun realm_set_is_valid(set: RealmSetPointer): Boolean {
        return realm_wrapper.realm_set_is_valid(set.cptr())
    }

    actual fun realm_get_dictionary(
        obj: RealmObjectPointer,
        key: PropertyKey
    ): RealmMapPointer {
        val ptr = realm_wrapper.realm_get_dictionary(obj.cptr(), key.key)
        return CPointerWrapper(ptr)
    }

    actual fun realm_dictionary_clear(dictionary: RealmMapPointer) {
        checkedBooleanResult(
            realm_wrapper.realm_dictionary_clear(dictionary.cptr())
        )
    }

    actual fun realm_dictionary_size(dictionary: RealmMapPointer): Long {
        memScoped {
            val size = alloc<ULongVar>()
            checkedBooleanResult(realm_wrapper.realm_dictionary_size(dictionary.cptr(), size.ptr))
            return size.value.toLong()
        }
    }

    actual fun realm_dictionary_to_results(
        dictionary: RealmMapPointer
    ): RealmResultsPointer {
        return CPointerWrapper(realm_wrapper.realm_dictionary_to_results(dictionary.cptr()))
    }

    actual fun MemAllocator.realm_dictionary_find(
        dictionary: RealmMapPointer,
        mapKey: RealmValue
    ): RealmValue {
        val struct = allocRealmValueT()

        // TODO optimize: use MemAllocator
        memScoped {
            val found = alloc<BooleanVar>()
            checkedBooleanResult(
                realm_wrapper.realm_dictionary_find(
                    dictionary.cptr(),
                    mapKey.value.readValue(),
                    struct.ptr,
                    found.ptr
                )
            )

            // Core will always return a realm_value_t, even if the value was not found, in which case
            // the type of the struct will be RLM_TYPE_NULL. This way we signal our converters not to
            // worry about nullability and just translate the struct types to their corresponding Kotlin
            // types.
            return RealmValue(struct)
        }
    }

    actual fun realm_dictionary_find_list(
        dictionary: RealmMapPointer,
        mapKey: RealmValue
    ): RealmListPointer {
        return CPointerWrapper(realm_wrapper.realm_dictionary_get_list(dictionary.cptr(), mapKey.value.readValue()))
    }
    actual fun realm_dictionary_find_dictionary(
        dictionary: RealmMapPointer,
        mapKey: RealmValue
    ): RealmMapPointer {
        return CPointerWrapper(realm_wrapper.realm_dictionary_get_dictionary(dictionary.cptr(), mapKey.value.readValue()))
    }

    actual fun MemAllocator.realm_dictionary_get(
        dictionary: RealmMapPointer,
        pos: Int
    ): Pair<RealmValue, RealmValue> {
        val keyTransport = allocRealmValueT()
        val valueTransport = allocRealmValueT()
        checkedBooleanResult(
            realm_wrapper.realm_dictionary_get(
                dictionary.cptr(),
                pos.toULong(),
                keyTransport.ptr,
                valueTransport.ptr
            )
        )
        return Pair(RealmValue(keyTransport), RealmValue(valueTransport))
    }

    actual fun MemAllocator.realm_dictionary_insert(
        dictionary: RealmMapPointer,
        mapKey: RealmValue,
        value: RealmValue
    ): Pair<RealmValue, Boolean> {
        val previousValue = realm_dictionary_find(dictionary, mapKey)

        // TODO optimize: use MemAllocator
        memScoped {
            realm_dictionary_find(dictionary, mapKey)
            val index = alloc<ULongVar>()
            val inserted = alloc<BooleanVar>()
            checkedBooleanResult(
                realm_wrapper.realm_dictionary_insert(
                    dictionary.cptr(),
                    mapKey.value.readValue(),
                    value.value.readValue(),
                    index.ptr,
                    inserted.ptr
                )
            )
            return Pair(previousValue, inserted.value)
        }
    }

    actual fun MemAllocator.realm_dictionary_erase(
        dictionary: RealmMapPointer,
        mapKey: RealmValue
    ): Pair<RealmValue, Boolean> {
        val previousValue = realm_dictionary_find(dictionary, mapKey)

        // TODO optimize: use MemAllocator
        memScoped {
            val erased = alloc<BooleanVar>()
            checkedBooleanResult(
                realm_wrapper.realm_dictionary_erase(
                    dictionary.cptr(),
                    mapKey.value.readValue(),
                    erased.ptr
                )
            )
            return Pair(previousValue, erased.value)
        }
    }

    actual fun realm_dictionary_contains_key(
        dictionary: RealmMapPointer,
        mapKey: RealmValue
    ): Boolean {
        // TODO optimize: use MemAllocator
        memScoped {
            val found = alloc<BooleanVar>()
            checkedBooleanResult(
                realm_wrapper.realm_dictionary_contains_key(
                    dictionary.cptr(),
                    mapKey.value.readValue(),
                    found.ptr
                )
            )
            return found.value
        }
    }

    actual fun realm_dictionary_contains_value(
        dictionary: RealmMapPointer,
        value: RealmValue
    ): Boolean {
        // TODO optimize: use MemAllocator
        memScoped {
            val index = alloc<ULongVar>()
            checkedBooleanResult(
                realm_wrapper.realm_dictionary_contains_value(
                    dictionary.cptr(),
                    value.value.readValue(),
                    index.ptr
                )
            )
            return index.value.toLong() != -1L
        }
    }

    actual fun MemAllocator.realm_dictionary_insert_embedded(
        dictionary: RealmMapPointer,
        mapKey: RealmValue
    ): RealmValue {
        val struct = allocRealmValueT()

        // Returns the new object as a Link to follow convention of other getters and allow to
        // reuse the converter infrastructure
        val embedded = realm_wrapper.realm_dictionary_insert_embedded(
            dictionary.cptr(),
            mapKey.value.readValue()
        )
        val outputStruct = realm_wrapper.realm_object_as_link(embedded).useContents {
            struct.type = realm_value_type.RLM_TYPE_LINK
            struct.link.apply {
                this.target_table = this@useContents.target_table
                this.target = this@useContents.target
            }
            struct
        }
        return RealmValue(outputStruct)
    }

    actual fun realm_dictionary_insert_list(dictionary: RealmMapPointer, mapKey: RealmValue): RealmListPointer {
        return CPointerWrapper(realm_wrapper.realm_dictionary_insert_list(dictionary.cptr(), mapKey.value.readValue()))
    }

    actual fun realm_dictionary_insert_dictionary(dictionary: RealmMapPointer, mapKey: RealmValue): RealmMapPointer {
        return CPointerWrapper(realm_wrapper.realm_dictionary_insert_dictionary(dictionary.cptr(), mapKey.value.readValue()))
    }

    actual fun realm_dictionary_get_keys(dictionary: RealmMapPointer): RealmResultsPointer {
        memScoped {
            val size = alloc<ULongVar>()
            val keysPointer = allocArray<CPointerVar<realm_results_t>>(1)
            checkedBooleanResult(
                realm_wrapper.realm_dictionary_get_keys(dictionary.cptr(), size.ptr, keysPointer)
            )
            return keysPointer[0]?.let {
                CPointerWrapper(it)
            } ?: throw IllegalArgumentException("There was an error retrieving the dictionary keys.")
        }
    }

    actual fun realm_dictionary_resolve_in(
        dictionary: RealmMapPointer,
        realm: RealmPointer
    ): RealmMapPointer? {
        memScoped {
            val dictionaryPointer = allocArray<CPointerVar<realm_dictionary_t>>(1)
            checkedBooleanResult(
                realm_wrapper.realm_dictionary_resolve_in(
                    dictionary.cptr(),
                    realm.cptr(),
                    dictionaryPointer
                )
            )
            return dictionaryPointer[0]?.let {
                CPointerWrapper(it)
            }
        }
    }

    actual fun realm_dictionary_is_valid(dictionary: RealmMapPointer): Boolean {
        return realm_wrapper.realm_dictionary_is_valid(dictionary.cptr())
    }

    actual fun realm_query_parse(
        realm: RealmPointer,
        classKey: ClassKey,
        query: String,
        args: RealmQueryArgumentList
    ): RealmQueryPointer {
        return CPointerWrapper(
            realm_wrapper.realm_query_parse(
                realm.cptr(),
                classKey.key.toUInt(),
                query,
                args.size,
                args.head.ptr
            )
        )
    }

    actual fun realm_query_parse_for_results(
        results: RealmResultsPointer,
        query: String,
        args: RealmQueryArgumentList
    ): RealmQueryPointer {
        return CPointerWrapper(
            realm_wrapper.realm_query_parse_for_results(
                results.cptr(),
                query,
                args.size,
                args.head.ptr
            )
        )
    }

    actual fun realm_query_parse_for_list(
        list: RealmListPointer,
        query: String,
        args: RealmQueryArgumentList
    ): RealmQueryPointer {
        return CPointerWrapper(
            realm_wrapper.realm_query_parse_for_list(
                list.cptr(),
                query,
                args.size,
                args.head.ptr
            )
        )
    }

    actual fun realm_query_parse_for_set(
        set: RealmSetPointer,
        query: String,
        args: RealmQueryArgumentList
    ): RealmQueryPointer {
        return CPointerWrapper(
            realm_wrapper.realm_query_parse_for_set(
                set.cptr(),
                query,
                args.size,
                args.head.ptr
            )
        )
    }

    actual fun realm_query_find_first(query: RealmQueryPointer): Link? {
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

    actual fun realm_query_find_all(query: RealmQueryPointer): RealmResultsPointer {
        return CPointerWrapper(realm_wrapper.realm_query_find_all(query.cptr()))
    }

    actual fun realm_query_count(query: RealmQueryPointer): Long {
        memScoped {
            val count = alloc<ULongVar>()
            checkedBooleanResult(realm_wrapper.realm_query_count(query.cptr(), count.ptr))
            return count.value.toLong()
        }
    }

    actual fun realm_query_append_query(
        query: RealmQueryPointer,
        filter: String,
        args: RealmQueryArgumentList
    ): RealmQueryPointer {
        return CPointerWrapper(
            realm_wrapper.realm_query_append_query(
                query.cptr(),
                filter,
                args.size,
                args.head.ptr
            )
        )
    }

    actual fun realm_query_get_description(query: RealmQueryPointer): String {
        return realm_wrapper.realm_query_get_description(query.cptr()).safeKString()
    }

    actual fun realm_results_get_query(results: RealmResultsPointer): RealmQueryPointer {
        return CPointerWrapper(realm_wrapper.realm_results_get_query(results.cptr()))
    }

    actual fun realm_results_resolve_in(
        results: RealmResultsPointer,
        realm: RealmPointer
    ): RealmResultsPointer {
        return CPointerWrapper(
            realm_wrapper.realm_results_resolve_in(
                results.cptr(),
                realm.cptr()
            )
        )
    }

    actual fun realm_results_count(results: RealmResultsPointer): Long {
        memScoped {
            val count = alloc<ULongVar>()
            checkedBooleanResult(realm_wrapper.realm_results_count(results.cptr(), count.ptr))
            return count.value.toLong()
        }
    }

    actual fun MemAllocator.realm_results_average(
        results: RealmResultsPointer,
        propertyKey: PropertyKey
    ): Pair<Boolean, RealmValue> {
        val struct = allocRealmValueT()
        // TODO optimize: integrate allocation of other native types in MemAllocator too
        memScoped {
            val found = cValue<BooleanVar>().ptr
            checkedBooleanResult(
                realm_wrapper.realm_results_average(
                    results.cptr(),
                    propertyKey.key,
                    struct.ptr,
                    found
                )
            )
            return found.pointed.value to RealmValue(struct)
        }
    }

    actual fun MemAllocator.realm_results_sum(
        results: RealmResultsPointer,
        propertyKey: PropertyKey
    ): RealmValue {
        val struct = allocRealmValueT()
        checkedBooleanResult(
            realm_wrapper.realm_results_sum(
                results.cptr(),
                propertyKey.key,
                struct.ptr,
                null
            )
        )
        val transport = RealmValue(struct)
        return transport
    }

    actual fun MemAllocator.realm_results_max(
        results: RealmResultsPointer,
        propertyKey: PropertyKey
    ): RealmValue {
        val struct = allocRealmValueT()
        checkedBooleanResult(
            realm_wrapper.realm_results_max(
                results.cptr(),
                propertyKey.key,
                struct.ptr,
                null
            )
        )
        return RealmValue(struct)
    }

    actual fun MemAllocator.realm_results_min(
        results: RealmResultsPointer,
        propertyKey: PropertyKey
    ): RealmValue {
        val struct = allocRealmValueT()
        checkedBooleanResult(
            realm_wrapper.realm_results_min(
                results.cptr(),
                propertyKey.key,
                struct.ptr,
                null
            )
        )
        return RealmValue(struct)
    }

    actual fun MemAllocator.realm_results_get(results: RealmResultsPointer, index: Long): RealmValue {
        val value = allocRealmValueT()
        checkedBooleanResult(
            realm_wrapper.realm_results_get(
                results.cptr(),
                index.toULong(),
                value.ptr
            )
        )
        return RealmValue(value)
    }

    actual fun realm_results_get_list(results: RealmResultsPointer, index: Long): RealmListPointer =
        CPointerWrapper(realm_wrapper.realm_results_get_list(results.cptr(), index.toULong()))

    actual fun realm_results_get_dictionary(results: RealmResultsPointer, index: Long): RealmMapPointer =
        CPointerWrapper(realm_wrapper.realm_results_get_dictionary(results.cptr(), index.toULong()))

    actual fun realm_get_object(realm: RealmPointer, link: Link): RealmObjectPointer {
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
        realm: RealmPointer,
        classKey: ClassKey,
        transport: RealmValue
    ): RealmObjectPointer? {
        val ptr = memScoped {
            val found = alloc<BooleanVar>()
            realm_wrapper.realm_object_find_with_primary_key(
                realm.cptr(),
                classKey.key.toUInt(),
                transport.value.readValue(),
                found.ptr
            )
        }
        val checkedPtr = checkedPointerResult(ptr)
        return if (checkedPtr != null) CPointerWrapper(checkedPtr) else null
    }

    actual fun realm_results_delete_all(results: RealmResultsPointer) {
        checkedBooleanResult(realm_wrapper.realm_results_delete_all(results.cptr()))
    }

    actual fun realm_object_delete(obj: RealmObjectPointer) {
        checkedBooleanResult(realm_wrapper.realm_object_delete(obj.cptr()))
    }

    actual fun realm_create_key_paths_array(realm: RealmPointer, clazz: ClassKey, keyPaths: List<String>): RealmKeyPathArrayPointer {
        memScoped {
            val userKeyPaths: CPointer<CPointerVarOf<CPointer<ByteVarOf<Byte>>>> = keyPaths.toCStringArray(this)
            val keyPathPointer = realm_wrapper.realm_create_key_path_array(realm.cptr(), clazz.key.toUInt(), keyPaths.size.toULong(), userKeyPaths)
            return CPointerWrapper(keyPathPointer)
        }
    }

    actual fun realm_object_add_notification_callback(
        obj: RealmObjectPointer,
        keyPaths: RealmKeyPathArrayPointer?,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer {
        return CPointerWrapper(
            realm_wrapper.realm_object_add_notification_callback(
                obj.cptr(),
                // Use the callback as user data
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata ->
                    userdata?.asStableRef<Callback<RealmChangesPointer>>()
                        ?.dispose()
                        ?: error("Notification callback data should never be null")
                },
                keyPaths?.cptr(),
                staticCFunction { userdata, change -> // Change callback
                    try {
                        userdata?.asStableRef<Callback<RealmChangesPointer>>()
                            ?.get()
                            ?.onChange(CPointerWrapper(realm_clone(change), managed = true))
                            ?: error("Notification callback data should never be null")
                    } catch (e: Exception) {
                        // TODO API-NOTIFICATION Consider catching errors and propagate to error
                        //  callback like the C-API error callback below
                        //  https://github.com/realm/realm-kotlin/issues/889
                        e.printStackTrace()
                    }
                },
            ),
            managed = false
        )
    }

    actual fun realm_results_add_notification_callback(
        results: RealmResultsPointer,
        keyPaths: RealmKeyPathArrayPointer?,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer {
        return CPointerWrapper(
            realm_wrapper.realm_results_add_notification_callback(
                results.cptr(),
                // Use the callback as user data
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata ->
                    userdata?.asStableRef<Callback<RealmChangesPointer>>()
                        ?.dispose()
                        ?: error("Notification callback data should never be null")
                },
                keyPaths?.cptr(),
                staticCFunction { userdata, change -> // Change callback
                    try {
                        userdata?.asStableRef<Callback<RealmChangesPointer>>()
                            ?.get()
                            ?.onChange(CPointerWrapper(realm_clone(change), managed = true))
                            ?: error("Notification callback data should never be null")
                    } catch (e: Exception) {
                        // TODO API-NOTIFICATION Consider catching errors and propagate to error
                        //  callback like the C-API error callback below
                        //  https://github.com/realm/realm-kotlin/issues/889
                        e.printStackTrace()
                    }
                },
            ),
            managed = false
        )
    }

    actual fun realm_list_add_notification_callback(
        list: RealmListPointer,
        keyPaths: RealmKeyPathArrayPointer?,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer {
        return CPointerWrapper(
            realm_wrapper.realm_list_add_notification_callback(
                list.cptr(),
                // Use the callback as user data
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata ->
                    userdata?.asStableRef<Callback<RealmChangesPointer>>()?.dispose()
                        ?: error("Notification callback data should never be null")
                },
                keyPaths?.cptr(),
                staticCFunction { userdata, change -> // Change callback
                    try {
                        userdata?.asStableRef<Callback<RealmChangesPointer>>()
                            ?.get()
                            ?.onChange(CPointerWrapper(realm_clone(change), managed = true))
                            ?: error("Notification callback data should never be null")
                    } catch (e: Exception) {
                        // TODO API-NOTIFICATION Consider catching errors and propagate to error
                        //  callback like the C-API error callback below
                        //  https://github.com/realm/realm-kotlin/issues/889
                        e.printStackTrace()
                    }
                },
            ),
            managed = false
        )
    }

    actual fun realm_set_add_notification_callback(
        set: RealmSetPointer,
        keyPaths: RealmKeyPathArrayPointer?,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer {
        return CPointerWrapper(
            realm_wrapper.realm_set_add_notification_callback(
                set.cptr(),
                // Use the callback as user data
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata ->
                    userdata?.asStableRef<Callback<RealmChangesPointer>>()
                        ?.dispose()
                        ?: error("Notification callback data should never be null")
                },
                keyPaths?.cptr(),
                staticCFunction { userdata, change -> // Change callback
                    try {
                        userdata?.asStableRef<Callback<RealmChangesPointer>>()
                            ?.get()
                            ?.onChange(CPointerWrapper(realm_clone(change), managed = true))
                            ?: error("Notification callback data should never be null")
                    } catch (e: Exception) {
                        // TODO API-NOTIFICATION Consider catching errors and propagate to error
                        //  callback like the C-API error callback below
                        //  https://github.com/realm/realm-kotlin/issues/889
                        e.printStackTrace()
                    }
                },
            ),
            managed = false
        )
    }

    actual fun realm_dictionary_add_notification_callback(
        map: RealmMapPointer,
        keyPaths: RealmKeyPathArrayPointer?,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer {
        return CPointerWrapper(
            realm_wrapper.realm_dictionary_add_notification_callback(
                map.cptr(),
                // Use the callback as user data
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata ->
                    userdata?.asStableRef<Callback<RealmChangesPointer>>()
                        ?.dispose()
                        ?: error("Notification callback data should never be null")
                },
                keyPaths?.cptr(),
                staticCFunction { userdata, change -> // Change callback
                    try {
                        userdata?.asStableRef<Callback<RealmChangesPointer>>()
                            ?.get()
                            ?.onChange(CPointerWrapper(realm_clone(change), managed = true))
                            ?: error("Notification callback data should never be null")
                    } catch (e: Exception) {
                        // TODO API-NOTIFICATION Consider catching errors and propagate to error
                        //  callback like the C-API error callback below
                        //  https://github.com/realm/realm-kotlin/issues/889
                        e.printStackTrace()
                    }
                },
            ),
            managed = false
        )
    }

    actual fun realm_object_changes_get_modified_properties(change: RealmChangesPointer): List<PropertyKey> {
        val propertyCount = realm_wrapper.realm_object_changes_get_num_modified_properties(change.cptr())

        memScoped {
            val propertyKeys = allocArray<LongVar>(propertyCount.toLong())
            realm_wrapper.realm_object_changes_get_modified_properties(change.cptr(), propertyKeys, propertyCount)
            return (0 until propertyCount.toInt()).map { PropertyKey(propertyKeys[it].toLong()) }
        }
    }

    private inline fun <reified T : CVariable> MemScope.initArray(size: CArrayPointer<ULongVar>) = allocArray<T>(size[0].toInt())

    actual fun <T, R> realm_collection_changes_get_indices(change: RealmChangesPointer, builder: CollectionChangeSetBuilder<T, R>) {
        memScoped {
            val insertionCount = allocArray<ULongVar>(1)
            val deletionCount = allocArray<ULongVar>(1)
            val modificationCount = allocArray<ULongVar>(1)
            val movesCount = allocArray<ULongVar>(1)
            val collectionWasErased = alloc<BooleanVar>()
            val collectionWasDeleted = alloc<BooleanVar>()

            realm_wrapper.realm_collection_changes_get_num_changes(
                change.cptr(),
                deletionCount,
                insertionCount,
                modificationCount,
                movesCount,
                collectionWasErased.ptr,
                collectionWasDeleted.ptr,
            )

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

    actual fun <T, R> realm_collection_changes_get_ranges(change: RealmChangesPointer, builder: CollectionChangeSetBuilder<T, R>) {
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

    actual fun <R> realm_dictionary_get_changes(
        change: RealmChangesPointer,
        builder: DictionaryChangeSetBuilder<R>
    ) {
        // TODO optimize - integrate within mem allocator?
        memScoped {
            val deletions = allocArray<ULongVar>(1)
            val insertions = allocArray<ULongVar>(1)
            val modifications = allocArray<ULongVar>(1)
            val collectionWasCleared = alloc<BooleanVar>()
            val collectionWasDeleted = alloc<BooleanVar>()

            realm_wrapper.realm_dictionary_get_changes(
                change.cptr(),
                deletions,
                insertions,
                modifications,
                collectionWasDeleted.ptr,
            )
            val deletionStructs = allocArray<realm_value_t>(deletions[0].toInt())
            val insertionStructs = allocArray<realm_value_t>(insertions[0].toInt())
            val modificationStructs = allocArray<realm_value_t>(modifications[0].toInt())

            realm_wrapper.realm_dictionary_get_changed_keys(
                change.cptr(),
                deletionStructs,
                deletions,
                insertionStructs,
                insertions,
                modificationStructs,
                modifications,
                collectionWasCleared.ptr,
            )

            val deletedKeys = (0 until deletions[0].toInt()).map {
                deletionStructs[it].string.toKotlinString()
            }
            val insertedKeys = (0 until insertions[0].toInt()).map {
                insertionStructs[it].string.toKotlinString()
            }
            val modifiedKeys = (0 until modifications[0].toInt()).map {
                modificationStructs[it].string.toKotlinString()
            }

            builder.initDeletions(deletedKeys.toTypedArray())
            builder.initInsertions(insertedKeys.toTypedArray())
            builder.initModifications(modifiedKeys.toTypedArray())
        }
    }

    actual fun realm_app_get(
        appConfig: RealmAppConfigurationPointer,
        syncClientConfig: RealmSyncClientConfigurationPointer,
        basePath: String
    ): RealmAppPointer {
        return CPointerWrapper(realm_wrapper.realm_app_create(appConfig.cptr()), managed = true)
    }

    actual fun realm_app_get_current_user(app: RealmAppPointer): RealmUserPointer? {
        val currentUserPtr: CPointer<realm_user_t>? = realm_wrapper.realm_app_get_current_user(app.cptr())
        return nativePointerOrNull(currentUserPtr)
    }

    actual fun realm_app_get_all_users(app: RealmAppPointer): List<RealmUserPointer> {
        memScoped {
            // We get the current amount of users by providing a `null` array and `out_n`
            // argument. Then the current count is written to `out_n`.
            // See https://github.com/realm/realm-core/blob/master/src/realm.h#L2634
            val capacityCount: ULongVarOf<ULong> = alloc<ULongVar>()
            checkedBooleanResult(
                realm_wrapper.realm_app_get_all_users(
                    app.cptr(),
                    null,
                    0UL,
                    capacityCount.ptr
                )
            )

            // Read actual users. We don't care about the small chance of missing a new user
            // between these two calls as that indicate two sections of user code running on
            // different threads and not coordinating.
            val actualUsersCount: ULongVarOf<ULong> = alloc<ULongVar>()
            val users = allocArray<CPointerVar<realm_user_t>>(capacityCount.value.toInt())
            checkedBooleanResult(realm_wrapper.realm_app_get_all_users(app.cptr(), users, capacityCount.value, actualUsersCount.ptr))
            val result: MutableList<RealmUserPointer> = mutableListOf()
            for (i in 0 until actualUsersCount.value.toInt()) {
                users[i]?.let { ptr: CPointer<realm_user_t> ->
                    result.add(CPointerWrapper(ptr, managed = true))
                }
            }
            return result
        }
    }

    actual fun realm_app_log_in_with_credentials(
        app: RealmAppPointer,
        credentials: RealmCredentialsPointer,
        callback: AppCallback<RealmUserPointer>
    ) {
        realm_wrapper.realm_app_log_in_with_credentials(
            app.cptr(),
            credentials.cptr(),
            staticCFunction { userData, user, error: CPointer<realm_app_error_t>? ->
                // Remember to clone user object or else it will go out of scope right after we leave this callback
                handleAppCallback(userData, error) { CPointerWrapper<RealmUserT>(realm_clone(user)) }
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userdata -> disposeUserData<AppCallback<RealmUserPointer>>(userdata) }
        )
    }

    actual fun realm_app_user_apikey_provider_client_create_apikey(
        app: RealmAppPointer,
        user: RealmUserPointer,
        name: String,
        callback: AppCallback<ApiKeyWrapper>
    ) {
        checkedBooleanResult(
            realm_wrapper.realm_app_user_apikey_provider_client_create_apikey(
                app.cptr(),
                user.cptr(),
                name,
                staticCFunction { userData: CPointer<out CPointed>?, apiKey: CPointer<realm_wrapper.realm_app_user_apikey>?, error: CPointer<realm_app_error_t>? ->
                    handleAppCallback(userData, error) {
                        apiKey!!.pointed.let {
                            ApiKeyWrapper(
                                ObjectId(
                                    it.id.bytes.readBytes(OBJECT_ID_BYTES_SIZE),
                                ),
                                it.key.safeKString(),
                                it.name.safeKString(),
                                it.disabled
                            )
                        }
                    }
                },
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata -> disposeUserData<AppCallback<ApiKeyWrapper>>(userdata) }
            )
        )
    }

    actual fun realm_app_user_apikey_provider_client_delete_apikey(
        app: RealmAppPointer,
        user: RealmUserPointer,
        id: BsonObjectId,
        callback: AppCallback<Unit>,
    ) {
        checkedBooleanResult(
            realm_wrapper.realm_app_user_apikey_provider_client_delete_apikey(
                app.cptr(),
                user.cptr(),
                id.realm_object_id_t(),
                staticCFunction { userData, error ->
                    handleAppCallback(userData, error) { /* No-op, returns Unit */ }
                },
                StableRef.create(callback).asCPointer(),
                staticCFunction { userData -> disposeUserData<AppCallback<Unit>>(userData) }
            )
        )
    }

    actual fun realm_app_user_apikey_provider_client_disable_apikey(
        app: RealmAppPointer,
        user: RealmUserPointer,
        id: BsonObjectId,
        callback: AppCallback<Unit>,
    ) {
        checkedBooleanResult(
            realm_wrapper.realm_app_user_apikey_provider_client_disable_apikey(
                app.cptr(),
                user.cptr(),
                id.realm_object_id_t(),
                staticCFunction { userData, error ->
                    handleAppCallback(userData, error) { /* No-op, returns Unit */ }
                },
                StableRef.create(callback).asCPointer(),
                staticCFunction { userData -> disposeUserData<AppCallback<Unit>>(userData) }
            )
        )
    }

    actual fun realm_app_user_apikey_provider_client_enable_apikey(
        app: RealmAppPointer,
        user: RealmUserPointer,
        id: BsonObjectId,
        callback: AppCallback<Unit>,
    ) {
        checkedBooleanResult(
            realm_wrapper.realm_app_user_apikey_provider_client_enable_apikey(
                app.cptr(),
                user.cptr(),
                id.realm_object_id_t(),
                staticCFunction { userData, error ->
                    handleAppCallback(userData, error) { /* No-op, returns Unit */ }
                },
                StableRef.create(callback).asCPointer(),
                staticCFunction { userData -> disposeUserData<AppCallback<Unit>>(userData) }
            )
        )
    }

    actual fun realm_app_user_apikey_provider_client_fetch_apikey(
        app: RealmAppPointer,
        user: RealmUserPointer,
        id: ObjectId,
        callback: AppCallback<ApiKeyWrapper>
    ) {
        checkedBooleanResult(
            realm_wrapper.realm_app_user_apikey_provider_client_fetch_apikey(
                app.cptr(),
                user.cptr(),
                id.realm_object_id_t(),
                staticCFunction { userData: CPointer<out CPointed>?, apiKey: CPointer<realm_wrapper.realm_app_user_apikey>?, error: CPointer<realm_app_error_t>? ->
                    handleAppCallback(userData, error) {
                        apiKey!!.pointed.let {
                            ApiKeyWrapper(
                                ObjectId(
                                    it.id.bytes.readBytes(OBJECT_ID_BYTES_SIZE),
                                ),
                                null,
                                it.name.safeKString(),
                                it.disabled
                            )
                        }
                    }
                },
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata -> disposeUserData<AppCallback<ApiKeyWrapper>>(userdata) }
            )
        )
    }

    actual fun realm_app_user_apikey_provider_client_fetch_apikeys(
        app: RealmAppPointer,
        user: RealmUserPointer,
        callback: AppCallback<Array<ApiKeyWrapper>>,
    ) {
        checkedBooleanResult(
            realm_wrapper.realm_app_user_apikey_provider_client_fetch_apikeys(
                app.cptr(),
                user.cptr(),
                staticCFunction { userData: CPointer<out CPointed>?, apiKeys: CPointer<realm_wrapper.realm_app_user_apikey>?, count: size_t, error: CPointer<realm_app_error_t>? ->
                    handleAppCallback(userData, error) {
                        val result = arrayOfNulls<ApiKeyWrapper>(count.toInt())
                        for (i in 0 until count.toInt()) {
                            apiKeys!![i].let {
                                result[i] = ApiKeyWrapper(
                                    ObjectId(
                                        it.id.bytes.readBytes(OBJECT_ID_BYTES_SIZE),
                                    ),
                                    null,
                                    it.name.safeKString(),
                                    it.disabled
                                )
                            }
                        }
                        result
                    }
                },
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata -> disposeUserData<AppCallback<Array<ApiKeyWrapper>>>(userdata) }
            )
        )
    }

    actual fun realm_app_log_out(
        app: RealmAppPointer,
        user: RealmUserPointer,
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
                staticCFunction { userdata -> disposeUserData<AppCallback<RealmUserPointer>>(userdata) }
            )
        )
    }

    actual fun realm_app_remove_user(
        app: RealmAppPointer,
        user: RealmUserPointer,
        callback: AppCallback<Unit>
    ) {
        checkedBooleanResult(
            realm_wrapper.realm_app_remove_user(
                app.cptr(),
                user.cptr(),
                staticCFunction { userData, error ->
                    handleAppCallback(userData, error) { /* No-op, returns Unit */ }
                },
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata ->
                    disposeUserData<AppCallback<RealmUserPointer>>(userdata)
                }
            )
        )
    }

    actual fun realm_app_delete_user(
        app: RealmAppPointer,
        user: RealmUserPointer,
        callback: AppCallback<Unit>
    ) {
        checkedBooleanResult(
            realm_wrapper.realm_app_delete_user(
                app.cptr(),
                user.cptr(),
                staticCFunction { userData, error ->
                    handleAppCallback(userData, error) { /* No-op, returns Unit */ }
                },
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata ->
                    disposeUserData<AppCallback<RealmUserPointer>>(userdata)
                }
            )
        )
    }

    actual fun realm_app_link_credentials(
        app: RealmAppPointer,
        user: RealmUserPointer,
        credentials: RealmCredentialsPointer,
        callback: AppCallback<RealmUserPointer>
    ) {
        checkedBooleanResult(
            realm_wrapper.realm_app_link_user(
                app.cptr(),
                user.cptr(),
                credentials.cptr(),
                staticCFunction { userData, user, error: CPointer<realm_app_error_t>? ->
                    // Remember to clone user object or else it will go out of scope right after we leave this callback
                    handleAppCallback(userData, error) { CPointerWrapper<RealmUserT>(realm_clone(user)) }
                },
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata ->
                    disposeUserData<AppCallback<RealmUserPointer>>(userdata)
                }
            )
        )
    }

    actual fun realm_clear_cached_apps() {
        realm_wrapper.realm_clear_cached_apps()
    }

    actual fun realm_app_sync_client_get_default_file_path_for_realm(
        syncConfig: RealmSyncConfigurationPointer,
        overriddenName: String?
    ): String {
        val cPath = realm_wrapper.realm_app_sync_client_get_default_file_path_for_realm(
            syncConfig.cptr(),
            overriddenName
        )
        return cPath.safeKString()
            .also { realm_wrapper.realm_free(cPath) }
    }

    actual fun realm_app_get_base_url(
        app: RealmAppPointer,
    ): String = realm_wrapper.realm_app_get_base_url(app.cptr())?.toKString()!!

    actual fun realm_app_update_base_url(
        app: RealmAppPointer,
        baseUrl: String?,
        callback: AppCallback<Unit>,
    ) {
        checkedBooleanResult(
            realm_wrapper.realm_app_update_base_url(
                app.cptr(),
                baseUrl,
                callback = staticCFunction { userData, error ->
                    handleAppCallback(
                        userData,
                        error
                    ) { /* No-op, returns Unit */ }
                },
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata ->
                    disposeUserData<AppCallback<Unit>>(userdata)
                }
            )
        )
    }

    actual fun realm_user_get_all_identities(user: RealmUserPointer): List<SyncUserIdentity> {
        memScoped {
            val count = AuthProvider.values().size
            val properties = allocArray<realm_wrapper.realm_user_identity>(count)
            val outCount: ULongVarOf<size_t> = alloc<size_tVar>()
            realm_wrapper.realm_user_get_all_identities(
                user.cptr(),
                properties,
                count.convert(),
                outCount.ptr
            )
            outCount.value.toLong().let { count ->
                return if (count > 0) {
                    (0 until outCount.value.toLong()).map {
                        with(properties[it]) {
                            SyncUserIdentity(this.id!!.toKString(), AuthProvider.of(this.provider_type))
                        }
                    }
                } else {
                    emptyList()
                }
            }
        }
    }

    actual fun realm_user_get_identity(user: RealmUserPointer): String {
        return realm_wrapper.realm_user_get_identity(user.cptr()).safeKString("identity")
    }

    actual fun realm_user_is_logged_in(user: RealmUserPointer): Boolean {
        return realm_wrapper.realm_user_is_logged_in(user.cptr())
    }

    actual fun realm_user_log_out(user: RealmUserPointer) {
        checkedBooleanResult(realm_wrapper.realm_user_log_out(user.cptr()))
    }

    actual fun realm_user_get_state(user: RealmUserPointer): CoreUserState {
        return CoreUserState.of(realm_wrapper.realm_user_get_state(user.cptr()))
    }

    actual fun realm_user_get_profile(user: RealmUserPointer): String =
        realm_wrapper.realm_user_get_profile_data(user.cptr()).safeKString()

    actual fun realm_user_get_custom_data(user: RealmUserPointer): String? =
        realm_wrapper.realm_user_get_custom_data(user.cptr())?.toKString()

    actual fun realm_user_refresh_custom_data(
        app: RealmAppPointer,
        user: RealmUserPointer,
        callback: AppCallback<Unit>
    ) {
        checkedBooleanResult(
            realm_wrapper.realm_app_refresh_custom_data(
                app = app.cptr(),
                user = user.cptr(),
                callback = staticCFunction { userData, error ->
                    handleAppCallback(userData, error) { /* No-op, returns Unit */ }
                },
                userdata = StableRef.create(callback).asCPointer(),
                userdata_free = staticCFunction { userData -> disposeUserData<AppCallback<Unit>>(userData) }
            )
        )
    }

    actual fun realm_sync_client_config_new(): RealmSyncClientConfigurationPointer {
        return CPointerWrapper(realm_wrapper.realm_sync_client_config_new())
    }

    actual fun realm_sync_client_config_set_default_binding_thread_observer(
        syncClientConfig: RealmSyncClientConfigurationPointer,
        appId: String
    ) {
        realm_wrapper.realm_sync_client_config_set_default_binding_thread_observer(
            config = syncClientConfig.cptr(),
            on_thread_create = staticCFunction { _ ->
                // Do nothing
            },
            on_thread_destroy = staticCFunction { _ ->
                // Do nothing. Threads in Kotlin Native are cleaned up correctly without us
                // having to do anything.
            },
            on_error = staticCFunction { userdata, error ->
                // TODO Wait for https://github.com/realm/realm-core/issues/4194 to correctly
                //  log errors. For now, just throw an Error as exceptions from the Sync Client
                //  indicate that something is fundamentally wrong on the Sync Thread.
                //  In Realm Java this has only been reported during development of new
                //  features, so throwing an Error seems appropriate to increase visibility.
                val threadId = safeUserData<String>(userdata)
                throw Error("[SyncThread-$threadId] Error on sync thread: ${error?.toKString()}")
            },
            user_data = StableRef.create(appId).asCPointer(),
            free_userdata = staticCFunction { userdata ->
                disposeUserData<String>(userdata)
            }
        )
    }

    actual fun realm_app_config_set_base_file_path(
        appConfig: RealmAppConfigurationPointer,
        basePath: String
    ) {
        realm_wrapper.realm_app_config_set_base_file_path(appConfig.cptr(), basePath)
    }

    actual fun realm_sync_client_config_set_multiplex_sessions(syncClientConfig: RealmSyncClientConfigurationPointer, enabled: Boolean) {
        realm_wrapper.realm_sync_client_config_set_multiplex_sessions(syncClientConfig.cptr(), enabled)
    }

    actual fun realm_set_log_callback(callback: LogCallback) {
        realm_wrapper.realm_set_log_callback(
            staticCFunction { userData, category, logLevel, message ->
                val userDataLogCallback = safeUserData<LogCallback>(userData)
                userDataLogCallback.log(logLevel.toShort(), category!!.toKString(), message?.toKString())
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userData -> disposeUserData<() -> LogCallback>(userData) }
        )
    }

    actual fun realm_set_log_level(level: CoreLogLevel) {
        realm_wrapper.realm_set_log_level(level.priority.toUInt())
    }

    actual fun realm_set_log_level_category(category: String, level: CoreLogLevel) {
        realm_wrapper.realm_set_log_level_category(category, level.priority.toUInt())
    }

    actual fun realm_get_log_level_category(category: String): CoreLogLevel =
        CoreLogLevel.valueFromPriority(realm_wrapper.realm_get_log_level_category(category).toShort())

    actual fun realm_get_category_names(): List<String> {
        memScoped {
            val namesCount = realm_wrapper.realm_get_category_names(0u, null)
            val namesBuffer = allocArray<CPointerVar<ByteVar>>(namesCount.toInt())
            realm_wrapper.realm_get_category_names(namesCount, namesBuffer)

            return List(namesCount.toInt()) {
                namesBuffer[it].safeKString()
            }
        }
    }

    actual fun realm_app_config_set_metadata_mode(
        appConfig: RealmAppConfigurationPointer,
        metadataMode: MetadataMode,
    ) {
        realm_wrapper.realm_app_config_set_metadata_mode(
            appConfig.cptr(),
            metadataMode.metadataValue
        )
    }

    actual fun realm_app_config_set_metadata_encryption_key(
        appConfig: RealmAppConfigurationPointer,
        encryptionKey: ByteArray
    ) {
        memScoped {
            val encryptionKeyPointer = encryptionKey.refTo(0).getPointer(memScope)
            @Suppress("UNCHECKED_CAST")
            realm_wrapper.realm_app_config_set_metadata_encryption_key(
                appConfig.cptr(),
                encryptionKeyPointer as CPointer<uint8_tVar>
            )
        }
    }

    actual fun realm_sync_client_config_set_user_agent_binding_info(
        syncClientConfig: RealmSyncClientConfigurationPointer,
        bindingInfo: String
    ) {
        realm_wrapper.realm_sync_client_config_set_user_agent_binding_info(
            syncClientConfig.cptr(),
            bindingInfo
        )
    }

    actual fun realm_sync_client_config_set_user_agent_application_info(
        syncClientConfig: RealmSyncClientConfigurationPointer,
        applicationInfo: String
    ) {
        realm_wrapper.realm_sync_client_config_set_user_agent_application_info(
            syncClientConfig.cptr(),
            applicationInfo
        )
    }

    actual fun realm_sync_client_config_set_connect_timeout(syncClientConfig: RealmSyncClientConfigurationPointer, timeoutMs: ULong) {
        realm_wrapper.realm_sync_client_config_set_connect_timeout(syncClientConfig.cptr(), timeoutMs)
    }

    actual fun realm_sync_client_config_set_connection_linger_time(syncClientConfig: RealmSyncClientConfigurationPointer, timeoutMs: ULong) {
        realm_wrapper.realm_sync_client_config_set_connection_linger_time(syncClientConfig.cptr(), timeoutMs)
    }

    actual fun realm_sync_client_config_set_ping_keepalive_period(syncClientConfig: RealmSyncClientConfigurationPointer, timeoutMs: ULong) {
        realm_wrapper.realm_sync_client_config_set_ping_keepalive_period(syncClientConfig.cptr(), timeoutMs)
    }

    actual fun realm_sync_client_config_set_pong_keepalive_timeout(syncClientConfig: RealmSyncClientConfigurationPointer, timeoutMs: ULong) {
        realm_wrapper.realm_sync_client_config_set_pong_keepalive_timeout(syncClientConfig.cptr(), timeoutMs)
    }

    actual fun realm_sync_client_config_set_fast_reconnect_limit(syncClientConfig: RealmSyncClientConfigurationPointer, timeoutMs: ULong) {
        realm_wrapper.realm_sync_client_config_set_fast_reconnect_limit(syncClientConfig.cptr(), timeoutMs)
    }

    actual fun realm_sync_config_set_error_handler(
        syncConfig: RealmSyncConfigurationPointer,
        errorHandler: SyncErrorCallback
    ) {
        realm_wrapper.realm_sync_config_set_error_handler(
            syncConfig.cptr(),
            staticCFunction { userData, syncSession, error ->
                val syncError: SyncError = error.useContents {
                    val code = CoreError(
                        this.status.categories.toInt(),
                        this.status.error.value.toInt(),
                        this.status.message.safeKString()
                    )

                    val userInfoMap = (0 until user_info_length.toInt())
                        .mapNotNull {
                            user_info_map?.get(it)
                        }.mapNotNull {
                            when {
                                it.key != null && it.value != null ->
                                    Pair(it.key.safeKString(), it.value.safeKString())
                                else -> null
                            }
                        }.toMap()

                    val compensatingWrites =
                        Array<CoreCompensatingWriteInfo>(compensating_writes_length.toInt()) { index ->
                            compensating_writes!![index].let { compensatingWriteInfo ->
                                CoreCompensatingWriteInfo(
                                    reason = compensatingWriteInfo.reason.safeKString(),
                                    objectName = compensatingWriteInfo.object_name.safeKString(),
                                    primaryKey = RealmValue(compensatingWriteInfo.primary_key)
                                )
                            }
                        }

                    SyncError(
                        errorCode = code,
                        originalFilePath = userInfoMap[c_original_file_path_key.safeKString()],
                        recoveryFilePath = userInfoMap[c_recovery_file_path_key.safeKString()],
                        isFatal = is_fatal,
                        isUnrecognizedByClient = is_unrecognized_by_client,
                        isClientResetRequested = is_client_reset_requested,
                        compensatingWrites = compensatingWrites,
                        userError = user_code_error?.asStableRef<Throwable>()?.get()
                    ).also {
                        user_code_error?.let { disposeUserData<Throwable>(it) }
                    }
                }
                val errorCallback = safeUserData<SyncErrorCallback>(userData)
                val session = CPointerWrapper<RealmSyncSessionT>(realm_clone(syncSession))
                errorCallback.onSyncError(session, syncError)
            },
            StableRef.create(errorHandler).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<(RealmSyncSessionPointer, SyncErrorCallback) -> Unit>(userdata)
            }
        )
    }

    actual fun realm_sync_config_set_resync_mode(
        syncConfig: RealmSyncConfigurationPointer,
        resyncMode: SyncSessionResyncMode
    ) {
        realm_wrapper.realm_sync_config_set_resync_mode(
            syncConfig.cptr(),
            resyncMode.value
        )
    }

    actual fun realm_sync_config_set_before_client_reset_handler(
        syncConfig: RealmSyncConfigurationPointer,
        beforeHandler: SyncBeforeClientResetHandler
    ) {
        realm_wrapper.realm_sync_config_set_before_client_reset_handler(
            syncConfig.cptr(),
            staticCFunction { userData, beforeRealm ->
                stableUserDataWithErrorPropagation<SyncBeforeClientResetHandler>(userData) {
                    val beforeDb = CPointerWrapper<FrozenRealmT>(beforeRealm, false)
                    onBeforeReset(beforeDb)
                    true
                }
            },
            StableRef.create(beforeHandler).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<SyncBeforeClientResetHandler>(userdata)
            }
        )
    }

    actual fun realm_sync_config_set_after_client_reset_handler(
        syncConfig: RealmSyncConfigurationPointer,
        afterHandler: SyncAfterClientResetHandler
    ) {
        realm_wrapper.realm_sync_config_set_after_client_reset_handler(
            syncConfig.cptr(),
            staticCFunction { userData, beforeRealm, afterRealm, didRecover ->
                stableUserDataWithErrorPropagation<SyncAfterClientResetHandler>(userData) {
                    val beforeDb = CPointerWrapper<FrozenRealmT>(beforeRealm, false)

                    // afterRealm is wrapped inside a ThreadSafeReference so the pointer needs to be resolved
                    val afterRealmPtr = realm_wrapper.realm_from_thread_safe_reference(afterRealm, null)
                    val afterDb = CPointerWrapper<LiveRealmT>(afterRealmPtr, false)

                    try {
                        onAfterReset(beforeDb, afterDb, didRecover)
                        true
                    } finally {
                        realm_wrapper.realm_close(afterRealmPtr)
                    }
                }
            },
            StableRef.create(afterHandler).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<SyncAfterClientResetHandler>(userdata)
            }
        )
    }

    actual fun realm_sync_immediately_run_file_actions(app: RealmAppPointer, syncPath: String): Boolean {
        memScoped {
            val didRun = alloc<BooleanVar>()
            checkedBooleanResult(
                realm_wrapper.realm_sync_immediately_run_file_actions(app.cptr(), syncPath, didRun.ptr)
            )
            return didRun.value
        }
    }

    actual fun realm_sync_session_get(realm: RealmPointer): RealmSyncSessionPointer {
        return CPointerWrapper(realm_wrapper.realm_sync_session_get(realm.cptr()))
    }

    actual fun realm_sync_session_wait_for_download_completion(
        syncSession: RealmSyncSessionPointer,
        callback: SyncSessionTransferCompletionCallback
    ) {
        realm_wrapper.realm_sync_session_wait_for_download_completion(
            syncSession.cptr(),
            staticCFunction<COpaquePointer?, CPointer<realm_error_t>?, Unit> { userData, error ->
                handleCompletionCallback(userData, error)
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<(RealmSyncSessionPointer, SyncSessionTransferCompletionCallback) -> Unit>(userdata)
            }
        )
    }

    actual fun realm_sync_session_wait_for_upload_completion(
        syncSession: RealmSyncSessionPointer,
        callback: SyncSessionTransferCompletionCallback
    ) {
        realm_wrapper.realm_sync_session_wait_for_upload_completion(
            syncSession.cptr(),
            staticCFunction<COpaquePointer?, CPointer<realm_error_t>?, Unit> { userData, error ->
                handleCompletionCallback(userData, error)
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<(RealmSyncSessionPointer, SyncSessionTransferCompletionCallback) -> Unit>(userdata)
            }
        )
    }

    actual fun realm_sync_session_state(syncSession: RealmSyncSessionPointer): CoreSyncSessionState {
        val value: realm_sync_session_state_e =
            realm_wrapper.realm_sync_session_get_state(syncSession.cptr())
        return CoreSyncSessionState.of(value)
    }

    actual fun realm_sync_connection_state(syncSession: RealmSyncSessionPointer): CoreConnectionState =
        CoreConnectionState.of(
            realm_wrapper.realm_sync_session_get_connection_state(syncSession.cptr())
        )

    actual fun realm_sync_session_pause(syncSession: RealmSyncSessionPointer) {
        realm_wrapper.realm_sync_session_pause(syncSession.cptr())
    }

    actual fun realm_sync_session_resume(syncSession: RealmSyncSessionPointer) {
        realm_wrapper.realm_sync_session_resume(syncSession.cptr())
    }

    actual fun realm_sync_session_handle_error_for_testing(
        syncSession: RealmSyncSessionPointer,
        error: ErrorCode,
        errorMessage: String,
        isFatal: Boolean
    ) {
        realm_wrapper.realm_sync_session_handle_error_for_testing(
            syncSession.cptr(),
            error.asNativeEnum,
            errorMessage,
            isFatal
        )
    }

    actual fun realm_sync_session_register_progress_notifier(
        syncSession: RealmSyncSessionPointer,
        direction: ProgressDirection,
        isStreaming: Boolean,
        callback: ProgressCallback,
    ): RealmNotificationTokenPointer {
        return CPointerWrapper(
            realm_wrapper.realm_sync_session_register_progress_notifier(
                syncSession.cptr(),
                staticCFunction<COpaquePointer?, ULong, ULong, Double, Unit> { userData, _, _, progress_estimate ->
                    safeUserData<ProgressCallback>(userData).run {
                        onChange(progress_estimate)
                    }
                },
                direction.nativeValue,
                isStreaming,
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata ->
                    disposeUserData<ProgressCallback>(userdata)
                }
            ),
            managed = false
        )
    }

    actual fun realm_sync_session_register_connection_state_change_callback(
        syncSession: RealmSyncSessionPointer,
        callback: ConnectionStateChangeCallback,
    ): RealmNotificationTokenPointer {
        return CPointerWrapper(
            realm_wrapper.realm_sync_session_register_connection_state_change_callback(
                syncSession.cptr(),
                staticCFunction<COpaquePointer?, realm_wrapper.realm_sync_connection_state, realm_wrapper.realm_sync_connection_state, Unit> { userData, oldState, newState ->
                    safeUserData<ConnectionStateChangeCallback>(userData).run {
                        onChange(CoreConnectionState.of(oldState), CoreConnectionState.of(newState))
                    }
                },
                StableRef.create(callback).asCPointer(),
                staticCFunction { userdata ->
                    disposeUserData<ConnectionStateChangeCallback>(userdata)
                }
            ),
            managed = false
        )
    }

    private fun handleCompletionCallback(
        userData: CPointer<out CPointed>?,
        error: CPointer<realm_error_t>?
    ) {
        val completionCallback = safeUserData<SyncSessionTransferCompletionCallback>(userData)
        if (error != null) {
            val category = error.pointed.categories.toInt()
            val value: Int = error.pointed.error.value.toInt()
            val message = error.pointed.message.safeKString()
            completionCallback.invoke(CoreError(category, value, message))
        } else {
            completionCallback.invoke(null)
        }
    }

    actual fun realm_network_transport_new(networkTransport: NetworkTransport): RealmNetworkTransportPointer {
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

    actual fun realm_sync_set_websocket_transport(
        syncClientConfig: RealmSyncClientConfigurationPointer,
        webSocketTransport: WebSocketTransport
    ) {
        val realmSyncSocketNew: CPointer<realm_sync_socket_t> =
            checkedPointerResult(
                realm_wrapper.realm_sync_socket_new(
                    userdata = StableRef.create(webSocketTransport).asCPointer(),
                    userdata_free = staticCFunction { userdata: CPointer<out CPointed>? ->
                        safeUserData<WebSocketTransport>(userdata).close()
                        disposeUserData<WebSocketTransport>(userdata)
                    },
                    post_func = staticCFunction { userdata: CPointer<out CPointed>?, syncSocketCallback: CPointer<realm_sync_socket_post_callback_t>? ->
                        val callback: WebsocketFunctionHandlerCallback = { cancelled, _, _ ->
                            realm_wrapper.realm_sync_socket_post_complete(
                                syncSocketCallback,
                                if (cancelled) WebsocketCallbackResult.RLM_ERR_SYNC_SOCKET_OPERATION_ABORTED.asNativeEnum else WebsocketCallbackResult.RLM_ERR_SYNC_SOCKET_SUCCESS.asNativeEnum,
                                ""
                            )
                        }

                        safeUserData<WebSocketTransport>(userdata).post(
                            CPointerWrapper(StableRef.create(callback).asCPointer())
                        )
                    },
                    create_timer_func = staticCFunction { userdata: CPointer<out CPointed>?, delayInMilliseconds: uint64_t, syncSocketCallback: CPointer<realm_sync_socket_timer_callback_t>? ->
                        val callback: WebsocketFunctionHandlerCallback = { cancelled, _, _ ->
                            if (cancelled) {
                                realm_wrapper.realm_sync_socket_timer_canceled(syncSocketCallback)
                            } else {
                                realm_wrapper.realm_sync_socket_timer_complete(
                                    syncSocketCallback,
                                    WebsocketCallbackResult.RLM_ERR_SYNC_SOCKET_SUCCESS.asNativeEnum,
                                    ""
                                )
                            }
                        }

                        safeUserData<WebSocketTransport>(userdata).let { ws ->
                            val job: CancellableTimer = ws.createTimer(
                                delayInMilliseconds.toLong(),
                                CPointerWrapper(StableRef.create(callback).asCPointer())
                            )
                            StableRef.create(job).asCPointer()
                        }
                    },
                    cancel_timer_func = staticCFunction { userdata: CPointer<out CPointed>?, timer: realm_sync_socket_timer_t? ->
                        safeUserData<CancellableTimer>(timer).cancel()
                    },
                    free_timer_func = staticCFunction { userdata: CPointer<out CPointed>?, timer: realm_sync_socket_timer_t? ->
                        disposeUserData<WebSocketTransport>(timer)
                    },
                    websocket_connect_func = staticCFunction { userdata: CPointer<out CPointed>?, endpoint: CValue<realm_wrapper.realm_websocket_endpoint_t>, observer: CPointer<realm_wrapper.realm_websocket_observer_t>? ->
                        safeUserData<WebSocketTransport>(userdata).let { websocketTransport ->
                            endpoint.useContents {
                                val managedObserver = WebSocketObserver(CPointerWrapper(observer))

                                val supportedProtocols = mutableListOf<String>()
                                for (i in 0 until this.num_protocols.toInt()) {
                                    val protocol: CPointer<ByteVarOf<Byte>>? =
                                        this.protocols?.get(i)
                                    supportedProtocols.add(protocol.safeKString())
                                }
                                val webSocketClient: WebSocketClient = websocketTransport.connect(
                                    managedObserver,
                                    this.path.safeKString(),
                                    this.address.safeKString(),
                                    this.port.toLong(),
                                    this.is_ssl,
                                    this.num_protocols.toLong(),
                                    supportedProtocols.joinToString(", ")
                                )
                                StableRef.create(webSocketClient).asCPointer()
                            }
                        }
                    },
                    websocket_write_func = staticCFunction { userdata: CPointer<out CPointed>?, websocket: realm_sync_socket_websocket_t?, data: CPointer<ByteVar>?, length: size_t, callback: CPointer<realm_sync_socket_write_callback_t>? ->
                        val postWriteCallback: WebsocketFunctionHandlerCallback =
                            { cancelled, status, reason ->
                                realm_wrapper.realm_sync_socket_write_complete(
                                    callback,
                                    if (cancelled) WebsocketCallbackResult.RLM_ERR_SYNC_SOCKET_OPERATION_ABORTED.asNativeEnum else WebsocketCallbackResult.RLM_ERR_SYNC_SOCKET_SUCCESS.asNativeEnum,
                                    reason
                                )
                            }

                        safeUserData<WebSocketTransport>(userdata).let { websocketTransport ->
                            safeUserData<WebSocketClient>(websocket).let { webSocketClient ->
                                data?.readBytes(length.toInt())?.run {
                                    websocketTransport.write(
                                        webSocketClient,
                                        this,
                                        length.toLong(),
                                        CPointerWrapper(
                                            StableRef.create(postWriteCallback).asCPointer()
                                        )
                                    )
                                }
                            }
                        }
                        Unit
                    },
                    websocket_free_func = staticCFunction { userdata: CPointer<out CPointed>?, websocket: realm_sync_socket_websocket_t? ->
                        safeUserData<WebSocketClient>(websocket).close()
                        disposeUserData<WebSocketClient>(websocket)
                    }
                )
            ) ?: error("Couldn't create Sync Socket")
        realm_wrapper.realm_sync_client_config_set_sync_socket(
            syncClientConfig.cptr(),
            realmSyncSocketNew
        )
        realm_release(realmSyncSocketNew)
    }

    actual fun realm_sync_socket_callback_complete(nativePointer: RealmWebsocketHandlerCallbackPointer, cancelled: Boolean, status: WebsocketCallbackResult, reason: String) {
        safeUserData<WebsocketFunctionHandlerCallback>(nativePointer.cptr())(cancelled, status, reason)
        disposeUserData<WebsocketFunctionHandlerCallback>(nativePointer.cptr())
    }

    actual fun realm_sync_socket_websocket_connected(nativePointer: RealmWebsocketProviderPointer, protocol: String) {
        realm_wrapper.realm_sync_socket_websocket_connected(nativePointer.cptr(), protocol)
    }

    actual fun realm_sync_socket_websocket_error(nativePointer: RealmWebsocketProviderPointer) {
        realm_wrapper.realm_sync_socket_websocket_error(nativePointer.cptr())
    }

    actual fun realm_sync_socket_websocket_message(
        nativePointer: RealmWebsocketProviderPointer,
        data: ByteArray
    ): Boolean {
        return realm_wrapper.realm_sync_socket_websocket_message(
            nativePointer.cptr(),
            data.toCValues(),
            data.size.toULong()
        )
    }

    actual fun realm_sync_socket_websocket_closed(nativePointer: RealmWebsocketProviderPointer, wasClean: Boolean, errorCode: WebsocketErrorCode, reason: String) {
        realm_wrapper.realm_sync_socket_websocket_closed(nativePointer.cptr(), wasClean, errorCode.asNativeEnum, reason)
    }

    @Suppress("LongParameterList")
    actual fun realm_app_config_new(
        appId: String,
        networkTransport: RealmNetworkTransportPointer,
        baseUrl: String?,
        connectionParams: SyncConnectionParams
    ): RealmAppConfigurationPointer {
        val appConfig = realm_wrapper.realm_app_config_new(appId, networkTransport.cptr())
        baseUrl?.let { realm_wrapper.realm_app_config_set_base_url(appConfig, it) }

        // Sync Connection Parameters
        realm_wrapper.realm_app_config_set_sdk(appConfig, connectionParams.sdkName)
        realm_wrapper.realm_app_config_set_sdk_version(appConfig, connectionParams.sdkVersion)
        realm_wrapper.realm_app_config_set_platform_version(appConfig, connectionParams.platformVersion)
        realm_wrapper.realm_app_config_set_device_name(appConfig, connectionParams.device)
        realm_wrapper.realm_app_config_set_device_version(appConfig, connectionParams.deviceVersion)
        realm_wrapper.realm_app_config_set_framework_name(appConfig, connectionParams.framework)
        realm_wrapper.realm_app_config_set_framework_version(appConfig, connectionParams.frameworkVersion)
        realm_wrapper.realm_app_config_set_bundle_id(appConfig, connectionParams.bundleId)

        return CPointerWrapper(appConfig)
    }

    actual fun realm_app_config_set_base_url(appConfig: RealmAppConfigurationPointer, baseUrl: String) {
        realm_wrapper.realm_app_config_set_base_url(appConfig.cptr(), baseUrl)
    }

    actual fun realm_app_credentials_new_anonymous(reuseExisting: Boolean): RealmCredentialsPointer {
        return CPointerWrapper(realm_wrapper.realm_app_credentials_new_anonymous(reuseExisting))
    }

    actual fun realm_app_credentials_new_email_password(
        username: String,
        password: String
    ): RealmCredentialsPointer {
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

    actual fun realm_app_credentials_new_api_key(key: String): RealmCredentialsPointer {
        memScoped {
            return CPointerWrapper(realm_wrapper.realm_app_credentials_new_api_key(key))
        }
    }

    actual fun realm_app_credentials_new_apple(idToken: String): RealmCredentialsPointer {
        memScoped {
            return CPointerWrapper(realm_wrapper.realm_app_credentials_new_apple(idToken))
        }
    }

    actual fun realm_app_credentials_new_facebook(accessToken: String): RealmCredentialsPointer {
        memScoped {
            return CPointerWrapper(realm_wrapper.realm_app_credentials_new_facebook(accessToken))
        }
    }

    actual fun realm_app_credentials_new_google_id_token(idToken: String): RealmCredentialsPointer {
        memScoped {
            return CPointerWrapper(realm_wrapper.realm_app_credentials_new_google_id_token(idToken))
        }
    }

    actual fun realm_app_credentials_new_google_auth_code(authCode: String): RealmCredentialsPointer {
        memScoped {
            return CPointerWrapper(realm_wrapper.realm_app_credentials_new_google_auth_code(authCode))
        }
    }

    actual fun realm_app_credentials_new_jwt(jwtToken: String): RealmCredentialsPointer {
        memScoped {
            return CPointerWrapper(realm_wrapper.realm_app_credentials_new_jwt(jwtToken))
        }
    }

    actual fun realm_app_credentials_new_custom_function(serializedEjsonPayload: String): RealmCredentialsPointer {
        memScoped {
            return CPointerWrapper(realm_wrapper.realm_app_credentials_new_function(serializedEjsonPayload))
        }
    }

    actual fun realm_auth_credentials_get_provider(credentials: RealmCredentialsPointer): AuthProvider {
        return AuthProvider.of(realm_wrapper.realm_auth_credentials_get_provider(credentials.cptr()))
    }

    actual fun realm_user_get_access_token(user: RealmUserPointer): String {
        return realm_wrapper.realm_user_get_access_token(user.cptr()).safeKString()
    }

    actual fun realm_user_get_refresh_token(user: RealmUserPointer): String {
        return realm_wrapper.realm_user_get_refresh_token(user.cptr()).safeKString()
    }

    actual fun realm_user_get_device_id(user: RealmUserPointer): String {
        return realm_wrapper.realm_user_get_device_id(user.cptr()).safeKString()
    }

    actual fun realm_app_credentials_serialize_as_json(credentials: RealmCredentialsPointer): String {
        return realm_wrapper
            .realm_app_credentials_serialize_as_json(credentials.cptr())
            .safeKString("credentials")
    }

    actual fun realm_app_email_password_provider_client_register_email(
        app: RealmAppPointer,
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
    actual fun realm_app_email_password_provider_client_confirm_user(
        app: RealmAppPointer,
        token: String,
        tokenId: String,
        callback: AppCallback<Unit>
    ) {
        memScoped {
            checkedBooleanResult(
                realm_wrapper.realm_app_email_password_provider_client_confirm_user(
                    app.cptr(),
                    token,
                    tokenId,
                    staticCFunction { userData, error ->
                        handleAppCallback(userData, error) { /* No-op, returns Unit */ }
                    },
                    StableRef.create(callback).asCPointer(),
                    staticCFunction { userData -> disposeUserData<AppCallback<Unit>>(userData) }
                )
            )
        }
    }

    actual fun realm_app_email_password_provider_client_resend_confirmation_email(
        app: RealmAppPointer,
        email: String,
        callback: AppCallback<Unit>
    ) {
        memScoped {
            checkedBooleanResult(
                realm_wrapper.realm_app_email_password_provider_client_resend_confirmation_email(
                    app.cptr(),
                    email,
                    staticCFunction { userData, error ->
                        handleAppCallback(userData, error) { /* No-op, returns Unit */ }
                    },
                    StableRef.create(callback).asCPointer(),
                    staticCFunction { userData -> disposeUserData<AppCallback<Unit>>(userData) }
                )
            )
        }
    }

    actual fun realm_app_email_password_provider_client_retry_custom_confirmation(
        app: RealmAppPointer,
        email: String,
        callback: AppCallback<Unit>
    ) {
        memScoped {
            checkedBooleanResult(
                realm_wrapper.realm_app_email_password_provider_client_retry_custom_confirmation(
                    app.cptr(),
                    email,
                    staticCFunction { userData, error ->
                        handleAppCallback(userData, error) { /* No-op, returns Unit */ }
                    },
                    StableRef.create(callback).asCPointer(),
                    staticCFunction { userData -> disposeUserData<AppCallback<Unit>>(userData) }
                )
            )
        }
    }

    actual fun realm_app_email_password_provider_client_send_reset_password_email(
        app: RealmAppPointer,
        email: String,
        callback: AppCallback<Unit>
    ) {
        memScoped {
            checkedBooleanResult(
                realm_wrapper.realm_app_email_password_provider_client_send_reset_password_email(
                    app.cptr(),
                    email,
                    staticCFunction { userData, error ->
                        handleAppCallback(userData, error) { /* No-op, returns Unit */ }
                    },
                    StableRef.create(callback).asCPointer(),
                    staticCFunction { userData -> disposeUserData<AppCallback<Unit>>(userData) }
                )
            )
        }
    }

    actual fun realm_app_email_password_provider_client_reset_password(
        app: RealmAppPointer,
        token: String,
        tokenId: String,
        newPassword: String,
        callback: AppCallback<Unit>
    ) {
        memScoped {
            checkedBooleanResult(
                realm_wrapper.realm_app_email_password_provider_client_reset_password(
                    app.cptr(),
                    newPassword.toRString(this),
                    token,
                    tokenId,
                    staticCFunction { userData, error ->
                        handleAppCallback(userData, error) { /* No-op, returns Unit */ }
                    },
                    StableRef.create(callback).asCPointer(),
                    staticCFunction { userData -> disposeUserData<AppCallback<Unit>>(userData) }
                )
            )
        }
    }

    actual fun realm_app_call_function(
        app: RealmAppPointer,
        user: RealmUserPointer,
        name: String,
        serviceName: String?,
        serializedEjsonArgs: String,
        callback: AppCallback<String>
    ) {
        realm_wrapper.realm_app_call_function(
            app.cptr(),
            user.cptr(),
            name,
            serializedEjsonArgs,
            serviceName,
            staticCFunction { userData: CPointer<out CPointed>?, data: CPointer<ByteVarOf<Byte>>?, error: CPointer<realm_app_error_t>? ->
                handleAppCallback(userData, error) {
                    data.safeKString()
                }
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userData -> disposeUserData<AppCallback<String>>(userData) }
        )
    }

    actual fun realm_app_call_reset_password_function(
        app: RealmAppPointer,
        email: String,
        newPassword: String,
        serializedEjsonPayload: String,
        callback: AppCallback<Unit>
    ) {
        memScoped {
            checkedBooleanResult(
                realm_wrapper.realm_app_email_password_provider_client_call_reset_password_function(
                    app.cptr(),
                    email,
                    newPassword.toRString(this),
                    serializedEjsonPayload,
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
        user: RealmUserPointer,
        partition: String
    ): RealmSyncConfigurationPointer {
        return CPointerWrapper<RealmSyncConfigT>(realm_wrapper.realm_sync_config_new(user.cptr(), partition)).also { ptr ->
            // Stop the session immediately when the Realm is closed, so the lifecycle of the
            // Sync Client thread is manageable.
            realm_wrapper.realm_sync_config_set_session_stop_policy(ptr.cptr(), realm_sync_session_stop_policy_e.RLM_SYNC_SESSION_STOP_POLICY_IMMEDIATELY)
        }
    }

    actual fun realm_app_sync_client_reconnect(app: RealmAppPointer) {
        realm_wrapper.realm_app_sync_client_reconnect(app.cptr())
    }
    actual fun realm_app_sync_client_has_sessions(app: RealmAppPointer): Boolean {
        return realm_wrapper.realm_app_sync_client_has_sessions(app.cptr())
    }

    actual fun realm_app_sync_client_wait_for_sessions_to_terminate(app: RealmAppPointer) {
        realm_wrapper.realm_app_sync_client_wait_for_sessions_to_terminate(app.cptr())
    }

    actual fun realm_config_set_sync_config(realmConfiguration: RealmConfigurationPointer, syncConfiguration: RealmSyncConfigurationPointer) {
        realm_wrapper.realm_config_set_sync_config(realmConfiguration.cptr(), syncConfiguration.cptr())
    }

    actual fun realm_flx_sync_config_new(user: RealmUserPointer): RealmSyncConfigurationPointer {
        return CPointerWrapper(realm_wrapper.realm_flx_sync_config_new((user.cptr())))
    }

    actual fun realm_sync_subscription_id(subscription: RealmSubscriptionPointer): ObjectId {
        return ObjectId(realm_wrapper.realm_sync_subscription_id(subscription.cptr()).getBytes())
    }

    actual fun realm_sync_subscription_name(subscription: RealmSubscriptionPointer): String? {
        return realm_wrapper.realm_sync_subscription_name(subscription.cptr()).useContents {
            this.toNullableKotlinString()
        }
    }

    actual fun realm_sync_subscription_object_class_name(subscription: RealmSubscriptionPointer): String {
        return realm_wrapper.realm_sync_subscription_object_class_name(subscription.cptr()).useContents {
            this.toKotlinString()
        }
    }

    actual fun realm_sync_subscription_query_string(subscription: RealmSubscriptionPointer): String {
        return realm_wrapper.realm_sync_subscription_query_string(subscription.cptr()).useContents {
            this.toKotlinString()
        }
    }

    actual fun realm_sync_subscription_created_at(subscription: RealmSubscriptionPointer): Timestamp {
        return realm_wrapper.realm_sync_subscription_created_at(subscription.cptr()).useContents {
            TimestampImpl(this.seconds, this.nanoseconds)
        }
    }

    actual fun realm_sync_subscription_updated_at(subscription: RealmSubscriptionPointer): Timestamp {
        return realm_wrapper.realm_sync_subscription_updated_at(subscription.cptr()).useContents {
            TimestampImpl(this.seconds, this.nanoseconds)
        }
    }

    actual fun realm_sync_get_latest_subscriptionset(realm: RealmPointer): RealmSubscriptionSetPointer {
        return CPointerWrapper(realm_wrapper.realm_sync_get_latest_subscription_set(realm.cptr()))
    }

    actual fun realm_sync_on_subscriptionset_state_change_async(
        subscriptionSet: RealmSubscriptionSetPointer,
        destinationState: CoreSubscriptionSetState,
        callback: SubscriptionSetCallback
    ) {
        realm_wrapper.realm_sync_on_subscription_set_state_change_async(
            subscriptionSet.cptr(),
            destinationState.nativeValue,
            staticCFunction<COpaquePointer?, realm_flx_sync_subscription_set_state_e, Unit> { userData, state ->
                val callback = safeUserData<SubscriptionSetCallback>(userData)
                callback.onChange(CoreSubscriptionSetState.of(state))
            },
            StableRef.create(callback).asCPointer(),
            staticCFunction { userdata ->
                disposeUserData<(SubscriptionSetCallback) -> Unit>(userdata)
            }
        )
    }

    actual fun realm_sync_subscriptionset_version(subscriptionSet: RealmBaseSubscriptionSetPointer): Long {
        return realm_wrapper.realm_sync_subscription_set_version(subscriptionSet.cptr())
    }

    actual fun realm_sync_subscriptionset_state(subscriptionSet: RealmBaseSubscriptionSetPointer): CoreSubscriptionSetState {
        val value: realm_flx_sync_subscription_set_state_e =
            realm_wrapper.realm_sync_subscription_set_state(subscriptionSet.cptr())
        return CoreSubscriptionSetState.of(value)
    }

    actual fun realm_sync_subscriptionset_error_str(subscriptionSet: RealmBaseSubscriptionSetPointer): String? {
        return realm_wrapper.realm_sync_subscription_set_error_str(subscriptionSet.cptr())?.toKString()
    }

    actual fun realm_sync_subscriptionset_size(subscriptionSet: RealmBaseSubscriptionSetPointer): Long {
        return realm_wrapper.realm_sync_subscription_set_size(subscriptionSet.cptr()).toLong()
    }

    actual fun realm_sync_subscription_at(
        subscriptionSet: RealmBaseSubscriptionSetPointer,
        index: Long
    ): RealmSubscriptionPointer {
        return CPointerWrapper(realm_wrapper.realm_sync_subscription_at(subscriptionSet.cptr(), index.toULong()))
    }

    actual fun realm_sync_find_subscription_by_name(
        subscriptionSet: RealmBaseSubscriptionSetPointer,
        name: String
    ): RealmSubscriptionPointer? {
        val ptr = realm_wrapper.realm_sync_find_subscription_by_name(subscriptionSet.cptr(), name)
        return nativePointerOrNull(ptr)
    }

    actual fun realm_sync_find_subscription_by_query(
        subscriptionSet: RealmBaseSubscriptionSetPointer,
        query: RealmQueryPointer
    ): RealmSubscriptionPointer? {
        val ptr = realm_wrapper.realm_sync_find_subscription_by_query(subscriptionSet.cptr(), query.cptr())
        return nativePointerOrNull(ptr)
    }

    actual fun realm_sync_subscriptionset_refresh(subscriptionSet: RealmSubscriptionSetPointer): Boolean {
        return realm_wrapper.realm_sync_subscription_set_refresh(subscriptionSet.cptr())
    }

    actual fun realm_sync_make_subscriptionset_mutable(
        subscriptionSet: RealmSubscriptionSetPointer
    ): RealmMutableSubscriptionSetPointer {
        return CPointerWrapper(
            realm_wrapper.realm_sync_make_subscription_set_mutable(subscriptionSet.cptr()),
            managed = false
        )
    }

    actual fun realm_sync_subscriptionset_clear(
        mutableSubscriptionSet: RealmMutableSubscriptionSetPointer
    ): Boolean {
        val erased = realm_wrapper.realm_sync_subscription_set_size(mutableSubscriptionSet.cptr()).toLong() > 0
        checkedBooleanResult(
            realm_wrapper.realm_sync_subscription_set_clear(mutableSubscriptionSet.cptr())
        )
        return erased
    }

    actual fun realm_sync_subscriptionset_insert_or_assign(
        mutableSubscriptionSet: RealmMutableSubscriptionSetPointer,
        query: RealmQueryPointer,
        name: String?
    ): Pair<RealmSubscriptionPointer, Boolean> {
        memScoped {
            val outIndex = alloc<size_tVar>()
            val outInserted = alloc<BooleanVar>()
            realm_wrapper.realm_sync_subscription_set_insert_or_assign_query(
                mutableSubscriptionSet.cptr(),
                query.cptr(),
                name,
                outIndex.ptr,
                outInserted.ptr
            )
            @Suppress("UNCHECKED_CAST")
            return Pair(
                realm_sync_subscription_at(
                    mutableSubscriptionSet as RealmSubscriptionSetPointer,
                    outIndex.value.toLong()
                ),
                outInserted.value
            )
        }
    }

    actual fun realm_sync_subscriptionset_erase_by_name(
        mutableSubscriptionSet: RealmMutableSubscriptionSetPointer,
        name: String
    ): Boolean {
        memScoped {
            val erased = alloc<BooleanVar>()
            checkedBooleanResult(
                realm_wrapper.realm_sync_subscription_set_erase_by_name(
                    mutableSubscriptionSet.cptr(),
                    name,
                    erased.ptr
                )
            )
            return erased.value
        }
    }

    actual fun realm_sync_subscriptionset_erase_by_query(
        mutableSubscriptionSet: RealmMutableSubscriptionSetPointer,
        query: RealmQueryPointer
    ): Boolean {
        memScoped {
            val erased = alloc<BooleanVar>()
            checkedBooleanResult(
                realm_wrapper.realm_sync_subscription_set_erase_by_query(
                    mutableSubscriptionSet.cptr(),
                    query.cptr(),
                    erased.ptr
                )
            )
            return erased.value
        }
    }

    actual fun realm_sync_subscriptionset_erase_by_id(
        mutableSubscriptionSet: RealmMutableSubscriptionSetPointer,
        sub: RealmSubscriptionPointer
    ): Boolean {
        memScoped {
            val id = realm_wrapper.realm_sync_subscription_id(sub.cptr())
            val erased = alloc<BooleanVar>()
            checkedBooleanResult(
                realm_wrapper.realm_sync_subscription_set_erase_by_id(
                    mutableSubscriptionSet.cptr(),
                    id,
                    erased.ptr
                )
            )
            return erased.value
        }
    }

    actual fun realm_sync_subscriptionset_commit(
        mutableSubscriptionSet: RealmMutableSubscriptionSetPointer
    ): RealmSubscriptionSetPointer {
        return CPointerWrapper(realm_wrapper.realm_sync_subscription_set_commit(mutableSubscriptionSet.cptr()))
    }

    /**
     * C-API functions for queries receive a pointer to one or more 'realm_query_arg_t' query
     * arguments. In turn, said arguments contain individual values or lists of values (in
     * combination with the 'is_list' flag) in order to support predicates like
     *
     * "fruit IN {'apple', 'orange'}"
     *
     * which is a statement equivalent to
     *
     * "fruit == 'apple' || fruit == 'orange'"
     *
     * See https://github.com/realm/realm-core/issues/4266 for more info.
     */
    private fun Array<RealmValue>.toQueryArgs(memScope: MemScope): CPointer<realm_query_arg_t> {
        with(memScope) {
            val cArgs: CPointer<realm_query_arg_t> = allocArray<realm_query_arg_t>(this@toQueryArgs.size)
            this@toQueryArgs.mapIndexed { i, arg: RealmValue ->
                cArgs[i].apply {
                    this.nb_args = 1.toULong()
                    this.is_list = false
                    this.arg = arg.value.ptr
                }
            }
            return cArgs
        }
    }

    private fun <T : CapiT> nativePointerOrNull(ptr: CPointer<*>?, managed: Boolean = true): NativePointer<T>? {
        return if (ptr != null) {
            CPointerWrapper(ptr, managed)
        } else {
            null
        }
    }

    private fun MemScope.classInfo(
        realm: RealmPointer,
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
        realm: RealmPointer,
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

    private fun CPointer<ByteVar>?.safeKString(identifier: String? = null): String {
        return this?.toKString()
            ?: throw NullPointerException(identifier?.let { "'$identifier' shouldn't be null." })
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
            val err: realm_app_error_t = error.pointed
            val ex = AppError.newInstance(
                err.categories.toInt(),
                err.error.value.toInt(),
                err.http_status_code,
                err.message?.toKString(),
                err.link_to_server_logs?.toKString()
            )
            userDataCallback.onError(ex)
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
                        else -> error("Unknown method: $method")
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

    interface Scheduler {
        fun notify(work_queue: CPointer<realm_work_queue_t>?)
    }

    class SingleThreadDispatcherScheduler(
        val threadId: ULong,
        dispatcher: CoroutineDispatcher
    ) : Scheduler {
        private val scope: CoroutineScope = CoroutineScope(dispatcher)
        val ref: CPointer<out CPointed> = StableRef.create(this).asCPointer()
        private lateinit var scheduler: CPointer<realm_scheduler_t>
        private val schedulerLock = SynchronizableObject()
        private val dispatcherLock = SynchronizableObject()
        private var cancelled = false
        private var dispatcherClosing = false

        fun setScheduler(scheduler: CPointer<realm_scheduler_t>) {
            this.scheduler = scheduler
        }

        override fun notify(work_queue: CPointer<realm_work_queue_t>?) {
            // Use a lock as a work-around for https://github.com/realm/realm-kotlin/issues/1608
            //
            // As the Core listeners are separated from Coroutines, there is a chance
            // that we have closed the Kotlin dispatcher and scheduler while Core is in the
            // process of sending notifications. If this happens we might end up in this
            // `notify` method with the dispatcher and scheduler being closed.
            //
            // As the ClosableDispatcher does not expose a `isClosed` state, it means
            // there is no way for us to detect if it is safe to launch a task using
            // the current coroutine APIs.
            //
            // Ass a work-around we use the `canceled` flag that is being set when the Scheduler
            // is being released. This should be safe as we are only closing the dispatcher when
            // releasing the scheduler. See [io.realm.kotlin.internal.util.LiveRealmContext] for
            // the logic around this.
            //
            // Note, JVM and Native behave differently on this. See this issue for more
            // details: https://github.com/Kotlin/kotlinx.coroutines/issues/3993
            dispatcherLock.withLock {
                if (!dispatcherClosing) {
                    scope.launch {
                        try {
                            printlntid("on dispatcher")
                            schedulerLock.withLock {
                                if (!cancelled) {
                                    realm_wrapper.realm_scheduler_perform_work(work_queue)
                                }
                            }
                        } catch (e: Exception) {
                            // Should never happen, but is included for development to get some indicators
                            // on errors instead of silent crashes.
                            e.printStackTrace()
                        }
                    }
                }
            }
        }

        fun cancel() {
            dispatcherLock.withLock {
                dispatcherClosing = true
            }
            schedulerLock.withLock {
                cancelled = true
            }
        }
    }
}

private typealias WebsocketFunctionHandlerCallback = (Boolean, WebsocketCallbackResult, String) -> Unit

fun realm_value_t.asByteArray(): ByteArray {
    if (this.type != realm_value_type.RLM_TYPE_BINARY) {
        error("Value is not of type ByteArray: $this.type")
    }

    val size = this.binary.size.toInt()
    return requireNotNull(this.binary.data).readBytes(size)
}

fun realm_value_t.asTimestamp(): Timestamp {
    if (this.type != realm_value_type.RLM_TYPE_TIMESTAMP) {
        error("Value is not of type Timestamp: $this.type")
    }
    return TimestampImpl(this.timestamp.seconds, this.timestamp.nanoseconds)
}

fun realm_value_t.asLink(): Link {
    if (this.type != realm_value_type.RLM_TYPE_LINK) {
        error("Value is not of type link: $this.type")
    }
    return Link(ClassKey(this.link.target_table.toLong()), this.link.target)
}

private fun BsonObjectId.realm_object_id_t(): CValue<realm_object_id_t> {
    return cValue {
        memScoped {
            this@realm_object_id_t.toByteArray().usePinned {
                memcpy(bytes.getPointer(memScope), it.addressOf(0), OBJECT_ID_BYTES_SIZE.toULong())
            }
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
@Suppress("NOTHING_TO_INLINE")
private inline fun printlntid(s: String) = Unit

private fun printlnWithTid(s: String) {
    // Don't try to optimize. Putting tid() call directly in formatted string causes crashes
    // (probably some compiler optimizations that causes references to be collected to early)
    val tid = tid()
    println("<" + tid.toString() + "> $s")
}

private fun tid(): ULong {
    memScoped {
        val tidVar = alloc<ULongVar>()
        pthread_threadid_np(null, tidVar.ptr).ensureUnixCallResult("pthread_threadid_np")
        return tidVar.value
    }
}

private fun getUnixError() = strerror(posix_errno())!!.toKString()

@Suppress("NOTHING_TO_INLINE")
private inline fun Int.ensureUnixCallResult(s: String): Int {
    if (this != 0) {
        throw Error("$s ${getUnixError()}")
    }
    return this
}
