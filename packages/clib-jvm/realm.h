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
#define RLM_EXPORT __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
#define RLM_API "" 
#else
#define RLM_API  
#endif // __cplusplus


typedef struct shared_realm realm_t;
typedef struct realm_schema realm_schema_t;
typedef void (*realm_free_userdata_func_t)(void*);
typedef void* (*realm_clone_userdata_func_t)(const void*);

/* Accessor types */
typedef struct realm_object realm_object_t;
typedef struct realm_list realm_list_t;
typedef struct realm_set realm_set_t;
typedef struct realm_dictionary realm_dictionary_t;

/* Query types */
typedef struct realm_query realm_query_t;
typedef struct realm_parsed_query realm_parsed_query_t;
typedef struct realm_parsed_query_arguments realm_parsed_query_arguments_t;
typedef struct realm_descriptor_ordering realm_descriptor_ordering_t;
typedef struct realm_sort_descriptor realm_sort_descriptor_t;
typedef struct realm_distinct_descriptor realm_distinct_descriptor_t;
typedef struct realm_limit_descriptor realm_limit_descriptor_t;
typedef struct realm_include_descriptor realm_include_descriptor_t;
typedef struct realm_key_path_mapping realm_key_path_mapping_t;
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
typedef struct realm_notication_token realm_notification_token_t;
typedef struct realm_collection_changes realm_collection_changes_t;
typedef void (*realm_before_collection_change_func_t)(void* userdata, const realm_collection_changes_t*);
typedef void (*realm_after_collection_change_func_t)(void* userdata, const realm_collection_changes_t*);
typedef void (*realm_collection_change_error_func_t)(void* userdata, realm_async_error_t*);


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
const char* realm_get_library_version();

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
 * Rethrow the last exception.
 *
 * Note: This function does not have C linkage, because throwing across language
 * boundaries is undefined behavior. When called from C code, this should result
 * in a linker error. When called from C++, `std::rethrow_exception` will be
 * called to propagate the exception unchanged.
 */
#if defined(__cplusplus)
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
 * True if a Realm C Wrapper object is "frozen" (immutable).
 *
 * Objects, collections, and results can be frozen. For all other types, this
 * function always returns false.
 */
RLM_API bool realm_is_frozen(const void*);

RLM_API realm_config_t* realm_config_new();
RLM_API bool realm_config_set_path(realm_config_t*, realm_string_t);
RLM_API bool realm_config_set_encryption_key(realm_config_t*, realm_binary_t);
RLM_API bool realm_config_set_schema(realm_config_t*, const realm_schema_t*);
RLM_API bool realm_config_set_schema_version(realm_config_t*, uint64_t version);
RLM_API bool realm_config_set_schema_mode(realm_config_t*, realm_schema_mode_e);
RLM_API bool realm_config_set_migration_function(realm_config_t*, realm_migration_func_t, void* userdata);
RLM_API bool realm_config_set_data_initialization_function(realm_config_t*, realm_data_initialization_func_t,
                                                           void* userdata);
RLM_API bool realm_config_set_should_compact_on_launch_function(realm_config_t*,
                                                                realm_should_compact_on_launch_func_t,
                                                                void* userdata);
RLM_API bool realm_config_set_disable_format_upgrade(realm_config_t*, bool);
RLM_API bool realm_config_set_automatic_change_notifications(realm_config_t*, bool);
RLM_API bool realm_config_set_scheduler(realm_config_t*, void*);
RLM_API bool realm_config_set_sync_config(realm_config_t*, realm_sync_config_t*);
RLM_API bool realm_config_set_force_sync_history(realm_config_t*, bool);
RLM_API bool realm_config_set_audit_factory(realm_config_t*, void*);
RLM_API bool realm_config_set_max_number_of_active_versions(realm_config_t*, size_t);

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
RLM_API realm_object_t* realm_find_object_with_primary_key(const realm_t*, realm_table_key_t, realm_value_t pk,
                                                           bool* out_found);

/**
 * Create an object in a class without a primary key.
 *
 * @return A non-NULL pointer if the object was created successfully.
 */
RLM_API realm_object_t* realm_create_object(realm_t*, realm_table_key_t);

/**
 * Create an object in a class with a primary key.
 *
 * @return A non-NULL pointer if the object was created successfully.
 */
RLM_API realm_object_t* realm_create_object_with_primary_key(realm_t*, realm_table_key_t, realm_value_t pk);

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
 * Get the value for a property.
 *
 * @return True if no exception occurred.
 */
RLM_API bool realm_get_value(const realm_object_t*, realm_col_key_t, realm_value_t* out_value);

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
 * This function cannot fail.
 */
RLM_API size_t realm_list_size(const realm_list_t*);

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

RLM_API realm_dictionary_t* _realm_dictionary_from_native_copy(const void* pdict, size_t n);
RLM_API realm_dictionary_t* _realm_dictionary_from_native_move(void* pdict, size_t n);
RLM_API realm_dictionary_t* realm_get_dictionary(const realm_object_t*, realm_col_key_t);
RLM_API size_t realm_dictionary_size(const realm_dictionary_t*);
RLM_API bool realm_dictionary_get(const realm_dictionary_t*, realm_value_t* key, realm_value_t* value,
                                  bool* out_found);
RLM_API bool realm_dictionary_find(const realm_dictionary_t*, realm_value_t key, realm_value_t* out_value,
                                   bool* out_found);
RLM_API bool realm_dictionary_insert(realm_dictionary_t*, realm_value_t key, realm_value_t value, bool* out_inserted,
                                     size_t* out_index);
RLM_API bool realm_dictionary_erase(realm_dictionary_t*, realm_value_t key, bool* out_erased);
RLM_API bool realm_dictionary_clear(realm_dictionary_t*);
typedef realm_value_t realm_key_value_pair_t[2];
RLM_API bool realm_dictionary_assign(realm_dictionary_t*, const realm_key_value_pair_t* pairs, size_t num_pairs);

/**
 * Construct a new, empty query targeting @a table.
 *
 * @return A non-null pointer if no exception occurred.
 */
RLM_API realm_query_t* realm_query_new(const realm_t*, realm_table_key_t table);

/**
 * Construct a new query targeting the results of a previous query.
 *
 * @return A non-null pointer if no exception occurred.
 */
RLM_API realm_query_t* realm_query_new_with_results(realm_results_t*);

/**
 * Parse a query string.
 *
 * If the query failed to parse, the parser error is available from
 * `realm_get_last_error()`.
 *
 * @return A non-null pointer if the query was successfully parsed, and no
 *         exception occurred.
 */
RLM_API realm_parsed_query_t* realm_query_parse(realm_string_t);

RLM_API realm_descriptor_ordering_t* realm_new_descriptor_ordering();
RLM_API bool realm_descriptor_ordering_append_sort(realm_descriptor_ordering_t*, void*);
RLM_API bool realm_descriptor_ordering_append_distinct(realm_descriptor_ordering_t*,
                                                       const realm_distinct_descriptor_t*);
RLM_API bool realm_descriptor_ordering_append_limit(realm_descriptor_ordering_t*, const realm_limit_descriptor_t*);
RLM_API bool realm_descriptor_ordering_append_include(realm_descriptor_ordering_t*,
                                                      const realm_include_descriptor_t*);

RLM_API bool realm_apply_parsed_predicate(realm_query_t*, const realm_parsed_query_t*,
                                          const realm_parsed_query_arguments_t*, const realm_key_path_mapping_t*);
RLM_API bool realm_apply_parsed_descriptor_ordering(realm_descriptor_ordering_t*, const realm_t*,
                                                    realm_table_key_t target, const realm_parsed_query_t*,
                                                    const realm_key_path_mapping_t*);

RLM_API bool realm_query_count(const realm_query_t*, size_t* out_count);
RLM_API bool realm_query_find_first(realm_query_t*, realm_obj_key_t* out_key, bool* out_found);
RLM_API realm_results_t* realm_query_find_all(realm_query_t*);
RLM_API realm_results_t* realm_query_find_all_with_ordering(realm_query_t*, const realm_descriptor_ordering_t*);
RLM_API bool realm_query_delete_all(const realm_query_t*);

RLM_API bool realm_query_min(realm_query_t*, realm_col_key_t, realm_value_t* out_min);
RLM_API bool realm_query_max(realm_query_t*, realm_col_key_t, realm_value_t* out_max);
RLM_API bool realm_query_sum(realm_query_t*, realm_col_key_t, realm_value_t* out_sum);
RLM_API bool realm_query_average(realm_query_t*, realm_col_key_t, realm_value_t* out_average);

typedef enum realm_query_op {
    RLM_QUERY_AND,
    RLM_QUERY_OR,
    RLM_QUERY_NOT,
} realm_query_op_e;

typedef enum realm_query_cond {
    RLM_QUERY_EQUAL,
    RLM_QUERY_NOT_EQUAL,
    RLM_QUERY_GREATER,
    RLM_QUERY_GREATER_EQUAL,
    RLM_QUERY_LESS,
    RLM_QUERY_LESS_EQUAL,
    RLM_QUERY_BETWEEN,
    RLM_QUERY_CONTAINS,
    RLM_QUERY_LIKE,
    RLM_QUERY_BEGINS_WITH,
    RLM_QUERY_ENDS_WITH,
    RLM_QUERY_LINKS_TO,
} realm_query_cond_e;

typedef enum realm_query_cond_flags {
    RLM_QUERY_CASE_SENSITIVE = 1,
} realm_query_cond_flags_e;

RLM_API bool realm_query_push_op(realm_query_t*, realm_query_op_e);
RLM_API bool realm_query_begin_group(realm_query_t*);
RLM_API bool realm_query_end_group(realm_query_t*);
RLM_API bool realm_query_push_cond(realm_query_t*, realm_col_key_t, realm_query_cond_e, const realm_value_t* values,
                                   size_t num_values, int flags);
RLM_API bool realm_query_push_cond_properties(realm_query_t*, realm_col_key_t lhs, realm_query_cond_e,
                                              realm_col_key_t rhs, int flags);
RLM_API bool realm_query_push_query(realm_query_t*, realm_query_t*);
RLM_API bool realm_query_negate(realm_query_t*);

RLM_API size_t realm_results_count(realm_results_t*);
RLM_API realm_value_t realm_results_get(realm_results_t*, size_t index);
RLM_API bool realm_results_delete_all(realm_results_t*);
RLM_API bool realm_results_filter(realm_results_t*, const realm_query_t*);
RLM_API bool realm_results_sort(realm_results_t*, const realm_sort_descriptor_t*);
RLM_API bool realm_results_distinct(realm_results_t*, const realm_distinct_descriptor_t*);
RLM_API bool realm_results_limit(realm_results_t*, const realm_limit_descriptor_t*);
RLM_API bool realm_results_apply_ordering(realm_results_t*, const realm_descriptor_ordering_t*);
RLM_API realm_results_t* realm_results_snapshot(const realm_results_t*);
RLM_API realm_results_t* realm_results_freeze(const realm_results_t*, const realm_t* frozen_realm);
RLM_API bool realm_results_min(const realm_results_t*, realm_col_key_t, realm_value_t* out_min);
RLM_API bool realm_results_max(const realm_results_t*, realm_col_key_t, realm_value_t* out_max);
RLM_API bool realm_results_sum(const realm_results_t*, realm_col_key_t, realm_value_t* out_sum);
RLM_API bool realm_results_average(const realm_results_t*, realm_col_key_t, realm_value_t* out_average);

#if defined(RLM_ENABLE_OBJECT_ACCESSOR_API)
/**
 * Specify the update policy for `realm_create_or_update_object()`.
 */
typedef enum realm_update_policy {
    /**
     * If the object does not already exists, skip creation.
     */
    REALM_UPDATE_POLICY_SKIP = 0,

    /**
     * If given something that is not a managed object, create one.
     */
    REALM_UPDATE_POLICY_CREATE = 1,

    /**
     * If the input object already exists in the current realm, create a new one
     * and copy properties from the old object.
     */
    REALM_UPDATE_POLICY_COPY = 2,

    /**
     * If the object has a primary key, and an object with the same primary key
     * already exists, update the existing object with the new property values,
     * rather than return an error. Only meaningful when passed together with
     * `RLM_UPDATE_POLICY_CREATE`.
     */
    REALM_UPDATE_POLICY_UPDATE = 4,

    /**
     * When updating an object, compute the diff between the old and the new
     * object, and only set the properties that have different values.
     */
    REALM_UPDATE_POLICY_DIFF = 8,

    /**
     * Create an object regardless of whether it already exists. If it already
     * exists, copy properties from the old object into the new one.
     *
     * If the existing object has a primary key, this causes
     * `realm_object_create_or_update()` to fail with a primary key violation.
     */
    REALM_UPDATE_POLICY_FORCE_CREATE = REALM_UPDATE_POLICY_CREATE | REALM_UPDATE_POLICY_COPY,

    /**
     * TODO: Document
     */
    REALM_UPDATE_POLICY_UPDATE_ALL =
        REALM_UPDATE_POLICY_CREATE | REALM_UPDATE_POLICY_COPY | REALM_UPDATE_POLICY_UPDATE,
    /**
     * TODO: Document
     */
    REALM_UPDATE_POLICY_UPDATE_MODIFIED = REALM_UPDATE_POLICY_UPDATE_ALL | REALM_UPDATE_POLICY_DIFF,

    /**
     * TODO: Document
     */
    REALM_UPDATE_POLICY_SET_LINK = REALM_UPDATE_POLICY_CREATE,
} realm_update_policy_e;

/**
 * A list of key-value pairs used to populate an object with single values, list
 * values, set values, transitively created/updated objects (links, or embedded
 * objects), or dictionaries.
 */
typedef struct realm_property_value {
    /**
     * The kind of value being set.
     *
     * For normal property values, pass `RLM_COLLECTION_TYPE_NONE`.
     *
     * For lists or sets, pass `RLM_COLLECTION_TYPE_LIST` or
     * `RLM_COLLECTION_TYPE_SET`, and populate the `values` pointer.
     *
     * For transitively created objects or dictionaries, pass
     * `RLM_COLLECTION_TYPE_OBJECT` or `RLM_COLLECTION_TYPE_DICTIONARY` and
     * populate the `named_values` pointer.
     */
    realm_collection_type_e collection_type;

    /**
     * The name of the property being initialized.
     */
    realm_string_t name;

    union {
        /**
         * Unnamed values, such as the values in a list or set.
         */
        const realm_value_t* values;

        /**
         * Named values, such as the members of an object or entries in a
         * dictionary.
         */
        const struct realm_property_value* named_values;
    };

    /**
     * Number of values to set in this property. For non-collection values, this
     * is always 1.
     *
     * This number may be zero for a transitively created object with default
     * property values.
     */
    size_t num_values;
} realm_property_value_t;

/**
 * Create an object.
 *
 * This is equivalent to calling `realm_object_create_or_update()` with
 * `update_policy = RLM_UPDATE_POLICY_FORCE_CREATE`.
 *
 * @param type The class key.
 * @param values Initial values for the object.
 * @param num_values The number of initial values for the object.
 * @return A non-null pointer if no exception occurred and the object was
 *         created.
 */
RLM_API realm_object_t* realm_object_create_deep(realm_t*, realm_table_key_t type,
                                                 const realm_property_value_t* values, size_t num_values);

/**
 * Create or update an object.
 *
 * The @a update_policy specifies how to treat nested values in @a values.
 *
 * - `RLM_UPDATE_POLICY_SKIP`:
 * - `RLM_UPDATE_POLICY_FORCE_CREATE`:
 *
 * @param type The class key.
 * @param values Initial values for the object.
 * @param num_values The number of initial values for the object.
 * @param update_policy Specify an update/creation policy for this object and
 *                      transitive objects (i.e., objects reachable through
 *                      links).
 * @param current_obj A key to an existing object to be updated. May be NULL.
 */
RLM_API realm_object_t* realm_object_create_or_update_deep(realm_t*, realm_table_key_t type,
                                                           const realm_property_value_t* values, size_t num_values,
                                                           int update_policy, const realm_obj_key_t* current_obj);
#endif // RLM_ENABLE_OBJECT_ACCESSOR_API

#endif // REALM_H
