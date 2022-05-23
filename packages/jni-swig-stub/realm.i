%module(directors="1") realmc

%{
#include "realm.h"
#include <cstring>
#include <string>
#include "realm_api_helpers.h"

using namespace realm::jni_util;
%}

// TODO OPTIMIZATION
//  - Transfer "value semantics" objects in one go. Maybe custom serializer into byte buffers for all value types

%include "typemaps.i"
%include "stdint.i"
%include "arrays_java.i"

// We do not want to use BigInteger for uintt64_t as we are not expecting overflows
%apply int64_t {uint64_t};

// Manual imports in java module class
%pragma(java) moduleimports=%{
%}

// Manual additions to java module class
%pragma(java) modulecode=%{
    // Trigger loading of shared library when the swig wrapper is loaded
    static {
        // using https://developer.android.com/reference/java/lang/System#getProperties()
        if (System.getProperty("java.specification.vendor").contains("Android")) {
            System.loadLibrary("realmc");
        } else {
            // otherwise locate, using reflection, the dependency SoLoader and call load
            // (calling SoLoader directly will create a circular dependency with `jvmMain`)
            try {
                Class<?> classToLoad = Class.forName("io.realm.jvm.SoLoader");
                Object instance = classToLoad.newInstance();
                java.lang.reflect.Method loadMethod = classToLoad.getDeclaredMethod("load");
                loadMethod.invoke(instance);
            } catch (Exception e) {
                throw new RuntimeException("Couldn't load Realm native libraries", e);
            }
        }
    }
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

// This sets up a type map for all methods with the argument pattern of:
//    realm_void_user_completion_func_t, void* userdata, realm_free_userdata_func_t
// This will make Swig wrap methods taking this argument pattern into:
//  - a Java method that takes one argument of type `Object` (`jstype`) and passes this object on as `Object` to the native method (`jtype`+``javain`)
//  - a JNI method that takes a `jobject` (`jni`) that translates the incoming single argument into the actual three arguments of the C-API method (`in`)
%typemap(jstype) (realm_app_void_completion_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) "Object" ;
//%typemap(jtype, nopgcpp="1") (realm_app_void_completion_func_t, void* userdata, realm_free_userdata_func_t userdata_free) "Object" ;
%typemap(jtype) (realm_app_void_completion_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) "Object" ;
%typemap(javain) (realm_app_void_completion_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) "$javainput";
%typemap(jni) (realm_app_void_completion_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) "jobject";
%typemap(in) (realm_app_void_completion_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = app_complete_void_callback;
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

%apply (realm_app_void_completion_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) {
    (realm_app_user_completion_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free)
};
%typemap(in) (realm_app_user_completion_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<realm_app_user_completion_func_t>(app_complete_result_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}
// Reuse void callback typemap as template for `realm_on_realm_change_func_t`
%apply (realm_app_void_completion_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) {
(realm_on_realm_change_func_t, void* userdata, realm_free_userdata_func_t)
};
%typemap(in) (realm_on_realm_change_func_t, void* userdata, realm_free_userdata_func_t) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<realm_on_realm_change_func_t>(realm_changed_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}
// Reuse void callback typemap as template for `realm_on_realm_change_func_t`
%apply (realm_app_void_completion_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) {
(realm_on_schema_change_func_t, void* userdata, realm_free_userdata_func_t)
};
%typemap(in) (realm_on_schema_change_func_t, void* userdata, realm_free_userdata_func_t) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<realm_on_schema_change_func_t>(schema_changed_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// reuse void callback type as template for `realm_sync_download_completion_func_t`
%apply (realm_app_void_completion_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) {
(realm_sync_download_completion_func_t, void* userdata, realm_free_userdata_func_t)
};
%typemap(in) (realm_sync_download_completion_func_t, void* userdata, realm_free_userdata_func_t) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<realm_sync_download_completion_func_t>(transfer_completion_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// reuse void callback type as template for `realm_sync_upload_completion_func_t`
%apply (realm_app_void_completion_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) {
(realm_sync_upload_completion_func_t, void* userdata, realm_free_userdata_func_t)
};
%typemap(in) (realm_sync_upload_completion_func_t, void* userdata, realm_free_userdata_func_t) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<realm_sync_upload_completion_func_t>(transfer_completion_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// reuse void callback type as template for `realm_migration_func_t` function
%apply (realm_app_void_completion_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) {
    (realm_migration_func_t, void* userdata, realm_free_userdata_func_t userdata_free)
};
%typemap(in) (realm_migration_func_t, void* userdata, realm_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<realm_migration_func_t>(migration_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// reuse void callback type as template for `realm_should_compact_on_launch_func_t` function
%apply (realm_app_void_completion_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) {
(realm_should_compact_on_launch_func_t, void* userdata, realm_free_userdata_func_t userdata_free)
};
%typemap(in) (realm_should_compact_on_launch_func_t, void* userdata, realm_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<realm_should_compact_on_launch_func_t>(realm_should_compact_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// reuse void callback type as template for `realm_data_initialization_func_t` function
%apply (realm_app_void_completion_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) {
(realm_data_initialization_func_t, void* userdata, realm_free_userdata_func_t userdata_free)
};
%typemap(in) (realm_data_initialization_func_t, void* userdata, realm_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<realm_data_initialization_func_t>(realm_data_initialization_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

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
               realm_list_t*, realm_app_credentials_t*, realm_app_config_t*, realm_app_t*,
               realm_sync_client_config_t*, realm_user_t*, realm_sync_config_t*,
               realm_sync_session_t*, realm_http_completion_func_t, realm_http_transport_t*,
               realm_collection_changes_t*, realm_callback_token_t*,
               realm_flx_sync_subscription_t*, realm_flx_sync_subscription_set_t*,
               realm_flx_sync_mutable_subscription_set_t* };

// For all functions returning a pointer or bool, check for null/false and throw an error if
// realm_get_last_error returns true.
// To bypass automatic error checks define the function explicitly here before the type maps until
// we have a distinction (type map, etc.) in the C API that we can use for targeting the type map.
bool realm_object_is_valid(const realm_object_t*);

%{
bool throw_as_java_exception(JNIEnv *jenv) {
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
        return true;
    } else {
        return false;
    }
}
%}

%typemap(out) SWIGTYPE* {
    if (!result) {
        bool exception_thrown = throw_as_java_exception(jenv);
        if (exception_thrown) {
            // Return immediately if there was an error in which case the exception will be raised when returning to JVM
            return $null;
        }
    }
    *($1_type*)&jresult = result;
}

%typemap(out) bool {
    if (!result) {
        bool exception_thrown = throw_as_java_exception(jenv);
        if (exception_thrown) {
            // Return immediately if there was an error in which case the exception will be raised when returning to JVM
            return $null;
        }
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
%array_functions(realm_index_range_t, indexRangeArray);
%array_functions(realm_collection_move_t, collectionMoveArray);

// Work around issues with realm_size_t on Windows https://jira.mongodb.org/browse/RKOTLIN-332
%apply int64_t[] { size_t* };

// bool output parameter
%apply bool* OUTPUT { bool* out_found, bool* did_create, bool* did_delete_realm, bool* out_inserted };

// uint64_t output parameter for realm_get_num_versions
%apply int64_t* OUTPUT { uint64_t* out_versions_count };

// Enable passing uint8_t* parameters for realm_config_get_encryption_key and realm_config_set_encryption_key as Byte[]
%apply int8_t[] {uint8_t *key};
%apply int8_t[] {uint8_t *out_key};

// Enable passing output argument pointers as long[]
%apply int64_t[] {void **};
// Type map for int64_t has an erroneous cast, don't know how to fix it except with this
%typemap(in) void** ( jlong *jarr ){
    // Original
    %#if defined(__ANDROID__) && defined(__aarch64__) // Android arm64-v8a
        if (!SWIG_JavaArrayInLonglong(jenv, &jarr, (long **)&$1, $input)) return $null;
    %#elif defined(__ANDROID__) // Android armeabi-v7a, x86_64 and x86
        if (!SWIG_JavaArrayInLonglong(jenv, &jarr, (jlong **)&$1, $input)) return $null;
    %#elif defined(__aarch64__) // macos M1
        if (!SWIG_JavaArrayInLonglong(jenv, &jarr, (jlong **)&$1, $input)) return $null;
    %#else
        if (!SWIG_JavaArrayInLonglong(jenv, &jarr, (long long **)&$1, $input)) return $null;
    %#endif
}
%typemap(argout) void** {
    // Original
    %#if defined(__ANDROID__) && defined(__aarch64__)
        SWIG_JavaArrayArgoutLonglong(jenv, jarr$argnum, (long*)$1, $input);
    %#elif defined(__ANDROID__)
        SWIG_JavaArrayArgoutLonglong(jenv, jarr$argnum, (jlong*)$1, $input);
    %#elif defined(__aarch64__)
        SWIG_JavaArrayArgoutLonglong(jenv, jarr$argnum, (jlong *)$1, $input);
    %#else
        SWIG_JavaArrayArgoutLonglong(jenv, jarr$argnum, (long long *)$1, $input);
    %#endif
}
%apply void** {realm_object_t **, realm_list_t **, size_t*, realm_class_key_t*, realm_property_key_t*, realm_user_t**};

%apply uint32_t[] {realm_class_key_t*};

// Just generate constants for the enum and pass them back and forth as integers
%include "enumtypeunsafe.swg"
%javaconst(1);


// FIXME OPTIMIZE Support getting/setting multiple attributes. Ignored for now due to incorrect
//  type cast in Swig-generated wrapper for "const realm_property_key_t*" which is not cast
//  correctly to the underlying C-API method.
%ignore "realm_get_values";
%ignore "realm_set_values";
// Not yet available in library
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
// FIXME Has this moved? Maybe a merge error in the core master/sync merge
%ignore "realm_results_freeze";

// Still missing from sync implementation
%ignore "realm_sync_client_config_set_metadata_encryption_key";

// Swig doesn't understand __attribute__ so eliminate it
#define __attribute__(x)

%include "realm.h"
%include "src/main/jni/realm_api_helpers.h"

