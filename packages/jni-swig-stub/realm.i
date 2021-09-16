%module(directors="1") realmc

%{
#include "realm.h"
#include <cstring>
#include <string>
#include <thread>
%}

// FIXME MEMORY Verify finalizers, etc.
//  https://github.com/realm/realm-kotlin/issues/93
// TODO OPTIMIZATION
//  - Transfer "value semantics" objects in one go. Maybe custom serializer into byte buffers for all value types

%include "typemaps.i"
%include "stdint.i"
%include "arrays_java.i"

// caching JNIEnv
%{
static JavaVM *cached_jvm = 0;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved) {
    cached_jvm = jvm;
    return JNI_VERSION_1_2;
}

namespace realm {
namespace jni_util {
    static JNIEnv * get_env(bool attach_if_needed = false) {
        JNIEnv *env;
        jint rc = cached_jvm->GetEnv((void **)&env, JNI_VERSION_1_2);
        if (rc == JNI_EDETACHED) {
            if (attach_if_needed) {
                #if defined(__ANDROID__)
                    JNIEnv **jenv = &env;
                #else
                    void **jenv = (void **)&env;
                #endif
                jint ret = cached_jvm->AttachCurrentThread(jenv, nullptr);
                if (ret != JNI_OK) throw std::runtime_error("Could not attach JVM on thread ");
            } else {
                throw std::runtime_error("current thread not attached");
            }
        }
        if (rc == JNI_EVERSION)
            throw std::runtime_error("jni version not supported");
        return env;
    }
}
}

%}

// We do not want to use BigInteger for uintt64_t as we are not expecting overflows
%apply int64_t {uint64_t};

// Manual imports in java module class
%pragma(java) moduleimports=%{
%}

// Manual additions to java module class
%pragma(java) modulecode=%{
//  Manual addition
%}

// Helpers included directly in cpp file
%{
realm_string_t rlm_str(const char* str)
{
    return realm_string_t{str, std::strlen(str)};
}
std::string rlm_stdstr(realm_string_t val)
{
    return std::string(val.data, 0, val.size);
}
%}

// Primitive/built in type handling
typedef jstring realm_string_t;
// TODO OPTIMIZATION Optimize...maybe port JStringAccessor from realm-java
//%typemap(jtype) realm_string_t "String"
//%typemap(jstype) realm_string_t "String"
%typemap(in) (realm_string_t) "$1 = rlm_str(jenv->GetStringUTFChars($arg,0));"
%typemap(out) (realm_string_t) "$result = jenv->NewStringUTF(std::string($1.data, 0, $1.size).c_str());"

%typemap(jstype) void* "long"
%typemap(javain) void* "$javainput"
%typemap(javadirectorin) void* "$1"
%typemap(javaout) void* {
return $jnicall;
}

// Reuse above type maps on other pointers too
%apply void* { realm_t*, realm_config_t*, realm_schema_t*, realm_object_t* , realm_query_t*,
               realm_results_t*, realm_notification_token_t*, realm_object_changes_t*,
               realm_list_t* };

// For all functions returning a pointer or bool, check for null/false and throw an error if
// realm_get_last_error returns true.
// To bypass automatic error checks define the function explicitly here before the type maps until
// we have a distinction (type map, etc.) in the C API that we can use for targeting the type map.
bool realm_object_is_valid(const realm_object_t*);

%{
void throw_as_java_exception(JNIEnv *jenv) {
    realm_error_t error;
    if (realm_get_last_error(&error)) {
        std::string message("[" + std::to_string(error.error) + "]: " + error.message);
        realm_clear_last_error();

        // Invoke CoreErrorUtils.coreErrorAsThrowable() to retrieve an exception instance that
        // maps to the core error.
        jclass error_type_class = (jenv)->FindClass("io/realm/internal/interop/CoreErrorUtils");
        static jmethodID error_type_as_exception = (jenv)->GetStaticMethodID(error_type_class,
                                                                      "coreErrorAsThrowable",
                                                                      "(ILjava/lang/String;)Ljava/lang/Throwable;");
        jstring error_message = (jenv)->NewStringUTF(message.c_str());

        jobject exception = (jenv)->CallStaticObjectMethod(
                error_type_class,
                error_type_as_exception,
                jint(error.error),
                error_message);
        (jenv)->Throw(reinterpret_cast<jthrowable>(exception));
    }
}
%}

%typemap(out) SWIGTYPE* {
    if (!result) {
        throw_as_java_exception(jenv);
    }
    *($1_type*)&jresult = result;
}

%typemap(out) bool {
    if (!result) {
        throw_as_java_exception(jenv);
    }
    jresult = (jboolean)result;
}
// Just showcasing a wrapping concept. Maybe we should just go with `long` (apply void* as above)
//%typemap(jstype) realm_t* "LongPointerWrapper"
//%typemap(javain) realm_t* "$javainput.ptr()"
//%typemap(javaout) realm_t* {
//    return new LongPointerWrapper($jnicall);
//}

// Array wrappers to allow building (continuous allocated) arrays of the corresponding types from
// JVM
%include "carrays.i"
%array_functions(realm_class_info_t, classArray);
%array_functions(realm_property_info_t, propertyArray);
%array_functions(realm_property_info_t*, propertyArrayArray);
%array_functions(realm_value_t, valueArray);

// size_t output parameter
%inline %{
struct realm_size_t {
    size_t value;
};
%}

// size_t output parameter
// The below struct is used to pass size_t output parameters to Java.
%typemap(jni) (size_t* out_count) "long"
%typemap(jtype) (size_t* out_count) "long"
%typemap(jstype) (size_t* out_count) "realm_size_t"
%typemap(javain) (size_t* out_count) "realm_size_t.getCPtr($javainput)"
// The below type maps are used to convert realm_size_t into a pointer to the same struct in JNI
// The type maps are only applied to arguments are named exactly 'out_count'
%apply size_t* out_count { size_t* out_size };

// bool output parameter
%apply bool* OUTPUT { bool* out_found };

// uint64_t output parameter for realm_get_num_versions
%apply int64_t* OUTPUT { uint64_t* out_versions_count };

// Enable passing uint8_t* parameters for realm_config_get_encryption_key and realm_config_set_encryption_key as Byte[]
%apply int8_t[] {uint8_t *key};
%apply int8_t[] {uint8_t *out_key};

// Just generate constants for the enum and pass them back and forth as integers
%include "enumtypeunsafe.swg"
%javaconst(1);


// FIXME OPTIMIZE Support getting/setting multiple attributes. Ignored for now due to incorrect
//  type cast in Swig-generated wrapper for "const realm_property_key_t*" which is not cast
//  correctly to the underlying C-API method.
%ignore "realm_get_values";
%ignore "realm_set_values";
// Not yet available in library
%ignore "realm_config_set_sync_config";
%ignore "realm_update_schema_advanced";
%ignore "realm_config_set_audit_factory";
%ignore "_realm_get_schema_native";
%ignore "realm_find_primary_key_property";
%ignore "_realm_list_from_native_copy";
%ignore "_realm_list_from_native_move";
%ignore "realm_list_assign";
%ignore "_realm_set_from_native_copy";
%ignore "_realm_set_from_native_move";
%ignore "realm_get_set";
%ignore "realm_set_size";
%ignore "realm_set_get";
%ignore "realm_set_find";
%ignore "realm_set_insert";
%ignore "realm_set_erase";
%ignore "realm_set_clear";
%ignore "realm_set_assign";
%ignore "realm_set_add_notification_callback";
%ignore "_realm_dictionary_from_native_copy";
%ignore "_realm_dictionary_from_native_move";
%ignore "realm_get_dictionary";
%ignore "realm_dictionary_size";
%ignore "realm_dictionary_get";
%ignore "realm_dictionary_insert";
%ignore "realm_dictionary_erase";
%ignore "realm_dictionary_clear";
%ignore "realm_dictionary_assign";
%ignore "realm_dictionary_add_notification_callback";
%ignore "realm_query_delete_all";
%ignore "realm_results_snapshot";

// Sync methods not implemented yet
%ignore "realm_sync_config_new";
%ignore "realm_sync_config_set_session_stop_policy";
%ignore "realm_sync_config_set_error_handler";
%ignore "realm_sync_config_set_realm_encryption_key";
%ignore "realm_sync_config_set_client_validate_ssl";
%ignore "realm_sync_config_set_ssl_trust_certificate_path";
%ignore "realm_sync_config_set_ssl_verify_callback";
%ignore "realm_sync_config_set_cancel_waits_on_nonfatal_error";
%ignore "realm_sync_config_set_authorization_header_name";
%ignore "realm_sync_config_set_custom_http_header";
%ignore "realm_sync_config_set_recovery_directory_path";
%ignore "realm_sync_config_set_resync_mode";
%ignore "realm_sync_client_config_new";
%ignore "realm_sync_client_config_set_base_file_path";
%ignore "realm_sync_client_config_set_metadata_mode";
%ignore "realm_sync_client_config_set_encryption_key";
%ignore "realm_sync_client_config_set_reset_metadata_on_error";
%ignore "realm_sync_client_config_set_logger_factory";
%ignore "realm_sync_client_config_set_log_level";
%ignore "realm_sync_client_config_set_reconnect_mode";
%ignore "realm_sync_client_config_set_multiplex_sessions";
%ignore "realm_sync_client_config_set_user_agent_binding_info";
%ignore "realm_sync_client_config_set_user_agent_application_info";
%ignore "realm_sync_client_config_set_connect_timeout";
%ignore "realm_sync_client_config_set_connection_linger_time";
%ignore "realm_sync_client_config_set_ping_keepalive_period";
%ignore "realm_sync_client_config_set_pong_keepalive_period";
%ignore "realm_sync_client_config_set_fast_reconnect_limit";

// Swig doesn't understand __attribute__ so eliminate it
#define __attribute__(x)

%include "realm.h"

%inline %{
realm_notification_token_t *
register_results_notification_cb(realm_results_t *results, jobject callback) {
    using namespace realm::jni_util;
    auto jenv = get_env();
    static jclass notification_class = jenv->FindClass("io/realm/internal/interop/NotificationCallback");
    static jmethodID on_change_method = jenv->GetMethodID(notification_class, "onChange", "(J)V");

    return realm_results_add_notification_callback(
        results,
        // Use the callback as user data
        static_cast<jobject>(get_env()->NewGlobalRef(callback)),
        [](void *userdata) {
            get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
        },
        // change callback
        [](void *userdata, const realm_collection_changes_t *changes) {
            // TODO API-NOTIFICATION Consider catching errors and propagate to error callback
            //  like the C-API error callback below
            //  https://github.com/realm/realm-kotlin/issues/303
            auto jenv = get_env(true);
            if (jenv->ExceptionCheck()) {
                jenv->ExceptionDescribe();
                throw std::runtime_error("An unexpected Error was thrown from Java. See LogCat");
            }
            jenv->CallVoidMethod(static_cast<jobject>(userdata),
                                 on_change_method,
                                 reinterpret_cast<jlong>(changes));
        },
        []( void *userdata,
        const realm_async_error_t *async_error) {
            // TODO Propagate errors to callback
            //  https://github.com/realm/realm-kotlin/issues/303
        },
        // C-API currently uses the realm's default scheduler no matter what passed here
        NULL
    );
}

realm_notification_token_t *
register_list_notification_cb(realm_list_t *list, jobject callback) {
    using namespace realm::jni_util;
    auto jenv = get_env();
    static jclass notification_class = jenv->FindClass("io/realm/internal/interop/NotificationCallback");
    static jmethodID on_change_method = jenv->GetMethodID(notification_class, "onChange", "(J)V");

    return realm_list_add_notification_callback(
        list,
        // Use the callback as user data
        static_cast<jobject>(get_env()->NewGlobalRef(callback)),
        [](void *userdata) {
            get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
        },
        // change callback
        [](void *userdata, const realm_collection_changes_t *changes) {
            // TODO API-NOTIFICATION Consider catching errors and propagate to error callback
            //  like the C-API error callback below
            //  https://github.com/realm/realm-kotlin/issues/303
            auto jenv = get_env(true);
            if (jenv->ExceptionCheck()) {
                jenv->ExceptionDescribe();
                throw std::runtime_error("An unexpected Error was thrown from Java. See LogCat");
            }
            jenv->CallVoidMethod(static_cast<jobject>(userdata),
                                 on_change_method,
                                 reinterpret_cast<jlong>(changes));
        },
        []( void *userdata,
        const realm_async_error_t *async_error) {
            // TODO Propagate errors to callback
            //  https://github.com/realm/realm-kotlin/issues/303
        },
        // C-API currently uses the realm's default scheduler no matter what passed here
        NULL
    );
}

realm_notification_token_t *
register_object_notification_cb(realm_object_t *object, jobject callback) {
    using namespace realm::jni_util;
    auto jenv = get_env();
    static jclass notification_class = jenv->FindClass("io/realm/internal/interop/NotificationCallback");
    static jmethodID on_change_method = jenv->GetMethodID(notification_class, "onChange", "(J)V");

    return realm_object_add_notification_callback(
        object,
        // Use the callback as user data
        static_cast<jobject>(get_env()->NewGlobalRef(callback)),
        [](void *userdata) {
            get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
        },
        // change callback
        [](void *userdata, const realm_object_changes_t *changes) {
            // TODO API-NOTIFICATION Consider catching errors and propagate to error callback
            //  like the C-API error callback below
            //  https://github.com/realm/realm-kotlin/issues/303
            auto jenv = get_env(true);
            if (jenv->ExceptionCheck()) {
                jenv->ExceptionDescribe();
                throw std::runtime_error("An unexpected Error was thrown from Java. See LogCat");
            }
            jenv->CallVoidMethod(static_cast<jobject>(userdata),
                                 on_change_method,
                                 reinterpret_cast<jlong>(changes));
        },
        []( void *userdata,
        const realm_async_error_t *async_error) {
            // TODO Propagate errors to callback
            //  https://github.com/realm/realm-kotlin/issues/303
        },
        // C-API currently uses the realm's default scheduler no matter what passed here
        NULL
    );
}

inline void jni_check_exception(JNIEnv* jenv = realm::jni_util::get_env()) {
    if (jenv->ExceptionCheck()) {
        jenv->ExceptionDescribe();
        throw std::runtime_error("An unexpected Error was thrown from Java.");
    }
}

class CustomJVMScheduler{
    public:
        CustomJVMScheduler(jobject dispatchScheduler) {
            JNIEnv *jenv = realm::jni_util::get_env(true);
            jclass jvm_scheduler_class = jenv->FindClass("io/realm/internal/interop/JVMScheduler");
            m_notify_method = jenv->GetMethodID(jvm_scheduler_class, "notifyCore", "(JJ)V");
            m_jvm_dispatch_scheduler = jenv->NewGlobalRef(dispatchScheduler);
        }

        ~CustomJVMScheduler() {
            realm::jni_util::get_env(true)->DeleteGlobalRef(m_jvm_dispatch_scheduler);
        }

        realm_scheduler_t* build() {
            return realm_scheduler_new(
                    this /*void* userdata*/,
            [](
            void *userdata) { /*realm_free_userdata_func_t*/ //TODO this doesn't seem to be invoked (maybe on closing the Realm)
                delete reinterpret_cast<CustomJVMScheduler * >(userdata);
            },
            [](void *userdata) { /*realm_scheduler_notify_func_t notify*/
                reinterpret_cast<CustomJVMScheduler * >(userdata)->notify();
            },
            [](void *userdata) -> bool
            { /*realm_scheduler_is_on_thread_func_t is_on_thread*/
                return reinterpret_cast<CustomJVMScheduler * >(userdata)->is_on_thread();
            },
            [](const void *userdata1,
            const void *userdata2) -> bool
            {/*realm_scheduler_is_same_as_func_t*/
                return  (userdata1 == userdata2);
            },
            [](void *userdata) -> bool
            {/*realm_scheduler_can_deliver_notifications_func_t can_deliver_notifications*/
                return true;
            },
            [](void *userdata,
            void *callback_userdata,
                    realm_free_userdata_func_t /* TODO finds out what's this freeing */,
            realm_scheduler_notify_func_t
            core_notif_function) {/*realm_scheduler_set_notify_callback_func_t set_notify_callback*/
                auto scheduler = reinterpret_cast<CustomJVMScheduler * >(userdata);
                // it's important to set the thread id here and not when constructing since the RealmConfiguration
                // could be reused to open a Realm (previously closed) in a new thread. Setting it in the ctor will
                // keep the old thread id cached which will inevitably causes a "[6]: Realm accessed from incorrect thread" exception
                scheduler->m_id = std::this_thread::get_id();
                scheduler->m_callback_userdata = callback_userdata;
                scheduler->m_core_notif_function = core_notif_function;
            });
        }

    private:
        std::thread::id m_id;
        jmethodID m_notify_method;
        jobject m_jvm_dispatch_scheduler;
        void* m_callback_userdata;
        realm_scheduler_notify_func_t m_core_notif_function;

        void notify() {
            auto jenv = realm::jni_util::get_env(true);
            jni_check_exception(jenv);
            jenv->CallVoidMethod(m_jvm_dispatch_scheduler, m_notify_method,
                                 reinterpret_cast<jlong>(m_core_notif_function),
                                 reinterpret_cast<jlong>(m_callback_userdata));
        }

        bool is_on_thread() {
            return m_id == std::this_thread::get_id();
        }
};

void register_realm_scheduler(jlong config_ptr, jobject dispatchScheduler) {
    CustomJVMScheduler * jvmScheduler = new CustomJVMScheduler(dispatchScheduler);
    realm_scheduler_t *scheduler = jvmScheduler->build();
    realm_config_set_scheduler(reinterpret_cast<realm_config_t * >(config_ptr), scheduler);
}

void invoke_core_notify_callback(jlong core_notif_function, jlong callback_userdata) {
    realm_scheduler_notify_func_t notify =  reinterpret_cast<realm_scheduler_notify_func_t>(core_notif_function);
    notify(reinterpret_cast<void*>(callback_userdata));
}

%}
