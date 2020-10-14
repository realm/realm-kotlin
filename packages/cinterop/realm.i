%module realmc
%{
#include "realm/realm.h"
%}

%include "typemaps.i"

%pragma(java) moduleimports=%{
import io.realm.interop.LongPointerWrapper;
%}

%pragma(java) modulecode=%{
  // > Manual additions
  // < Manual addition
%}

// Primitive/built in type handling
typedef jstring realm_string_t;
%typemap(in) (jstring) {
	realm_string_t $1;
//	$1.data = jenv->GetStringUTFChars($arg, 0);
//	$1.size = strlen($1.data)
}

//typedef long* realm_config_t;
%typemap(jstype) realm_config_t* "long"
%typemap(javain) realm_config_t* "$javainput"
%typemap(javaout) realm_config_t* {
    return $jnicall;
}

//typedef long* realm_t;
%typemap(jstype) realm_t* "LongPointerWrapper"
%typemap(javain) realm_t* "$javainput.ptr"
%typemap(javaout) realm_t* {
    return new LongPointerWrapper($jnicall);
}

// Small collection of methods
const char* realm_get_library_version();
realm_config_t* realm_config_new();
bool realm_config_set_path(realm_config_t*, realm_string_t);
realm_t* realm_open(const realm_config_t* config);
bool realm_close(realm_t*);

// Custom auxiliary method
void custom(char* s);
// Implementation
%{
void custom(char* s) {
    printf("Hello, %s", s);
}
%}

typedef struct realm_string {
    const char* data;
    size_t size;
} realm_string_t;

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
