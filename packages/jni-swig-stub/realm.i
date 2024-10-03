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
    // This is only done on JVM. On Android, the native code is manually 
    // loaded using the RealmInitializer class.
    static {
        // using https://developer.android.com/reference/java/lang/System#getProperties()
        if (!System.getProperty("java.specification.vendor").contains("Android")) {
            // otherwise locate, using reflection, the dependency SoLoader and call load
            // (calling SoLoader directly will create a circular dependency with `jvmMain`)
            try {
                Class<?> classToLoad = Class.forName("io.realm.kotlin.jvm.SoLoader");
                @SuppressWarnings("deprecation")
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

%typemap(javafinalize) SWIGTYPE %{
@SuppressWarnings({"deprecation", "removal"})
protected void finalize() {
    delete();
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

// Reuse void callback typemap as template for callbacks returning a single api key
%apply (realm_app_void_completion_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) {
(realm_return_apikey_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free)
};
%typemap(in) (realm_return_apikey_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<realm_return_apikey_func_t>(app_apikey_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// Reuse void callback typemap as template for callbacks returning a string
%apply (realm_app_void_completion_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) {
(realm_return_string_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free)
};
%typemap(in) (realm_return_string_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<realm_return_string_func_t>(app_string_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// Reuse void callback typemap as template for callbacks returning a list of api keys
%apply (realm_app_void_completion_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) {
(realm_return_apikey_list_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free)
};
%typemap(in) (realm_return_apikey_list_func_t callback, void* userdata, realm_free_userdata_func_t userdata_free) {
    auto jenv = get_env(true);
    $1 = reinterpret_cast<realm_return_apikey_list_func_t>(app_apikey_list_callback);
    $2 = static_cast<jobject>(jenv->NewGlobalRef($input));
    $3 = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
}

// String handling
typedef jstring realm_string_t;
// Typemap used for passing realm_string_t into the C-API in situations where the string buffer
// only have to be live across the C-API call. The lifetime is controlled by the `tmp` JStringAccessor.
%typemap(in) realm_string_t (JStringAccessor tmp(jenv, NULL)){
    $1 = tmp = JStringAccessor(jenv, $arg);
}
// Clean up of jstring buffers are managed by the lifetime of the `tmp` JStringAccessor
%typemap(freearg) realm_string_t ""
// Typemap used for passing realm_string_t into the C-API in situations where the string buffer
// needs to be kept alive after returning from C-API call. This will copy the string buffer to the
// heap and this has to be explicitly freed at a later point.
// Currently just matching 'realm_string_t string' arguments to match realm_value_t.string = $input
%typemap(in) realm_string_t string {
    auto s = JStringAccessor(jenv, $arg);
    auto size = s.size();
    $1.size = size;
    $1.data = (char const *) (new char[size]);
    memcpy((char *)$1.data, (const char *)s.data(), size);
}
%typemap(out) (realm_string_t) "$result = to_jstring(jenv, StringData{$1.data, $1.size});"

// Type map to allow passing void* as Long
%typemap(jstype) void* "long"
%typemap(javain) void* "$javainput"
%typemap(javadirectorin) void* "$1"
%typemap(javaout) void* {
    return $jnicall;
}
// Reuse above type maps on other pointers too
%apply void* { realm_t*, realm_config_t*, realm_schema_t*, realm_object_t* , realm_query_t*,
               realm_results_t*, realm_notification_token_t*, realm_object_changes_t*,
               realm_list_t*, realm_collection_changes_t*, realm_callback_token_t*,
               realm_set_t*, realm_async_open_task_t*, realm_dictionary_t*,
               realm_dictionary_changes_t*, realm_scheduler_t*,
               realm_key_path_array_t* };

// For all functions returning a pointer or bool, check for null/false and throw an error if
// realm_get_last_error returns true.
// To bypass automatic error checks define the function explicitly here before the type maps until
// we have a distinction (type map, etc.) in the C API that we can use for targeting the type map.
bool realm_object_is_valid(const realm_object_t*);

%typemap(out) SWIGTYPE* {
    if (!result) {
        bool exception_thrown = throw_last_error_as_java_exception(jenv);
        if (exception_thrown) {
            // Return immediately if there was an error in which case the exception will be raised when returning to JVM
            return $null;
        }
    }
    *($1_type*)&jresult = result;
}

%typemap(out) bool {
    if (!result) {
        bool exception_thrown = throw_last_error_as_java_exception(jenv);
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
// Workaround for updated Swig behavior with 4.2.0
// https://github.com/swig/swig/commit/ecaa052f3d319834a66aaa07047be3662e5e52e2#diff-cd2fcc891412baae0fc46479c0870cbdd18133d06d68dcd216be8a37ecf77b37R10
%apply int { size_t nelements, size_t index }
%array_functions(realm_class_info_t, classArray);
%array_functions(realm_property_info_t, propertyArray);
%array_functions(realm_property_info_t*, propertyArrayArray);
%array_functions(realm_value_t, valueArray);
%array_functions(realm_index_range_t, indexRangeArray);
%array_functions(realm_collection_move_t, collectionMoveArray);
%array_functions(realm_query_arg_t, queryArgArray);
// Workaround for updated Swig behavior with 4.2.0
// https://github.com/swig/swig/commit/ecaa052f3d319834a66aaa07047be3662e5e52e2#diff-cd2fcc891412baae0fc46479c0870cbdd18133d06d68dcd216be8a37ecf77b37R10
%clear size_t nelements, size_t index;

// bool output parameter
%apply bool* OUTPUT { bool* out_found, bool* did_create, bool* did_delete_realm, bool* out_inserted,
                      bool* erased, bool* out_erased, bool* did_refresh, bool* did_run,
                      bool* found, bool* out_collection_was_cleared, bool* did_compact,
                      bool* collection_was_cleared, bool* out_collection_was_deleted,
                      bool* out_was_deleted};

// uint64_t output parameter for realm_get_num_versions
%apply int64_t* OUTPUT { uint64_t* out_versions_count };

// Enable passing uint8_t* as byte[]
%apply int8_t[] {uint8_t*}; //
%typemap(in) uint8_t* (jbyte *jarr) %{
    if(!SWIG_JavaArrayInSchar(jenv, &jarr, (signed char **)&$1, $input)) return $null;
%}
%typemap(out) uint8_t* %{
    $result = SWIG_JavaArrayOutSchar(jenv, (signed char *)result, arg1->size);
%}
%typemap(argout) uint8_t* %{
SWIG_JavaArrayArgoutSchar(jenv, jarr$argnum, (signed char *)$1, $input);
%}
%typemap(freearg) uint8_t*;

// Enable passing void** as long[]
%apply int64_t[] {void **};
%typemap(in) void ** (jlong *jarr) %{
    if(!SWIG_JavaArrayInLonglong(jenv, &jarr, (long long **)&$1, $input)) return $null;
%}
%typemap(out) void ** %{
    $result = SWIG_JavaArrayOutLonglong(jenv, (long long *)result, arg1->size);
%}
%typemap(argout) void ** %{
    SWIG_JavaArrayArgoutLonglong(jenv, jarr$argnum, (long long *)$1, $input);
%}
%typemap(freearg) void**;

// Reuse above typemap for void** (from apply int64_t[]) {void **}) to pass various pointer types as
// long[]
%apply void** {realm_object_t**, realm_list_t**, realm_class_key_t*, size_t*,
realm_property_key_t*, realm_user_t**, realm_set_t**, realm_results_t**};

// Enable passing uint64_t [2] parameter for realm_decimal128 as Long[]
%apply int64_t[] {uint64_t w[2]};
%typemap(in) uint64_t w[2] (jlong *jarr) %{
if(!SWIG_JavaArrayInLonglong(jenv, &jarr, (long long **)&$1, $input)) return $null;
%}
%typemap(argout) uint64_t w[2] %{
SWIG_JavaArrayArgoutLonglong(jenv, jarr$argnum, (long long *)$1, $input);
%}
%typemap(out) uint64_t w[2] %{
$result = SWIG_JavaArrayOutLonglong(jenv, (long long *)result, 2);
%}

%apply uint32_t[] {realm_class_key_t*};

// Just generate constants for the enum and pass them back and forth as integers
%include "enumtypeunsafe.swg"
%javaconst(1);

// Add support for String[] vs char** conversion
// See https://www.swig.org/Doc4.0/Java.html#Java_converting_java_string_arrays
// Begin --

/* This tells SWIG to treat char ** as a special case when used as a parameter
   in a function call */
%typemap(in) char ** (jint size) {
    int i = 0;
    size = jenv->GetArrayLength($input);
    $1 = (char **) malloc((size+1)*sizeof(char *));
    /* make a copy of each string */
    for (i = 0; i<size; i++) {
        jstring j_string = (jstring)jenv->GetObjectArrayElement($input, i);
        const char * c_string = jenv->GetStringUTFChars(j_string, 0);
        $1[i] = (char*) malloc((strlen(c_string)+1)*sizeof(char));
        strcpy($1[i], c_string);
        jenv->ReleaseStringUTFChars(j_string, c_string);
        jenv->DeleteLocalRef(j_string);
    }
    $1[i] = 0;
}

/* This cleans up the memory we malloc'd before the function call */
%typemap(freearg) char ** {
    int i;
    for (i=0; i<size$argnum-1; i++) {
        free($1[i]);
    }
    free($1);
}

/* This allows a C function to return a char ** as a Java String array */
%typemap(out) char ** {
    int i;
    int len=0;
    jstring temp_string;
    const jclass clazz = jenv->FindClass("java/lang/String");

    while ($1[len]) len++;
    jresult = jenv->NewObjectArray(len, clazz, NULL);
    /* exception checking omitted */
    for (i=0; i<len; i++) {
        temp_string = (*jenv)->NewStringUTF(*result++);
        jenv->SetObjectArrayElement(jresult, i, temp_string);
        jenv->DeleteLocalRef(temp_string);
    }
}

/* These 3 typemaps tell SWIG what JNI and Java types to use */
%typemap(jni) char ** "jobjectArray"
%typemap(jtype) char ** "String[]"
%typemap(jstype) char ** "String[]"

/* These 2 typemaps handle the conversion of the jtype to jstype typemap type
   and vice versa */
%typemap(javain) char ** "$javainput"
%typemap(javaout) char ** {
    return $jnicall;
}
// -- End

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
%ignore "_realm_set_from_native_copy"; // Not implemented in the C-API
%ignore "_realm_set_from_native_move"; // Not implemented in the C-API
%ignore "realm_set_assign"; // Not implemented in the C-API
%ignore "realm_dictionary_assign"; // Not implemented in the C-API
%ignore "_realm_dictionary_from_native_copy";
%ignore "_realm_dictionary_from_native_move";
%ignore "realm_query_delete_all";
%ignore "realm_results_snapshot";
// FIXME Has this moved? Maybe a merge error in the core master/sync merge
%ignore "realm_results_freeze";
// FIXME realm_websocket_endpoint::protocols are a `const chart **` which is causing problems with Swig. Find a proper typemap for it.
%ignore "protocols";

// TODO improve typemaps for freeing ByteArrays. At the moment we assume a realm_binary_t can only
//  be inside a realm_value_t and only those instances are freed properly until we refine their
//  corresponding typemap. Other usages will possible incur in leaking values, like in
//  realm_convert_with_path.
%ignore realm_convert_with_path;

%ignore "realm_object_add_notification_callback";
%ignore "realm_list_add_notification_callback";
%ignore "realm_set_add_notification_callback";
%ignore "realm_dictionary_add_notification_callback";
%ignore "realm_results_add_notification_callback";
%ignore "realm_sync_socket_callback_result_e";
%ignore "realm_sync_errno_connection_e";
%ignore "realm_sync_errno_session_e";
%ignore "realm_web_socket_errno_e";

// Swig doesn't understand __attribute__ so eliminate it
#define __attribute__(x)

%include "realm.h"
%include "realm/error_codes.h"
%include "src/main/jni/realm_api_helpers.h"
