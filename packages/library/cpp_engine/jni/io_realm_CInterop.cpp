#include "io_realm_CInterop.h"
#include <iostream>
#include <android/log.h>
#include <realm.hpp>
#include "../wrapper.h"

using namespace realm;
using realm::SharedGroupOptions;

JNIEXPORT jlong JNICALL Java_io_realm_CInterop_JNI_1initTmpDir
  (JNIEnv *env, jobject, jstring tmpDir) {
    const char *tmp_dir = env->GetStringUTFChars(tmpDir, 0);

    __android_log_print(ANDROID_LOG_VERBOSE, "REALM_JNI", "Java_io_realm_CInterop_JNI_1initTmpDir tmp_dir = %s", tmp_dir);

    SharedGroupOptions::set_sys_tmp_dir(std::string(tmp_dir));
    
    env->ReleaseStringUTFChars(tmpDir, tmp_dir);
  }

JNIEXPORT jlong JNICALL Java_io_realm_CInterop_JNI_1openRealm
  (JNIEnv *env, jobject, jstring path, jstring schema) {
    const char *realm_path = env->GetStringUTFChars(path, 0);
    const char *realm_schema = env->GetStringUTFChars(schema, 0);

    __android_log_print(ANDROID_LOG_VERBOSE, "REALM_JNI", "Java_io_realm_CInterop_JNI_1openRealm path: = %s schema = %s", realm_path, realm_schema);

    database_t* db_pointer = create(realm_path, realm_schema);

    env->ReleaseStringUTFChars(path, realm_path);
    env->ReleaseStringUTFChars(schema, realm_schema);
    
    jlong ret = reinterpret_cast<jlong>(db_pointer);
    return ret;
  }


JNIEXPORT jlong JNICALL Java_io_realm_CInterop_JNI_1addObject
  (JNIEnv *env, jobject, jlong dbPointer, jstring tableName) {
    const char *table_name = env->GetStringUTFChars(tableName, 0);

    __android_log_print(ANDROID_LOG_VERBOSE, "REALM_JNI", "Java_io_realm_CInterop_JNI_1addObject table_name: = %s", table_name);

    realm_object_t* object_pointer = add_object(reinterpret_cast<database_t*>(dbPointer), table_name);

    env->ReleaseStringUTFChars(tableName, table_name);

    return reinterpret_cast<jlong>(object_pointer);
  }


JNIEXPORT void JNICALL Java_io_realm_CInterop_JNI_1beginTransaction
  (JNIEnv *, jobject, jlong dbPointer) {
    __android_log_print(ANDROID_LOG_VERBOSE, "REALM_JNI", "Java_io_realm_CInterop_JNI_1beginTransaction");
    database_t* db_pointer = reinterpret_cast<database_t*>(dbPointer);
    begin_transaction(db_pointer);
  }


JNIEXPORT void JNICALL Java_io_realm_CInterop_JNI_1commitTransaction
  (JNIEnv *, jobject, jlong dbPointer) {
    __android_log_print(ANDROID_LOG_VERBOSE, "REALM_JNI", "Java_io_realm_CInterop_JNI_1commitTransaction");
    commit_transaction(reinterpret_cast<database_t*>(dbPointer));
  }

JNIEXPORT void JNICALL Java_io_realm_CInterop_JNI_1cancelTransaction
  (JNIEnv *, jobject, jlong dbPointer) {
    __android_log_print(ANDROID_LOG_VERBOSE, "REALM_JNI", "Java_io_realm_CInterop_JNI_1cancelTransaction");
    cancel_transaction(reinterpret_cast<database_t*>(dbPointer));
  }

JNIEXPORT jlong JNICALL Java_io_realm_CInterop_JNI_1realmresultsQuery
  (JNIEnv *env, jobject, jlong dbPointer, jstring tableName, jstring _query) {
    const char *table_name = env->GetStringUTFChars(tableName, 0);
    const char *table_query = env->GetStringUTFChars(_query, 0);

    __android_log_print(ANDROID_LOG_VERBOSE, "REALM_JNI", "Java_io_realm_CInterop_JNI_1realmresultsQuery table_name = %s query = %s", table_name, table_query);
    realm_results_t* query_pointer = query(reinterpret_cast<database_t*>(dbPointer), table_name, table_query);

    env->ReleaseStringUTFChars(tableName, table_name);
    env->ReleaseStringUTFChars(_query, table_query);

    return reinterpret_cast<jlong>(query_pointer);
    // return reinterpret_cast<jlong>(nullptr);
  }

/*
 * Class:     io_realm_CInterop
 * Method:    JNI_objectGetString
 * Signature: (JLjava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_io_realm_CInterop_JNI_1objectGetString
  (JNIEnv *env, jobject, jlong objPointer, jstring propertyName) {
    
    //TODO use wrapper JStringAcessor to handle basic string operation
    const char *property_name = env->GetStringUTFChars(propertyName, 0);
    const char* property_value = object_get_string(reinterpret_cast<realm_object_t*>(objPointer), property_name);

    __android_log_print(ANDROID_LOG_VERBOSE, "REALM_JNI", "Java_io_realm_CInterop_JNI_1objectGetString property = %s value = %s", property_name, property_value);

    env->ReleaseStringUTFChars(propertyName, property_name);
    return env->NewStringUTF(property_value);
  }

/*
 * Class:     io_realm_CInterop
 * Method:    JNI_objectSetString
 * Signature: (JLjava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_io_realm_CInterop_JNI_1objectSetString
  (JNIEnv *env, jobject, jlong objPointer, jstring propertyName, jstring value) {
      const char *property_name = env->GetStringUTFChars(propertyName, 0);
      const char *property_value = env->GetStringUTFChars(value, 0);

      __android_log_print(ANDROID_LOG_VERBOSE, "REALM_JNI", "Java_io_realm_CInterop_JNI_1objectSetString property = %s value = %s", property_name, property_value);
      object_set_string(reinterpret_cast<realm_object_t*>(objPointer), property_name, property_value);

      env->ReleaseStringUTFChars(propertyName, property_name);
      env->ReleaseStringUTFChars(value, property_value);
  }

/*
 * Class:     io_realm_CInterop
 * Method:    JNI_objectGetInt64
 * Signature: (JLjava/lang/String;)J
 */
JNIEXPORT jlong JNICALL Java_io_realm_CInterop_JNI_1objectGetInt64
  (JNIEnv *env, jobject, jlong objPointer, jstring propertyName) {
    const char *property_name = env->GetStringUTFChars(propertyName, 0);
    
    __android_log_print(ANDROID_LOG_VERBOSE, "REALM_JNI", "Java_io_realm_CInterop_JNI_1objectGetInt64 property = %s", property_name);

    int64_t value = object_get_int64(reinterpret_cast<realm_object_t*>(objPointer), property_name);

    env->ReleaseStringUTFChars(propertyName, property_name);  
    return value;
  }

/*
 * Class:     io_realm_CInterop
 * Method:    JNI_objectSetInt64
 * Signature: (JLjava/lang/String;J)V
 */
JNIEXPORT void JNICALL Java_io_realm_CInterop_JNI_1objectSetInt64
  (JNIEnv *env, jobject, jlong objPointer, jstring propertyName, jlong value) {

    const char *property_name = env->GetStringUTFChars(propertyName, 0);
    
    __android_log_print(ANDROID_LOG_VERBOSE, "REALM_JNI", "Java_io_realm_CInterop_JNI_1objectSetInt64 property = %s", property_name);

    object_set_int64(reinterpret_cast<realm_object_t*>(objPointer), property_name, value);
    
    env->ReleaseStringUTFChars(propertyName, property_name);
  }

/*
 * Class:     io_realm_CInterop
 * Method:    JNI_queryGetSize
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_io_realm_CInterop_JNI_1queryGetSize
  (JNIEnv *, jobject, jlong queryPointer) {
    __android_log_print(ANDROID_LOG_VERBOSE, "REALM_JNI", "Java_io_realm_CInterop_JNI_1queryGetSize");
    return realmresults_size(reinterpret_cast<realm_results_t*>(queryPointer));
  }

/*
 * Class:     io_realm_CInterop
 * Method:    JNI_queryGetObjectAt
 * Signature: (JLjava/lang/String;I)J
 */
JNIEXPORT jlong JNICALL Java_io_realm_CInterop_JNI_1queryGetObjectAt
  (JNIEnv *env, jobject, jlong queryPointer, jstring tableName, jint index) {
    const char *object_type = env->GetStringUTFChars(tableName, 0);
    __android_log_print(ANDROID_LOG_VERBOSE, "REALM_JNI", "Java_io_realm_CInterop_JNI_1queryGetObjectAt table_name = %s", object_type);
    realm_object_t* objectPointer = realmresults_get(reinterpret_cast<realm_results_t*>(queryPointer), object_type, index);
    return reinterpret_cast<jlong>(objectPointer);
  }
