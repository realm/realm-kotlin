#ifndef __WRAPPER_H__
#define __WRAPPER_H__

#ifdef __cplusplus
extern "C"
{
#endif

    struct database;
    struct realm_object;
    struct realm_results;
    struct realm_list;
    typedef struct database database_t;
    typedef struct realm_object realm_object_t;
    typedef struct realm_results realm_results_t;
    typedef struct realm_list realm_list_t;
    

    database_t *create(const char *db_name, const char *schema);
    void destroy(database_t *db_ptr);

    // ***********      REALM      *********** //

    void begin_transaction(database_t *db_ptr);
    void commit_transaction(database_t *db_ptr);
    void cancel_transaction(database_t *db_ptr);

    // *********** OBJECT ACCESSOR *********** //

    // create an Object of type 'object_type' and return its pointer 
    realm_object_t* add_object(database_t *db_ptr, const char *object_type);
    void delete_object(database_t *db_ptr, realm_object_t* instance);

    int8_t object_get_bool(realm_object_t *obj_ptr, const char* property_name);
    int64_t object_get_int64(realm_object_t *obj_ptr, const char* property_name);
    double object_get_double(realm_object_t *obj_ptr, const char* property_name);
    const char* object_get_string(realm_object_t *obj_ptr, const char* property_name);
    realm_object_t* object_get_object(realm_object_t *obj_ptr, const char* property_name);
    realm_list_t* object_get_list(realm_object_t *obj_ptr, const char* property_name);
    realm_results_t* object_get_linkingobjects(realm_object_t *obj_ptr, const char *property_name);

    void object_set_bool(realm_object_t *obj_ptr, const char* property_name, int8_t value);
    void object_set_int64(realm_object_t *obj_ptr, const char* property_name, int64_t value);
    void object_set_double(realm_object_t *obj_ptr, const char* property_name, double value);
    void object_set_string(realm_object_t *obj_ptr, const char* property_name, const char* value);
    void object_set_object(realm_object_t *obj_ptr, const char* property_name, realm_object_t *value);

    // ***********       QUERY     *********** //
    realm_results_t* query(database_t *db_ptr, const char *object_type, const char* query);
    size_t realmresults_size(realm_results_t *realm_results_ptr);
    void realmresults_delete(realm_results_t *realm_results_ptr);
    realm_object_t* realmresults_get(realm_results_t *realm_results_ptr, const char* object_type, size_t index);

    // ***********    REALM LIST   *********** //
    size_t realmlist_size(realm_list_t *realm_list_ptr);
    void realmlist_clear(realm_list_t *realm_list_ptr);
    void realmlist_insert(realm_list_t *realm_list_ptr, realm_object_t *obj_ptr, size_t index);
    void realmlist_erase(realm_list_t *realm_list_ptr, size_t index);
    realm_object_t* realmlist_get(realm_list_t *realm_list_ptr, const char* object_type, size_t index);
    void realmlist_set(realm_list_t *realm_list_ptr, realm_object_t *obj_ptr, size_t index);


    // ***********    CALLBACK   *********** //
    typedef void (*CallbackFn)(const char *name);
    void register_listner(database_t *db_ptr, const char *object_type, CallbackFn callback);

#ifdef __cplusplus
}
#endif

#endif /* __WRAPPER_H__ */