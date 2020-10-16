/*
    FIXME: License, since this header may be distributed independently from
    other headers.
*/

#ifndef REALM_H
#define REALM_H

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>

#if defined(_WIN32) || defined(__CYGWIN__)

#if defined(Realm_EXPORTS)
// Exporting Win32 symbols
#define RLM_EXPORT __declspec(dllexport)
#else
// Importing Win32 symbols. Note: Clients linking statically should define
// RLM_NO_DLLIMPORT.
#if !defined(RLM_NO_DLLIMPORT)
#define RLM_EXPORT __declspec(dllimport)
#else
#define RLM_EXPORT
#endif // RLM_NO_DLLIMPORT
#endif // Realm_EXPORTS

#else
// Not Win32
#define RLM_EXPORT
#endif

#ifdef __cplusplus
#define RLM_API extern "C" RLM_EXPORT
#else
#define RLM_API RLM_EXPORT
#endif // __cplusplus


typedef struct shared_realm realm_t;
typedef struct realm_schema realm_schema_t;
typedef struct realm_scheduler realm_scheduler_t;
typedef void (*realm_free_userdata_func_t)(void*);
typedef void* (*realm_clone_userdata_func_t)(const void*);

/* Accessor types */
typedef struct realm_object realm_object_t;
typedef struct realm_list realm_list_t;
typedef struct realm_set realm_set_t;
typedef struct realm_dictionary realm_dictionary_t;

/* Query types */
typedef struct realm_query realm_query_t;
typedef struct realm_results realm_results_t;

/* Config types */
typedef struct realm_config realm_config_t;
typedef struct realm_sync_config realm_sync_config_t;
typedef void (*realm_migration_func_t)(void* userdata, realm_t* old_realm, realm_t* new_realm,
                                       const realm_schema_t* schema);
typedef void (*realm_data_initialization_func_t)(void* userdata, realm_t* realm);
typedef bool (*realm_should_compact_on_launch_func_t)(void* userdata, uint64_t total_bytes, uint64_t used_bytes);
typedef enum realm_schema_mode {
    RLM_SCHEMA_MODE_AUTOMATIC,
    RLM_SCHEMA_MODE_IMMUTABLE,
    RLM_SCHEMA_MODE_READ_ONLY_ALTERNATIVE,
    RLM_SCHEMA_MODE_RESET_FILE,
    RLM_SCHEMA_MODE_ADDITIVE,
    RLM_SCHEMA_MODE_MANUAL,
} realm_schema_mode_e;

/* Key types */
typedef struct realm_table_key {
    uint32_t table_key;
} realm_table_key_t;

typedef struct realm_col_key {
    int64_t col_key;
} realm_col_key_t;

typedef struct realm_obj_key {
    int64_t obj_key;
} realm_obj_key_t;

typedef struct realm_version {
    uint64_t version;
} realm_version_t;


/* Value types */

typedef enum realm_value_type {
    RLM_TYPE_NULL,
    RLM_TYPE_INT,
    RLM_TYPE_BOOL,
    RLM_TYPE_STRING,
    RLM_TYPE_BINARY,
    RLM_TYPE_TIMESTAMP,
    RLM_TYPE_FLOAT,
    RLM_TYPE_DOUBLE,
    RLM_TYPE_DECIMAL128,
    RLM_TYPE_OBJECT_ID,
    RLM_TYPE_LINK,
} realm_value_type_e;

typedef struct realm_string {
    const char* data;
    size_t size;
} realm_string_t;

typedef struct realm_binary {
    const uint8_t* data;
    size_t size;
} realm_binary_t;

typedef struct realm_timestamp {
    int64_t seconds;
    int32_t nanoseconds;
} realm_timestamp_t;

typedef struct realm_decimal128 {
    uint64_t w[2];
} realm_decimal128_t;

typedef struct realm_link {
    realm_table_key_t target_table;
    realm_obj_key_t target;
} realm_link_t;

typedef struct realm_object_id {
    uint8_t bytes[12];
} realm_object_id_t;

typedef struct realm_value {
    union {
        int64_t integer;
        bool boolean;
        realm_string_t string;
        realm_binary_t binary;
        realm_timestamp_t timestamp;
        float fnum;
        double dnum;
        realm_decimal128_t decimal128;
        realm_object_id_t object_id;

        realm_link_t link;

        char data[16];
    };
    realm_value_type_e type;
} realm_value_t;


/* Error types */
typedef struct realm_async_error realm_async_error_t;
typedef enum realm_errno {
    RLM_ERR_NONE = 0,
    RLM_ERR_UNKNOWN,
    RLM_ERR_OTHER_EXCEPTION,
    RLM_ERR_OUT_OF_MEMORY,
    RLM_ERR_NOT_CLONABLE,

    RLM_ERR_INVALIDATED_OBJECT,
    RLM_ERR_INVALID_PROPERTY,
    RLM_ERR_MISSING_PROPERTY_VALUE,
    RLM_ERR_PROPERTY_TYPE_MISMATCH,
    RLM_ERR_MISSING_PRIMARY_KEY,
    RLM_ERR_WRONG_PRIMARY_KEY_TYPE,
    RLM_ERR_MODIFY_PRIMARY_KEY,
    RLM_ERR_READ_ONLY_PROPERTY,
    RLM_ERR_PROPERTY_NOT_NULLABLE,
    RLM_ERR_INVALID_ARGUMENT,

    RLM_ERR_LOGIC,
    RLM_ERR_NO_SUCH_TABLE,
    RLM_ERR_TABLE_NAME_IN_USE,
    RLM_ERR_CROSS_TABLE_LINK_TARGET,
    RLM_ERR_DESCRIPTOR_MISMATCH,
    RLM_ERR_UNSUPPORTED_FILE_FORMAT_VERSION,
    RLM_ERR_MULTIPLE_SYNC_AGENTS,
    RLM_ERR_ADDRESS_SPACE_EXHAUSTED,
    RLM_ERR_MAXIMUM_FILE_SIZE_EXCEEDED,
    RLM_ERR_OUT_OF_DISK_SPACE,
    RLM_ERR_KEY_NOT_FOUND,
    RLM_ERR_COLUMN_NOT_FOUND,
    RLM_ERR_COLUMN_ALREADY_EXISTS,
    RLM_ERR_KEY_ALREADY_USED,
    RLM_ERR_SERIALIZATION_ERROR,
    RLM_ERR_INVALID_PATH_ERROR,
    RLM_ERR_DUPLICATE_PRIMARY_KEY_VALUE,

    RLM_ERR_INDEX_OUT_OF_BOUNDS,

    RLM_ERR_INVALID_QUERY_STRING,
    RLM_ERR_INVALID_QUERY,
    // ...
} realm_errno_e;

typedef enum realm_logic_error_kind {
    RLM_LOGIC_ERR_NONE = 0,
    RLM_LOGIC_ERR_STRING_TOO_BIG,
    // ...
} realm_logic_error_kind_e;

typedef struct realm_error {
    realm_errno_e error;
    realm_string_t message;
    union {
        int code;
        realm_logic_error_kind_e logic_error_kind;
    } kind;
} realm_error_t;

/* Schema types */

typedef enum realm_column_attr {
    // Values matching `realm::ColumnAttr`.
    RLM_COLUMN_ATTR_NONE = 0,
    RLM_COLUMN_ATTR_INDEXED = 1,
    RLM_COLUMN_ATTR_UNIQUE = 2,
    RLM_COLUMN_ATTR_RESERVED = 4,
    RLM_COLUMN_ATTR_STRONG_LINKS = 8,
    RLM_COLUMN_ATTR_NULLABLE = 16,
    RLM_COLUMN_ATTR_LIST = 32,
    RLM_COLUMN_ATTR_DICTIONARY = 64,
    RLM_COLUMN_ATTR_COLLECTION = 64 + 32,
} realm_column_attr_e;

typedef enum realm_property_type {
    // Values matching `realm::ColumnType`.
    RLM_PROPERTY_TYPE_INT = 0,
    RLM_PROPERTY_TYPE_BOOL = 1,
    RLM_PROPERTY_TYPE_STRING = 2,
    RLM_PROPERTY_TYPE_BINARY = 4,
    RLM_PROPERTY_TYPE_ANY = 6,
    RLM_PROPERTY_TYPE_TIMESTAMP = 8,
    RLM_PROPERTY_TYPE_FLOAT = 9,
    RLM_PROPERTY_TYPE_DOUBLE = 10,
    RLM_PROPERTY_TYPE_DECIMAL128 = 11,
    RLM_PROPERTY_TYPE_OBJECT = 12,
    RLM_PROPERTY_TYPE_LINKING_OBJECTS = 14,
    RLM_PROPERTY_TYPE_OBJECT_ID = 15,
} realm_property_type_e;

typedef enum realm_collection_type {
    RLM_COLLECTION_TYPE_NONE = 0,
    RLM_COLLECTION_TYPE_LIST = 1,
    RLM_COLLECTION_TYPE_SET = 2,
    RLM_COLLECTION_TYPE_DICTIONARY = 4,
} realm_collection_type_e;

typedef struct realm_property_info {
    realm_string_t name;
    realm_string_t public_name;
    realm_property_type_e type;
    realm_collection_type_e collection_type;

    realm_string_t link_target;
    realm_string_t link_origin_property_name;
    realm_col_key_t key;
    int flags;
} realm_property_info_t;

typedef struct realm_class_info {
    realm_string_t name;
    realm_string_t primary_key;
    size_t num_properties;
    size_t num_computed_properties;
    realm_table_key_t key;
    int flags;
} realm_class_info_t;

typedef enum realm_class_flags {
    RLM_CLASS_NORMAL = 0,
    RLM_CLASS_EMBEDDED = 1,
} realm_class_flags_e;

typedef enum realm_property_flags {
    RLM_PROPERTY_NORMAL = 0,
    RLM_PROPERTY_NULLABLE = 1,
    RLM_PROPERTY_PRIMARY_KEY = 2,
    RLM_PROPERTY_INDEXED = 4,
} realm_property_flags_e;


/* Notification types */
typedef struct realm_notification_token realm_notification_token_t;
typedef struct realm_object_changes realm_object_changes_t;
typedef struct realm_collection_changes realm_collection_changes_t;
typedef void (*realm_on_object_change_func_t)(void* userdata, const realm_object_changes_t*);
typedef void (*realm_on_collection_change_func_t)(void* userdata, const realm_collection_changes_t*);
typedef void (*realm_callback_error_func_t)(void* userdata, const realm_async_error_t*);

/* Scheduler types */
typedef void (*realm_scheduler_notify_func_t)(void* userdata);
typedef bool (*realm_scheduler_is_on_thread_func_t)(void* userdata);
typedef bool (*realm_scheduler_can_deliver_notifications_func_t)(void* userdata);
typedef void (*realm_scheduler_set_notify_callback_func_t)(void* userdata, void* callback_userdata,
                                                           realm_free_userdata_func_t, realm_scheduler_notify_func_t);
typedef realm_scheduler_t* (*realm_scheduler_default_factory_func_t)(void* userdata);

/* Sync types */
typedef void (*realm_sync_upload_completion_func_t)(void* userdata, realm_async_error_t*);
typedef void (*realm_sync_download_completion_func_t)(void* userdata, realm_async_error_t*);
typedef void (*realm_sync_connection_state_changed_func_t)(void* userdata, int, int);
typedef void (*realm_sync_session_state_changed_func_t)(void* userdata, int, int);
typedef void (*realm_sync_progress_func_t)(void* userdata, size_t transferred, size_t total);

/**
 * Get a string representing the version number of the Realm library.
 *
 * @return A null-terminated string.
 */
RLM_API const char* realm_get_library_version();

/**
 * Get individual components of the version number of the Realm library.
 *
 * @param out_major The major version number (X.0.0).
 * @param out_minor The minor version number (0.X.0).
 * @param out_patch The patch version number (0.0.X).
 * @param out_extra The extra version string (0.0.0-X).
 */
RLM_API void realm_get_library_version_numbers(int* out_major, int* out_minor, int* out_patch,
                                               const char** out_extra);

/**
 * Get the last error that happened on this thread.
 *
 * Errors are thread-local. Getting the error must happen on the same thread as
 * the call that caused the error to occur. The error is specific to the current
 * thread, and not the Realm instance for which the error occurred.
 *
 * Note: The error is not cleared by subsequent successful calls to this
 *       function, but it will be overwritten by subsequent failing calls to
 *       other library functions.
 *
 * Note: Calling this function does not clear the current last error.
 *
 * This function does not allocate any memory.
 *
 * @param err A pointer to a `realm_error_t` struct that will be populated
 *            with information about the last error, if there is one. May be
 *            NULL.
 * @return True if an error occurred.
 */
RLM_API bool realm_get_last_error(realm_error_t* err);

/**
 * Get information about an async error, potentially coming from another thread.
 *
 * This function does not allocate any memory.
 *
 * @param err A pointer to a `realm_error_t` struct that will be populated with
 *            information about the error. May not be NULL.
 * @see realm_get_last_error()
 */
RLM_API void realm_get_async_error(const realm_async_error_t* err, realm_error_t* out_err);

/**
 * Convert the last error to `realm_async_error_t`, which can safely be passed
 * between threads.
 *
 * Note: This function does not clear the last error.
 *
 * @return A non-null pointer if there was an error on this thread.
 * @see realm_get_last_error()
 * @see realm_get_async_error()
 * @see realm_clear_last_error()
 */
RLM_API realm_async_error_t* realm_get_last_error_as_async_error();

#if defined(__cplusplus)
/**
 * Rethrow the last exception.
 *
 * Note: This function does not have C linkage, because throwing across language
 * boundaries is undefined behavior. When called from C code, this should result
 * in a linker error. When called from C++, `std::rethrow_exception` will be
 * called to propagate the exception unchanged.
 */
RLM_EXPORT void realm_rethrow_last_error();
#endif // __cplusplus

/**
 * Clear the last error on the calling thread.
 *
 * Use this if the system has recovered from an error, e.g. by closing the
 * offending Realm and reopening it, freeing up resources, or similar.
 *
 * @return True if an error was cleared.
 */
RLM_API bool realm_clear_last_error();

/**
 * Free any Realm C Wrapper object.
 *
 * Note: Any pointer returned from a library function is owned by the caller.
 *       The caller is responsible for calling `realm_release()`. The only
 *       exception from this is C++ bridge functions that return `void*`, with
 *       the prefix `_realm`.
 *
 * Note: C++ destructors are typically `noexcept`, so it is likely that an
 *       exception will crash the process.
 *
 * @param ptr A pointer to a Realm C Wrapper object. May be NULL.
 */
RLM_API void realm_release(const void* ptr);

/**
 * Clone a Realm C Wrapper object.
 *
 * If the object is not clonable, this function fails with RLM_ERR_NOT_CLONABLE.
 *
 * @return A pointer to an object of the same type as the input, or NULL if
 *         cloning failed.
 */
RLM_API void* realm_clone(const void*);

/**
 * Return true if two API objects refer to the same underlying data. Objects
 * with different types are never equal.
 *
 * Note: This function cannot be used with types that have value semantics, only
 *       opaque types that have object semantics.
 *
 *    - `realm_t` objects are identical if they represent the same instance (not
 *      just if they represent the same file).
 *    - `realm_schema_t` objects are equal if the represented schemas are equal.
 *    - `realm_config_t` objects are equal if the configurations are equal.
 *    - `realm_object_t` objects are identical if they belong to the same realm
 *      and class, and have the same object key.
 *    - `realm_list_t` and other collection objects are identical if they come
 *      from the same object and property.
 *    - `realm_query_t` objects are never equal.
 *    - `realm_scheduler_t` objects are equal if they represent the same
 *      scheduler.
 *    - Query descriptor objects are equal if they represent equivalent
 *      descriptors.
 *    - `realm_async_error_t` objects are equal if they represent the same
 *      exception instance.
 *
 * This function cannot fail.
 */
RLM_API bool realm_equals(const void*, const void*);

/**
 * True if a Realm C Wrapper object is "frozen" (immutable).
 *
 * Objects, collections, and results can be frozen. For all other types, this
 * function always returns false.
 */
RLM_API bool realm_is_frozen(const void*);

/**
 * Allocate a new configuration with default options.
 */
RLM_API realm_config_t* realm_config_new();

/**
 * Set the path of the realm being opened.
 */
RLM_API bool realm_config_set_path(realm_config_t*, realm_string_t);

/**
 * Set the encryption key for the realm.
 *
 * The key must be either 64 bytes long or have length zero (in which case
 * encryption is disabled).
 */
RLM_API bool realm_config_set_encryption_key(realm_config_t*, realm_binary_t key);

/**
 * Set the schema object for this realm.
 *
 * This does not take ownership of the schema object, and it should be released
 * afterwards.
 *
 * @param schema The schema object. May be NULL if the realm is opened without a
 *               schema.
 */
RLM_API bool realm_config_set_schema(realm_config_t*, const realm_schema_t* schema);

/**
 * Set the schema version of the schema.
 */
RLM_API bool realm_config_set_schema_version(realm_config_t*, uint64_t version);

/**
 * Set the schema mode.
 */
RLM_API bool realm_config_set_schema_mode(realm_config_t*, realm_schema_mode_e);

/**
 * Set the migration callback.
 *
 * The migration function is called during a migration for schema modes
 * `RLM_SCHEMA_MODE_AUTOMATIC` and `RLM_SCHEMA_MODE_MANUAL`. The callback is
 * invoked with a realm instance before the migration and the realm instance
 * that is currently performing the migration.
 */
RLM_API bool realm_config_set_migration_function(realm_config_t*, realm_migration_func_t, void* userdata);

/**
 * Set the data initialization function.
 *
 * The callback is invoked the first time the schema is created, such that the
 * user can perform one-time initialization of the data in the realm.
 *
 * The realm instance passed to the callback is in a write transaction.
 */
RLM_API bool realm_config_set_data_initialization_function(realm_config_t*, realm_data_initialization_func_t,
                                                           void* userdata);

/**
 * Set the should-compact-on-launch callback.
 *
 * The callback is invoked the first time a realm file is opened in this process
 * to decide whether the realm file should be compacted.
 *
 * Note: If another process has the realm file open, it will not be compacted.
 */
RLM_API bool realm_config_set_should_compact_on_launch_function(realm_config_t*,
                                                                realm_should_compact_on_launch_func_t,
                                                                void* userdata);

/**
 * Disable file format upgrade on open (default: false).
 *
 * If a migration is needed to open the realm file with the provided schema, an
 * error is thrown rather than automatically performing the migration.
 */
RLM_API bool realm_config_set_disable_format_upgrade(realm_config_t*, bool);

/**
 * Automatically generated change notifications (default: true).
 */
RLM_API bool realm_config_set_automatic_change_notifications(realm_config_t*, bool);

/**
 * The scheduler which this realm should be bound to (default: NULL).
 *
 * If NULL, the realm will be bound to the default scheduler for the current thread.
 */
RLM_API bool realm_config_set_scheduler(realm_config_t*, const realm_scheduler_t*);

/**
 * Sync configuration for this realm (default: NULL).
 */
RLM_API bool realm_config_set_sync_config(realm_config_t*, realm_sync_config_t*);

/**
 * Force the realm file to be initialized as a synchronized realm, even if no
 * sync config is provided (default: false).
 */
RLM_API bool realm_config_set_force_sync_history(realm_config_t*, bool);

/**
 * Set the audit interface for the realm (unimplemented).
 */
RLM_API bool realm_config_set_audit_factory(realm_config_t*, void*);

/**
 * Maximum number of active versions in the realm file allowed before an
 * exception is thrown (default: UINT64_MAX).
 */
RLM_API bool realm_config_set_max_number_of_active_versions(realm_config_t*, size_t);

/**
 * Create a custom scheduler object from callback functions.
 *
 * @param userdata Pointer passed to all callbacks.
 * @param notify Function to trigger a call to the registered callback on the
 *               scheduler's event loop. This function must be thread-safe, or
 *               NULL, in which case the scheduler is considered unable to
 *               deliver notifications.
 * @param is_on_thread Function to return true if called from the same thread as
 *                     the scheduler. This function must be thread-safe.
 * @param can_deliver_notifications Function to return true if the scheduler can
 *                                  support `notify()`. This function does not
 *                                  need to be thread-safe.
 * @param set_notify_callback Function to accept a callback that will be invoked
 *                            by `notify()` on the scheduler's event loop. This
 *                            function does not need to be thread-safe.
 */
RLM_API realm_scheduler_t*
realm_scheduler_new(void* userdata, realm_free_userdata_func_t, realm_scheduler_notify_func_t notify,
                    realm_scheduler_is_on_thread_func_t is_on_thread,
                    realm_scheduler_can_deliver_notifications_func_t can_deliver_notifications,
                    realm_scheduler_set_notify_callback_func_t set_notify_callback);

/**
 * Create an instance of the default scheduler for the current platform,
 * normally confined to the calling thread.
 */
RLM_API realm_scheduler_t* realm_scheduler_make_default();

/**
 * Get the scheduler used by frozen realms. This scheduler does not support
 * notifications, and does not perform any thread checking.
 */
RLM_API const realm_scheduler_t* realm_scheduler_get_frozen();

/**
 * For platforms with no default scheduler implementation, register a factory
 * function which can produce custom schedulers.
 *
 * The provided callback may produce a scheduler by calling `realm_scheduler_new()`.
 */
RLM_API void realm_scheduler_set_default_factory(void* userdata, realm_free_userdata_func_t,
                                                 realm_scheduler_default_factory_func_t);

/**
 * Trigger a call to the registered notifier callback on the scheduler's event loop.
 *
 * This function is thread-safe.
 */
RLM_API void realm_scheduler_notify(realm_scheduler_t*);

/**
 * Returns true if the caller is currently running on the scheduler's thread.
 *
 * This function is thread-safe.
 */
RLM_API bool realm_scheduler_is_on_thread(const realm_scheduler_t*);

/**
 * Returns true if the scheduler is able to deliver notifications.
 *
 * A false return value may indicate that notifications are not applicable for
 * the scheduler, not implementable, or a temporary inability to deliver
 * notifications.
 *
 * This function is not thread-safe.
 */
RLM_API bool realm_scheduler_can_deliver_notifications(const realm_scheduler_t*);

/**
 * Set the callback that will be invoked by `realm_scheduler_notify()`.
 *
 * This function is not thread-safe.
 */
RLM_API bool realm_scheduler_set_notify_callback(realm_scheduler_t*, void* userdata, realm_free_userdata_func_t,
                                                 realm_scheduler_notify_func_t);


/**
 * Open a Realm file.
 *
 * @param config Realm configuration. If the Realm is already opened on another
 *               thread, validate that the given configuration is compatible
 *               with the existing one.
 * @return If successful, the Realm object. Otherwise, NULL.
 */
RLM_API realm_t* realm_open(const realm_config_t* config);

/**
 * Create a `realm_t*` from a `std::shared_ptr<Realm>*`.
 *
 * This is intended as a migration path for users of the C++ Object Store API.
 *
 * Call `realm_release()` on the returned `realm_t*` to decrement the refcount
 * on the inner `std::shared_ptr<Realm>`.
 *
 * @param pshared_ptr A pointer to an instance of `std::shared_ptr<Realm>`.
 * @param n Must be equal to `sizeof(std::shared_ptr<Realm>)`.
 * @return A `realm_t*` representing the same Realm object as the passed
 *         `std::shared_ptr<Realm>`.
 */
RLM_API realm_t* _realm_from_native_ptr(const void* pshared_ptr, size_t n);

/**
 * Forcibly close a Realm file.
 *
 * Note that this invalidates all Realm instances for the same path.
 *
 * The Realm will be automatically closed when the last reference is released,
 * including references to objects within the Realm.
 *
 * @return True if no exception occurred.
 */
RLM_API bool realm_close(realm_t*);

/**
 * True if the Realm file is closed.
 *
 * This function cannot fail.
 */
RLM_API bool realm_is_closed(realm_t*);

/**
 * Begin a write transaction for the Realm file.
 *
 * @return True if no exception occurred.
 */
RLM_API bool realm_begin_write(realm_t*);

/**
 * Return true if the realm is in a write transaction.
 *
 * This function cannot fail.
 */
RLM_API bool realm_is_writable(const realm_t*);

/**
 * Commit a write transaction.
 *
 * @return True if the commit succeeded and no exceptions were thrown.
 */
RLM_API bool realm_commit(realm_t*);

/**
 * Roll back a write transaction.
 *
 * @return True if the rollback succeeded and no exceptions were thrown.
 */
RLM_API bool realm_rollback(realm_t*);

/**
 * Refresh the view of the realm file.
 *
 * If another process or thread has made changes to the realm file, this causes
 * those changes to become visible in this realm instance.
 *
 * This calls `advance_read()` at the Core layer.
 *
 * @return True if the realm was successfully refreshed and no exceptions were
 *         thrown.
 */
RLM_API bool realm_refresh(realm_t*);

/**
 * Produce a frozen view of this realm.
 *
 * @return A non-NULL realm instance representing the frozen state.
 */
RLM_API realm_t* realm_freeze(realm_t*);

/**
 * Vacuum the free space from the realm file, reducing its file size.
 *
 * @return True if compaction was successful and no exceptions were thrown.
 */
RLM_API bool realm_compact(realm_t*, bool* did_compact);

/**
 * Create a new schema from classes and their properties.
 *
 * Note: This function does not validate the schema.
 *
 * Note: `realm_table_key_t` and `realm_col_key_t` values inside
 *       `realm_class_info_t` and `realm_property_info_t` are unused when
 *       defining the schema. Call `realm_get_schema()` to obtain the values for
 *       these fields in an open realm.
 *
 * @return True if allocation of the schema structure succeeded.
 */
RLM_API realm_schema_t* realm_schema_new(const realm_class_info_t* classes, size_t num_classes,
                                         const realm_property_info_t** class_properties);

/**
 * Get the schema for this realm.
 *
 * Note: The returned value is allocated by this function, so `realm_release()`
 *       must be called on it.
 */
RLM_API const realm_schema_t* realm_get_schema(const realm_t*);

/**
 * Get the `realm::Schema*` pointer for this realm.
 *
 * This is intended as a migration path for users of the C++ Object Store API.
 *
 * The returned value is owned by the `realm_t` instance, and must not be freed.
 */
RLM_API const void* _realm_get_schema_native(const realm_t*);

/**
 * Validate the schema.
 *
 * @return True if the schema passed validation. If validation failed,
 *         `realm_get_last_error()` will produce an error describing the
 *         validation failure.
 */
RLM_API bool realm_schema_validate(const realm_schema_t*);

/**
 * Return the number of classes in the Realm's schema.
 *
 * This cannot fail.
 */
RLM_API size_t realm_get_num_classes(const realm_t*);

/**
 * Get the table keys for classes in the schema.
 *
 * @param out_keys An array that will contain the keys of each class in the
 *                 schema. May be NULL, in which case `out_n` can be used to
 *                 determine the number of classes in the schema.
 * @param max The maximum number of keys to write to `out_keys`.
 * @param out_n The actual number of classes. May be NULL.
 * @return True if no exception occurred.
 */
RLM_API bool realm_get_class_keys(const realm_t*, realm_table_key_t* out_keys, size_t max, size_t* out_n);

/**
 * Find a by the name of @a name.
 *
 * @param name The name of the class.
 * @param out_found Set to true if the class was found and no error occurred.
 *                  Otherwise, false. May not be NULL.
 * @param out_class_info A pointer to a `realm_class_info_t` that will be
 *                       populated with information about the class. May be
 *                       NULL.
 * @return True if no exception occurred.
 */
RLM_API bool realm_find_class(const realm_t*, realm_string_t name, bool* out_found,
                              realm_class_info_t* out_class_info);

/**
 * Get the class with @a key from the schema.
 *
 * Passing an invalid @a key for this schema is considered an error.
 *
 * @param key The key of the class, as discovered by `realm_get_class_keys()`.
 * @param out_class_info A pointer to a `realm_class_info_t` that will be
 *                       populated with the information of the class. May be
 *                       NULL, though that's kind of pointless.
 * @return True if no exception occurred.
 */
RLM_API bool realm_get_class(const realm_t*, realm_table_key_t key, realm_class_info_t* out_class_info);

/**
 * Get the list of properties for the class with this @a key.
 *
 * @param out_properties A pointer to an array of `realm_property_info_t`, which
 *                       will be populated with the information about the
 *                       properties. To see all properties, the length of the
 *                       array should be at least the number of properties in
 *                       the class, as reported in the sum of persisted and
 *                       computed properties for the class. May be NULL, in
 *                       which case this function can be used to discover the
 *                       number of properties in the class.
 * @param max The maximum number of entries to write to `out_properties`.
 * @param out_n The actual number of properties written to `out_properties`.
 * @return True if no exception occurred.
 */
RLM_API bool realm_get_class_properties(const realm_t*, realm_table_key_t key, realm_property_info_t* out_properties,
                                        size_t max, size_t* out_n);

/**
 * Get the property keys for the class with this @a key.
 *
 * @param key The class key.
 * @param out_col_keys An array of property keys. May be NULL, in which case
 *                     this function can be used to discover the number of
 *                     properties for this class.
 * @param max The maximum number of keys to write to `out_col_keys`. Ignored if
 *            `out_col_keys == NULL`.
 * @param out_n The actual number of properties written to `out_col_keys` (if
 *              non-NULL), or number of properties in the class.
 **/
RLM_API bool realm_get_property_keys(const realm_t*, realm_table_key_t key, realm_col_key_t* out_col_keys, size_t max,
                                     size_t* out_n);


/**
 * Find a property by its column key.
 *
 * It is an error to pass a property @a key that is not present in this class.
 *
 * @param class_key The key of the class.
 * @param key The column key for the property.
 * @param out_property_info A pointer to a `realm_property_info_t` that will be
 *                          populated with information about the property.
 * @return True if no exception occurred.
 */
RLM_API bool realm_get_property(const realm_t*, realm_table_key_t class_key, realm_col_key_t key,
                                realm_property_info_t* out_property_info);

/**
 * Find a property by the internal (non-public) name of @a name.
 *
 * @param class_key The table key for the class.
 * @param name The name of the property.
 * @param out_found Will be set to true if the property was found. May not be
 *                  NULL.
 * @param out_property_info A pointer to a `realm_property_info_t` that will be
 *                          populated with information about the property. May
 *                          be NULL.
 * @return True if no exception occurred.
 */
RLM_API bool realm_find_property(const realm_t*, realm_table_key_t class_key, realm_string_t name, bool* out_found,
                                 realm_property_info_t* out_property_info);

/**
 * Find a property with the public name of @a name.
 *
 * @param class_key The table key for the class.
 * @param public_name The public name of the property.
 * @param out_found Will be set to true if the property was found. May not be
 *                  NULL.
 * @param out_property_info A pointer to a `realm_property_info_t` that will be
 *                          populated with information about the property. May
 *                          be NULL.
 * @return True if no exception occurred.
 */
RLM_API bool realm_find_property_by_public_name(const realm_t*, realm_table_key_t class_key,
                                                realm_string_t public_name, bool* out_found,
                                                realm_property_info_t* out_property_info);

/**
 * Find the primary key property for a class, if it has one.
 *
 * @param class_key The table key for this class.
 * @param out_found Will be set to true if the property was found. May not be
 *                  NULL.
 * @param out_property_info A property to a `realm_property_info_t` that will be
 *                          populated with information about the property, if it
 *                          was found. May be NULL.
 * @return True if no exception occurred.
 */
RLM_API bool realm_find_primary_key_property(const realm_t*, realm_table_key_t class_key, bool* out_found,
                                             realm_property_info_t* out_property_info);

/**
 * Get the number of objects in a table (class).
 *
 * @param out_count A pointer to a `size_t` that will contain the number of
 *                  objects, if successful.
 * @return True if the table key was valid for this realm.
 */
RLM_API bool realm_get_num_objects(const realm_t*, realm_table_key_t, size_t* out_count);

/**
 * Get an object with a particular object key.
 *
 * @param class_key The class key.
 * @param obj_key The key to the object. Passing a non-existent key is
 *                considered an error.
 * @return A non-NULL pointer if no exception occurred.
 */
RLM_API realm_object_t* realm_get_object(const realm_t*, realm_table_key_t class_key, realm_obj_key_t obj_key);

/**
 * Find an object with a particular primary key value.
 *
 * @param out_found A pointer to a boolean that will be set to true or false if
 *                  no error occurred.
 * @return A non-NULL pointer if the object was found and no exception occurred.
 */
RLM_API realm_object_t* realm_object_find_with_primary_key(const realm_t*, realm_table_key_t, realm_value_t pk,
                                                           bool* out_found);

/**
 * Create an object in a class without a primary key.
 *
 * @return A non-NULL pointer if the object was created successfully.
 */
RLM_API realm_object_t* realm_object_create(realm_t*, realm_table_key_t);

/**
 * Create an object in a class with a primary key.
 *
 * @return A non-NULL pointer if the object was created successfully.
 */
RLM_API realm_object_t* realm_object_create_with_primary_key(realm_t*, realm_table_key_t, realm_value_t pk);

/**
 * Delete a realm object.
 *
 * Note: This does not call `realm_release()` on the `realm_object_t` instance.
 *
 * @return True if no exception occurred.
 */
RLM_API bool realm_object_delete(realm_object_t*);

RLM_API realm_object_t* _realm_object_from_native_copy(const void* pobj, size_t n);
RLM_API realm_object_t* _realm_object_from_native_move(void* pobj, size_t n);
RLM_API void* _realm_object_get_native_ptr(realm_object_t*);

/**
 * True if this object still exists in the realm.
 *
 * This function cannot fail.
 */
RLM_API bool realm_object_is_valid(const realm_object_t*);

/**
 * Get the key for this object.
 *
 * This function cannot fail.
 */
RLM_API realm_obj_key_t realm_object_get_key(const realm_object_t* object);

/**
 * Get the table for this object.
 *
 * This function cannot fail.
 */
RLM_API realm_table_key_t realm_object_get_table(const realm_object_t* object);

/**
 * Get a `realm_link_t` representing a link to @a object.
 *
 * This function cannot fail.
 */
RLM_API realm_link_t realm_object_as_link(const realm_object_t* object);

/**
 * Subscribe to notifications for this object.
 *
 * @return A non-null pointer if no exception occurred.
 */
RLM_API realm_notification_token_t* realm_object_add_notification_callback(realm_object_t*, void* userdata,
                                                                           realm_free_userdata_func_t free,
                                                                           realm_on_object_change_func_t on_change,
                                                                           realm_callback_error_func_t on_error,
                                                                           realm_scheduler_t*);

/**
 * Get the value for a property.
 *
 * @return True if no exception occurred.
 */
RLM_API bool realm_get_value(const realm_object_t*, realm_col_key_t, realm_value_t* out_value);

/**
 * Get the values for several properties.
 *
 * This is provided as an alternative to calling `realm_get_value()` multiple
 * times in a row, which is particularly useful for language runtimes where
 * crossing the native bridge is comparatively expensive. In addition, it
 * eliminates some parameter validation that would otherwise be repeated for
 * each call.
 *
 * Example use cases:
 *
 *  - Extracting all properties of an object for serialization.
 *  - Converting an object to some in-memory representation.
 *
 * @param num_values The number of elements in @a properties and @a out_values.
 * @param properties The keys for the properties to fetch. May not be NULL.
 * @param out_values Where to write the property values. If an error occurs,
 *                   this array may only be partially initialized. May not be
 *                   NULL.
 * @return True if no exception occurs.
 */
RLM_API bool realm_get_values(const realm_object_t*, size_t num_values, const realm_col_key_t* properties,
                              realm_value_t* out_values);

/**
 * Set the value for a property.
 *
 * @param new_value The new value for the property.
 * @param is_default True if this property is being set as part of setting the
 *                   default values for a new object. This has no effect in
 *                   non-sync'ed realms.
 * @return True if no exception occurred.
 */
RLM_API bool realm_set_value(realm_object_t*, realm_col_key_t, realm_value_t new_value, bool is_default);

/**
 * Set the values for several properties.
 *
 * This is provided as an alternative to calling `realm_get_value()` multiple
 * times in a row, which is particularly useful for language runtimes where
 * crossing the native bridge is comparatively expensive. In addition, it
 * eliminates some parameter validation that would otherwise be repeated for
 * each call.
 *
 * Example use cases:
 *
 *  - Initializing a new object with default values.
 *  - Deserializing some in-memory structure into a realm object.
 *
 * This operation is "atomic"; if an exception occurs due to invalid input (such
 * as type mismatch, nullability mismatch, etc.), the object will remain
 * unmodified.
 *
 * @param num_values The number of elements in @a properties and @a values.
 * @param properties The keys of the properties to set. May not be NULL.
 * @param values The values to assign to the properties. May not be NULL.
 * @param is_default True if the properties are being set as part of setting
 *                   default values for a new object. This has no effect in
 *                   non-sync'ed realms.
 * @return True if no exception occurred.
 */
RLM_API bool realm_set_values(realm_object_t*, size_t num_values, const realm_col_key_t* properties,
                              const realm_value_t* values, bool is_default);

/**
 * Get a list instance for the property of an object.
 *
 * Note: It is up to the caller to call `realm_release()` on the returned list.
 *
 * @return A non-null pointer if no exception occurred.
 */
RLM_API realm_list_t* realm_get_list(realm_object_t*, realm_col_key_t);

/**
 * Create a `realm_list_t` from a pointer to a `realm::List`, copy-constructing
 * the internal representation.
 *
 * @param plist A pointer to an instance of `realm::List`.
 * @param n Must be equal to `sizeof(realm::List)`.
 * @return A non-null pointer if no exception occurred.
 */
RLM_API realm_list_t* _realm_list_from_native_copy(const void* plist, size_t n);

/**
 * Create a `realm_list_t` from a pointer to a `realm::List`, move-constructing
 * the internal representation.
 *
 * @param plist A pointer to an instance of `realm::List`.
 * @param n Must be equal to `sizeof(realm::List)`.
 * @return A non-null pointer if no exception occurred.
 */
RLM_API realm_list_t* _realm_list_from_native_move(void* plist, size_t n);

/**
 * Get the size of a list, in number of elements.
 *
 * This function may fail if the object owning the list has been deleted.
 *
 * @param out_size Where to put the list size. May be NULL.
 * @return True if no exception occurred.
 */
RLM_API bool realm_list_size(const realm_list_t*, size_t* out_size);

/**
 * Get the property that this list came from.
 *
 * @return True if no exception occurred.
 */
RLM_API bool realm_list_get_property(const realm_list_t*, realm_property_info_t* out_property_info);

/**
 * Get the value at @a index.
 *
 * @param out_value The resulting value, if no error occurred. May be NULL,
 *                  though nonsensical.
 * @return True if no exception occurred.
 */
RLM_API bool realm_list_get(const realm_list_t*, size_t index, realm_value_t* out_value);

/**
 * Set the value at @a index.
 *
 * @param value The value to set.
 * @return True if no exception occurred.
 */
RLM_API bool realm_list_set(realm_list_t*, size_t index, realm_value_t value);

/**
 * Insert @a value at @a index.
 *
 * @param value The value to insert.
 * @return True if no exception occurred.
 */
RLM_API bool realm_list_insert(realm_list_t*, size_t index, realm_value_t value);

/**
 * Erase the element at @a index.
 *
 * @return True if no exception occurred.
 */
RLM_API bool realm_list_erase(realm_list_t*, size_t index);

/**
 * Clear a list.
 *
 * @return True if no exception occurred.
 */
RLM_API bool realm_list_clear(realm_list_t*);

/**
 * Replace the contents of a list with values.
 *
 * This is equivalent to calling `realm_list_clear()`, and then
 * `realm_list_insert()` repeatedly.
 *
 * @return True if no exception occurred.
 */
RLM_API bool realm_list_assign(realm_list_t*, const realm_value_t* values, size_t num_values);

/**
 * Subscribe to notifications for this object.
 *
 * @return A non-null pointer if no exception occurred.
 */
RLM_API realm_notification_token_t* realm_list_add_notification_callback(realm_list_t*, void* userdata,
                                                                         realm_free_userdata_func_t free,
                                                                         realm_on_collection_change_func_t on_change,
                                                                         realm_callback_error_func_t on_error,
                                                                         realm_scheduler_t*);

/**
 * True if an object notification indicates that the object was deleted.
 *
 * This function cannot fail.
 */
RLM_API bool realm_object_changes_is_deleted(const realm_object_changes_t*);

/**
 * Get the number of properties that were modified in an object notification.
 *
 * This function cannot fail.
 */
RLM_API size_t realm_object_changes_get_num_modified_properties(const realm_object_changes_t*);

/**
 * Get the column keys for the properties that were modified in an object
 * notification.
 *
 * This function cannot fail.
 *
 * @param out_modified Where the column keys should be written. May be NULL.
 * @param max The maximum number of column keys to write.
 * @return The number of column keys written to @a out_modified, or the number
 *         of modified properties if @a out_modified is NULL.
 */
RLM_API size_t realm_object_changes_get_modified_properties(const realm_object_changes_t*,
                                                            realm_col_key_t* out_modified, size_t max);

/**
 * Get the number of various types of changes in a collection notification.
 *
 * @param out_num_deletions The number of deletions. May be NULL.
 * @param out_num_insertions The number of insertions. May be NULL.
 * @param out_num_modifications The number of modifications. May be NULL.
 * @param out_num_moves The number of moved elements. May be NULL.
 */
RLM_API void realm_collection_changes_get_num_changes(const realm_collection_changes_t*, size_t* out_num_deletions,
                                                      size_t* out_num_insertions, size_t* out_num_modifications,
                                                      size_t* out_num_moves);

/**
 * Get the number of various types of changes in a collection notification,
 * suitable for acquiring the change indices as ranges, which is much more
 * compact in memory than getting the individual indices when multiple adjacent
 * elements have been modified.
 *
 * @param out_num_deletions The number of deletions. May be NULL.
 * @param out_num_insertions The number of insertions. May be NULL.
 * @param out_num_modifications The number of modifications. May be NULL.
 * @param out_num_moves The number of moved elements. May be NULL.
 */
RLM_API void realm_collection_changes_get_num_ranges(const realm_collection_changes_t*,
                                                     size_t* out_num_deletion_ranges,
                                                     size_t* out_num_insertion_ranges,
                                                     size_t* out_num_modification_ranges, size_t* out_num_moves);

typedef struct realm_collection_move {
    size_t from;
    size_t to;
} realm_collection_move_t;

typedef struct realm_index_range {
    size_t from;
    size_t to;
} realm_index_range_t;

/**
 * Get the indices of changes in a collection notification.
 *
 * Note: For moves, every `from` index will also be present among deletions, and
 *       every `to` index will also be present among insertions.
 *
 * This function cannot fail.
 *
 * @param out_deletion_indices Where to put the indices of deleted elements
 *                             (*before* the deletion happened). May be NULL.
 * @param max_deletion_indices The max number of indices to write to @a
 *                             out_deletion_indices.
 * @param out_insertion_indices Where the put the indices of inserted elements
 *                              (*after* the insertion happened). May be NULL.
 * @param max_insertion_indices The max number of indices to write to @a
 *                              out_insertion_indices.
 * @param out_modification_indices Where to put the indices of modified elements
 *                                 (*before* any insertions or deletions of
 *                                 other elements). May be NULL.
 * @param max_modification_indices The max number of indices to write to @a
 *                                 out_modification_indices.
 * @param out_modification_indices_after Where to put the indices of modified
 *                                       elements (*after* any insertions or
 *                                       deletions of other elements). May be
 *                                       NULL.
 * @param max_modification_indices_after The max number of indices to write to
 *                                       @a out_modification_indices_after.
 * @param out_moves Where to put the pairs of indices of moved elements. May be
 *                  NULL.
 * @param max_moves The max number of pairs to write to @a out_moves.
 */
RLM_API void realm_collection_changes_get_changes(const realm_collection_changes_t*, size_t* out_deletion_indices,
                                                  size_t max_deletion_indices, size_t* out_insertion_indices,
                                                  size_t max_insertion_indices, size_t* out_modification_indices,
                                                  size_t max_modification_indices,
                                                  size_t* out_modification_indices_after,
                                                  size_t max_modification_indices_after,
                                                  realm_collection_move_t* out_moves, size_t max_moves);

RLM_API void realm_collection_changes_get_ranges(
    const realm_collection_changes_t*, realm_index_range_t* out_deletion_ranges, size_t max_deletion_ranges,
    realm_index_range_t* out_insertion_ranges, size_t max_insertion_ranges,
    realm_index_range_t* out_modification_ranges, size_t max_modification_ranges,
    realm_index_range_t* out_modification_ranges_after, size_t max_modification_ranges_after,
    realm_collection_move_t* out_moves, size_t max_moves);

RLM_API realm_set_t* _realm_set_from_native_copy(const void* pset, size_t n);
RLM_API realm_set_t* _realm_set_from_native_move(void* pset, size_t n);
RLM_API realm_set_t* realm_get_set(const realm_object_t*, realm_col_key_t);
RLM_API size_t realm_set_size(const realm_set_t*);
RLM_API bool realm_set_get(const realm_set_t*, size_t index, realm_value_t* out_value);
RLM_API bool realm_set_find(const realm_set_t*, realm_value_t value, size_t* out_index);
RLM_API bool realm_set_insert(realm_set_t*, realm_value_t value, size_t out_index);
RLM_API bool realm_set_erase(realm_set_t*, realm_value_t value, bool* out_erased);
RLM_API bool realm_set_clear(realm_set_t*);
RLM_API bool realm_set_assign(realm_set_t*, realm_value_t values, size_t num_values);
RLM_API realm_notification_token_t* realm_set_add_notification_callback(realm_object_t*, void* userdata,
                                                                        realm_free_userdata_func_t free,
                                                                        realm_on_collection_change_func_t on_change,
                                                                        realm_callback_error_func_t on_error,
                                                                        realm_scheduler_t*);


RLM_API realm_dictionary_t* _realm_dictionary_from_native_copy(const void* pdict, size_t n);
RLM_API realm_dictionary_t* _realm_dictionary_from_native_move(void* pdict, size_t n);
RLM_API realm_dictionary_t* realm_get_dictionary(const realm_object_t*, realm_col_key_t);
RLM_API size_t realm_dictionary_size(const realm_dictionary_t*);
RLM_API bool realm_dictionary_get(const realm_dictionary_t*, realm_value_t key, realm_value_t* out_value,
                                  bool* out_found);
RLM_API bool realm_dictionary_insert(realm_dictionary_t*, realm_value_t key, realm_value_t value, bool* out_inserted,
                                     size_t* out_index);
RLM_API bool realm_dictionary_erase(realm_dictionary_t*, realm_value_t key, bool* out_erased);
RLM_API bool realm_dictionary_clear(realm_dictionary_t*);
typedef realm_value_t realm_key_value_pair_t[2];
RLM_API bool realm_dictionary_assign(realm_dictionary_t*, const realm_key_value_pair_t* pairs, size_t num_pairs);
RLM_API realm_notification_token_t*
realm_dictionary_add_notification_callback(realm_object_t*, void* userdata, realm_free_userdata_func_t free,
                                           realm_on_collection_change_func_t on_change,
                                           realm_callback_error_func_t on_error, realm_scheduler_t*);

/**
 * Parse a query string and bind it to a table.
 *
 * If the query failed to parse, the parser error is available from
 * `realm_get_last_error()`.
 *
 * @param target_table The table on which to run this query.
 * @param query_string A string in the Realm Query Language, optionally
 *                     containing argument placeholders (`$0`, `$1`, etc.).
 * @param num_args The number of arguments for this query.
 * @param args A pointer to a list of argument values.
 * @return A non-null pointer if the query was successfully parsed and no
 *         exception occurred.
 */
RLM_API realm_query_t* realm_query_parse(const realm_t*, realm_table_key_t target_table, realm_string_t query_string,
                                         size_t num_args, const realm_value_t* args);

/**
 * Parse a query string and bind it to a list.
 *
 * If the query failed to parse, the parser error is available from
 * `realm_get_last_error()`.
 *
 * @param target_list The list on which to run this query.
 * @param query_string A string in the Realm Query Language, optionally
 *                     containing argument placeholders (`$0`, `$1`, etc.).
 * @param num_args The number of arguments for this query.
 * @param args A pointer to a list of argument values.
 * @return A non-null pointer if the query was successfully parsed and no
 *         exception occurred.
 */
RLM_API realm_query_t* realm_query_parse_for_list(const realm_list_t* target_list, realm_string_t query_string,
                                                  size_t num_args, const realm_value_t* values);

/**
 * Parse a query string and bind it to another query result.
 *
 * If the query failed to parse, the parser error is available from
 * `realm_get_last_error()`.
 *
 * @param target_results The results on which to run this query.
 * @param query_string A string in the Realm Query Language, optionally
 *                     containing argument placeholders (`$0`, `$1`, etc.).
 * @param num_args The number of arguments for this query.
 * @param args A pointer to a list of argument values.
 * @return A non-null pointer if the query was successfully parsed and no
 *         exception occurred.
 */
RLM_API realm_query_t* realm_query_parse_for_results(const realm_results_t* target_results,
                                                     realm_string_t query_string, size_t num_args,
                                                     const realm_value_t* values);

/**
 * Count the number of objects found by this query.
 */
RLM_API bool realm_query_count(const realm_query_t*, size_t* out_count);

/**
 * Return the first object matched by this query.
 *
 * Note: This function can only produce objects, not values. Use the
 *       `realm_results_t` returned by `realm_query_find_all()` to retrieve
 *       values from a list of primitive values.
 *
 * @param out_value Where to write the result, if any object matched the query.
 *                  May be NULL.
 * @param out_found Where to write whether the object was found. May be NULL.
 * @return True if no exception occurred.
 */
RLM_API bool realm_query_find_first(realm_query_t*, realm_value_t* out_value, bool* out_found);

/**
 * Produce a results object for this query.
 *
 * Note: This does not actually run the query until the results are accessed in
 *       some way.
 *
 * @return A non-null pointer if no exception occurred.
 */
RLM_API realm_results_t* realm_query_find_all(realm_query_t*);

/**
 * Delete all objects matched by a query.
 */
RLM_API bool realm_query_delete_all(const realm_query_t*);

/**
 * Count the number of results.
 *
 * If the result is "live" (not a snapshot), this may rerun the query if things
 * have changed.
 *
 * @return True if no exception occurred.
 */
RLM_API bool realm_results_count(realm_results_t*, size_t* out_count);

/**
 * Get the matching element at @a index in the results.
 *
 * If the result is "live" (not a snapshot), this may rerun the query if things
 * have changed.
 *
 * Note: The bound returned by `realm_results_count()` for a non-snapshot result
 *       is not a reliable way to iterate over elements in the result, because
 *       the result will be live-updated if changes are made in each iteration
 *       that may change the number of query results or even change the
 *       ordering. In other words, this method should probably only be used with
 *       snapshot results.
 *
 * @return True if no exception occurred (including out-of-bounds).
 */
RLM_API bool realm_results_get(realm_results_t*, size_t index, realm_value_t* out_value);

/**
 * Delete all objects in the result.
 *
 * If the result if "live" (not a snapshot), this may rerun the query if things
 * have changed.
 *
 * @return True if no exception occurred.
 */
RLM_API bool realm_results_delete_all(realm_results_t*);

/**
 * Return a snapshot of the results that never automatically updates.
 *
 * The returned result is suitable for use with `realm_results_count()` +
 * `realm_results_get()`.
 */
RLM_API realm_results_t* realm_results_snapshot(const realm_results_t*);

/**
 * Map the results into a frozen realm instance.
 */
RLM_API realm_results_t* realm_results_freeze(const realm_results_t*, const realm_t* frozen_realm);

/**
 * Compute the minimum value of a property in the results.
 *
 * @param out_min Where to write the result, if there were matching rows.
 * @param out_found Set to true if there are matching rows.
 * @return True if no exception occurred.
 */
RLM_API bool realm_results_min(realm_results_t*, realm_col_key_t, realm_value_t* out_min, bool* out_found);

/**
 * Compute the maximum value of a property in the results.
 *
 * @param out_max Where to write the result, if there were matching rows.
 * @param out_found Set to true if there are matching rows.
 * @return True if no exception occurred.
 */
RLM_API bool realm_results_max(realm_results_t*, realm_col_key_t, realm_value_t* out_max, bool* out_found);

/**
 * Compute the sum value of a property in the results.
 *
 * @param out_sum Where to write the result. Zero if no rows matched.
 * @param out_found Set to true if there are matching rows.
 * @return True if no exception occurred.
 */
RLM_API bool realm_results_sum(realm_results_t*, realm_col_key_t, realm_value_t* out_sum, bool* out_found);

/**
 * Compute the average value of a property in the results.
 *
 * Note: For numeric columns, the average is always converted to double.
 *
 * @param out_average Where to write the result.
 * @param out_found Set to true if there are matching rows.
 * @return True if no exception occurred.
 */
RLM_API bool realm_results_average(realm_results_t*, realm_col_key_t, realm_value_t* out_average, bool* out_found);

RLM_API realm_notification_token_t* realm_results_add_notification_callback(realm_results_t*, void* userdata,
                                                                            realm_free_userdata_func_t,
                                                                            realm_on_collection_change_func_t,
                                                                            realm_callback_error_func_t,
                                                                            realm_scheduler_t*);

#endif // REALM_H
