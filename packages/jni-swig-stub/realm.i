%module realmc
%{
#include "realm/realm.h"
#include <cstring>
#include <string>
%}

// TODO
//  - Memory management
//  - Optimization
//    - Transfer "value semantics" objects in one go. Maybe custom serializer into byte buffers for all value types

%include "typemaps.i"
%include "stdint.i"
%include "arrays_java.i"

// Manual imports in java module class
%pragma(java) moduleimports=%{
import io.realm.interop.LongPointerWrapper;
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
// FIXME Optimize...maybe port JStringAccessor from realm-
//%typemap(jtype) realm_string_t "String"
//%typemap(jstype) realm_string_t "String"
%typemap(in) (realm_string_t) "$1 = rlm_str(jenv->GetStringUTFChars($arg,0));"
%typemap(out) (realm_string_t) "$result = jenv->NewStringUTF(std::string($1.data, 0, $1.size).c_str());"

%typemap(jstype) void* "long"
%typemap(javain) void* "$javainput"
%typemap(javaout) void* {
return $jnicall;
}

// Reuse above type maps on other pointers too
%apply void* { realm_t*, realm_config_t*, realm_schema_t*, realm_object_t* , realm_query_t* };
// For all functions returning a pointer, check for null and throw an error
// FIXME Maybe too general?
%typemap(out) SWIGTYPE* {
    if (!result) {
        realm_error_t error;
        // FIXME Check return value
        realm_get_last_error(&error);
        jclass clazz = (jenv)->FindClass("java/lang/RuntimeException");
        (jenv)->ThrowNew(clazz, rlm_stdstr(error.message).c_str());
        return $null;
    } else {
        *($1_type*)&jresult = result;
    }
}
// FIXME Just showcasing a wrapping concept. Maybe we should just go with `long` (apply void* as above)
//%typemap(jstype) realm_t* "LongPointerWrapper"
//%typemap(javain) realm_t* "$javainput.ptr()"
//%typemap(javaout) realm_t* {
//    return new LongPointerWrapper($jnicall);
//}

// Array wrappers to allow building (continuous allocated) arrays of the corresponding types from
%include "carrays.i"
%array_functions(realm_class_info_t, classArray);
%array_functions(realm_property_info_t, propertyArray);
%array_functions(realm_property_info_t*, propertyArrayArray);

// size_t output parameter
struct realm_size_t {
    size_t value;
};
%{
struct realm_size_t {
    size_t value;
};
%}
%typemap(jni) (size_t* out_count) "long"
%typemap(jtype) (size_t* out_count) "long"
%typemap(jstype) (size_t* out_count) "realm_size_t"
%typemap(javain) (size_t* out_count) "realm_size_t.getCPtr($javainput)"

// bool output parameter
%apply bool* OUTPUT { bool* out_found };

// Just generate constants for the enum and pass them back and forth as integers
%include "enumtypeunsafe.swg"
%javaconst(1);
// Wrap and throw on error indication
// FIXME Find a way to apply this by pattern; alternatively do an out-typemap for bool (but don't know that can be done selectively either)
%exception realm_find_class {
        bool result = $action
        if (!result) {
            realm_error_t error;
            // FIXME Check return value
            realm_get_last_error(&error);
            jclass clazz = (jenv)->FindClass("java/lang/RuntimeException");
            (jenv)->ThrowNew(clazz, rlm_stdstr(error.message).c_str());
            return $null;
        }
}

// Make swig types package private (as opposed to public by default) to ensure that we don't expose
// types outside the package
%typemap(javaclassmodifiers) SWIGTYPE "class";
%typemap(javaclassmodifiers) enum SWIGTYPE "final class";

// Not yet available in library
%ignore "realm_get_async_error";
%ignore "realm_get_last_error_as_async_error";
%ignore "realm_config_set_encryption_key";
%ignore "realm_config_set_disable_format_upgrade";
%ignore "realm_config_set_sync_config";
%ignore "realm_config_set_force_sync_history";
%ignore "realm_config_set_audit_factory";
%ignore "realm_is_closed";
%ignore "realm_is_writable";
%ignore "_realm_get_schema_native";
%ignore "realm_find_primary_key_property";
%ignore "realm_object_get_table";
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
%ignore "realm_results_freeze";

// Swig doesn't understand __attribute__ so eliminate it
#define __attribute__(x)

%include "realm.h"
