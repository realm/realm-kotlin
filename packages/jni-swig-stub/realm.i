%module(directors="1") realmc

%{
#include "realm.h"
#include <cstring>
#include <string>
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
                jint ret = cached_jvm->AttachCurrentThread(&env, nullptr);
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

%typemap(out) SWIGTYPE* {
    if (!result) {
        realm_error_t error;
        if (realm_get_last_error(&error)) {
            std::string message("[" + std::to_string(error.error) + "]: " + error.message);
            realm_clear_last_error();
            // TODO API-SCHEMA Cache class lookup
            // FIXME Extract all error information and throw exceptions based on type
            //  https://github.com/realm/realm-kotlin/issues/70
            jclass clazz = (jenv)->FindClass("java/lang/RuntimeException");
            (jenv)->ThrowNew(clazz, message.c_str());
        }
    }
    *($1_type*)&jresult = result;
}
%typemap(out) bool {
    if (!result) {
        realm_error_t error;
        if (realm_get_last_error(&error)) {
            std::string message("[" + std::to_string(error.error) + "]: " + error.message);
            realm_clear_last_error();
            // TODO API-SCHEMA Cache class lookup
            // FIXME Extract all error information and throw exceptions based on type
            jclass clazz = (jenv)->FindClass("java/lang/RuntimeException");
            (jenv)->ThrowNew(clazz, message.c_str());
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

// int8_t parameter for realm_config_get_encryption_key and realm_config_set_encryption_key
%apply int8_t[] {uint8_t *key};
%apply int8_t[] {uint8_t *out_key};

// Just generate constants for the enum and pass them back and forth as integers
%include "enumtypeunsafe.swg"
%javaconst(1);

// Make swig types package private (as opposed to public by default) to ensure that we don't expose
// types outside the package
%typemap(javaclassmodifiers) SWIGTYPE "class";
%typemap(javaclassmodifiers) NotificationCallback "public class";
%typemap(javaclassmodifiers) enum SWIGTYPE "final class";

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

// Swig doesn't understand __attribute__ so eliminate it
#define __attribute__(x)

%include "realm.h"

%inline %{
realm_notification_token_t *
register_results_notification_cb(realm_results_t *results, jobject callback) {
    using namespace realm::jni_util;
    auto jenv = get_env();
    static jclass notification_class = jenv->FindClass("io/realm/interop/NotificationCallback");
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
register_object_notification_cb(realm_object_t *object, jobject callback) {
    using namespace realm::jni_util;
    auto jenv = get_env();
    static jclass notification_class = jenv->FindClass("io/realm/interop/NotificationCallback");
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

%}
