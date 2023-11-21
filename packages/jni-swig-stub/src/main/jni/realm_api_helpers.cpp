/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "realm_api_helpers.h"
#include <vector>
#include <thread>
#include <realm/object-store/c_api/util.hpp>
#include "java_method.hpp"

using namespace realm::jni_util;
using namespace realm::_impl;

jobject wrap_pointer(JNIEnv* jenv, jlong pointer, jboolean managed = false) {
    static JavaMethod pointer_wrapper_constructor(jenv,
                                                  JavaClassGlobalDef::long_pointer_wrapper(),
                                                  "<init>",
                                                  "(JZ)V");
    return jenv->NewObject(JavaClassGlobalDef::long_pointer_wrapper(),
                           pointer_wrapper_constructor,
                           pointer,
                           managed);
}

inline jboolean jni_check_exception(JNIEnv *jenv = get_env(), bool registrable_callback_error = false) {
    // FIXME https://github.com/realm/realm-kotlin/issues/665 This function is catching and swallowing
    //  the exception. This behavior could leave the SDK in an illegal state.
    if (jenv->ExceptionCheck()) {
        // Print the exception stacktrace in stderr.
        jenv->ExceptionDescribe();
        jthrowable exception = jenv->ExceptionOccurred();
        jenv->ExceptionClear();
        // setting the user code error is only propagated on certain callbacks.
        if (registrable_callback_error)
            realm_register_user_code_callback_error(jenv->NewGlobalRef(exception));
        return false;
    }
    return true;
}

inline void push_local_frame(JNIEnv *jenv, jint frame_size) {
    if (jenv->PushLocalFrame(frame_size) != 0) {
        jni_check_exception(jenv);
        throw std::runtime_error("Failed pushing a local frame with size " + std::to_string(frame_size));
    }
}

inline jobject create_java_exception(JNIEnv *jenv, realm_error_t error) {
    // Invoke CoreErrorConverter.asThrowable() to retrieve an exception instance that
    // maps to the core error.
    static const JavaClass& error_type_class = realm::_impl::JavaClassGlobalDef::core_error_converter();
    static JavaMethod error_type_as_exception(jenv,
                                              error_type_class,
                                              "asThrowable",
                                              "(IILjava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)Ljava/lang/Throwable;", true);

    push_local_frame(jenv, 3);
    jstring error_message = (jenv)->NewStringUTF(error.message);
    jstring error_path = (jenv)->NewStringUTF(error.path);
    jobject exception = (jenv)->CallStaticObjectMethod(
            error_type_class,
            error_type_as_exception,
            jint(error.categories),
            jint(error.error),
            error_message,
            error_path,
            static_cast<jobject>(error.usercode_error)
    );
    if (error.usercode_error) {
        (jenv)->DeleteGlobalRef(static_cast<jobject>(error.usercode_error));
    }
    jni_check_exception(jenv);
    return jenv->PopLocalFrame(exception);
}

bool throw_last_error_as_java_exception(JNIEnv *jenv) {
    realm_error_t error;
    if (realm_get_last_error(&error)) {
        jobject exception = create_java_exception(jenv, error);
        realm_clear_last_error();
        (jenv)->Throw(reinterpret_cast<jthrowable>(exception));
        return true;
    } else {
        return false;
    }
}

inline std::string get_exception_message(JNIEnv *env) {
    jthrowable e = env->ExceptionOccurred();
    env->ExceptionClear();
    jclass clazz = env->GetObjectClass(e);
    jmethodID get_message = env->GetMethodID(clazz,
                                            "getMessage",
                                            "()Ljava/lang/String;");
    jstring message = (jstring) env->CallObjectMethod(e, get_message);
    return env->GetStringUTFChars(message, NULL);
}

inline void system_out_println(JNIEnv *env, std::string message) {
    jclass system_class = env->FindClass("java/lang/System");
    jfieldID field_id = env->GetStaticFieldID(system_class, "out", "Ljava/io/PrintStream;");
    jobject system_out = env->GetStaticObjectField(system_class, field_id);
    jclass print_stream_class = env->FindClass("java/io/PrintStream");
    jmethodID method_id = env->GetMethodID(print_stream_class, "println", "(Ljava/lang/String;)V");
    env->CallVoidMethod(system_out, method_id, to_jstring(env, message));
}

void
realm_changed_callback(void* userdata) {
    auto env = get_env(true);
    static JavaClass java_callback_class(env, "kotlin/jvm/functions/Function0");
    static JavaMethod java_callback_method(env, java_callback_class, "invoke",
                                           "()Ljava/lang/Object;");
    // TODO Align exceptions handling https://github.com/realm/realm-kotlin/issues/665
    jni_check_exception(env);
    env->CallObjectMethod(static_cast<jobject>(userdata), java_callback_method);
    jni_check_exception(env);
}

void
schema_changed_callback(void* userdata, const realm_schema_t* new_schema) {
    auto env = get_env(true);
    static JavaClass java_callback_class(env, "kotlin/jvm/functions/Function1");
    static JavaMethod java_callback_method(env, java_callback_class, "invoke",
                                           "(Ljava/lang/Object;)Ljava/lang/Object;");
    env->PushLocalFrame(1);
    jobject schema_pointer_wrapper = wrap_pointer(env,reinterpret_cast<jlong>(new_schema));
    // TODO Align exceptions handling https://github.com/realm/realm-kotlin/issues/665
    jni_check_exception(env);
    env->CallObjectMethod(static_cast<jobject>(userdata), java_callback_method, schema_pointer_wrapper);
    jni_check_exception(env);
    env->PopLocalFrame(NULL);
}

bool migration_callback(void *userdata, realm_t *old_realm, realm_t *new_realm,
                        const realm_schema_t *schema) {
    auto env = get_env(true);
    static JavaClass java_callback_class(env, "io/realm/kotlin/internal/interop/MigrationCallback");
    static JavaMethod java_callback_method(env, java_callback_class, "migrate",
                                           "(Lio/realm/kotlin/internal/interop/NativePointer;Lio/realm/kotlin/internal/interop/NativePointer;Lio/realm/kotlin/internal/interop/NativePointer;)V");
    // These realm/schema pointers are only valid for the duration of the
    // migration so don't let ownership follow the NativePointer-objects
    env->PushLocalFrame(3);
    env->CallVoidMethod(static_cast<jobject>(userdata), java_callback_method,
                        wrap_pointer(env, reinterpret_cast<jlong>(old_realm), false),
                        wrap_pointer(env, reinterpret_cast<jlong>(new_realm), false),
                        wrap_pointer(env, reinterpret_cast<jlong>(schema))
    );
    bool failed = jni_check_exception(env, true);
    env->PopLocalFrame(NULL);
    return failed;
}

// TODO OPTIMIZE Abstract pattern for all notification registrations for collections that receives
//  changes as realm_collection_changes_t.
realm_notification_token_t *
register_results_notification_cb(realm_results_t *results, jobject callback) {
    auto jenv = get_env();
    static jclass notification_class = jenv->FindClass("io/realm/kotlin/internal/interop/NotificationCallback");
    static jmethodID on_change_method = jenv->GetMethodID(notification_class, "onChange", "(J)V");

    return realm_results_add_notification_callback(
            results,
            // Use the callback as user data
            static_cast<jobject>(get_env()->NewGlobalRef(callback)),
            [](void *userdata) {
                get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
            },
            NULL, // See https://github.com/realm/realm-kotlin/issues/661
            // change callback
            [](void *userdata, const realm_collection_changes_t *changes) {
                // TODO API-NOTIFICATION Consider catching errors and propagate to error callback
                //  like the C-API error callback below
                //  https://github.com/realm/realm-kotlin/issues/889
                auto jenv = get_env(true);
                jni_check_exception(jenv);
                jenv->CallVoidMethod(static_cast<jobject>(userdata),
                                     on_change_method,
                                     reinterpret_cast<jlong>(changes));
                jni_check_exception(jenv);
            }
    );
}

realm_on_object_change_func_t get_on_object_change() {
    auto jenv = get_env(true);
    static jclass notification_class = jenv->FindClass("io/realm/kotlin/internal/interop/NotificationCallback");
    static jmethodID on_change_method = jenv->GetMethodID(notification_class, "onChange", "(J)V");
    return [](realm_userdata_t userdata, const realm_object_changes_t* changes) {
        // TODO API-NOTIFICATION Consider catching errors and propagate to error callback
        //  like the C-API error callback below
        //  https://github.com/realm/realm-kotlin/issues/889
        auto jenv = get_env(true);
        jni_check_exception(jenv);
        jenv->CallVoidMethod(static_cast<jobject>(userdata),
                             on_change_method,
                             reinterpret_cast<jlong>(changes));
        jni_check_exception(jenv);
    };
}

realm_on_collection_change_func_t get_on_collection_change() {
    auto jenv = get_env(true);
    static jclass notification_class = jenv->FindClass("io/realm/kotlin/internal/interop/NotificationCallback");
    static jmethodID on_change_method = jenv->GetMethodID(notification_class, "onChange", "(J)V");
    return [](realm_userdata_t userdata, const realm_collection_changes_t* changes) {
        // TODO API-NOTIFICATION Consider catching errors and propagate to error callback
        //  like the C-API error callback below
        //  https://github.com/realm/realm-kotlin/issues/889
        auto jenv = get_env(true);
        jni_check_exception(jenv);
        jenv->CallVoidMethod(static_cast<jobject>(userdata),
                             on_change_method,
                             reinterpret_cast<jlong>(changes));
        jni_check_exception(jenv);
    };
}

realm_on_dictionary_change_func_t get_on_dictionary_change() {
    auto jenv = get_env(true);
    static jclass notification_class = jenv->FindClass("io/realm/kotlin/internal/interop/NotificationCallback");
    static jmethodID on_change_method = jenv->GetMethodID(notification_class, "onChange", "(J)V");
    return [](realm_userdata_t userdata, const realm_dictionary_changes_t* changes) {
        // TODO API-NOTIFICATION Consider catching errors and propagate to error callback
        //  like the C-API error callback below
        //  https://github.com/realm/realm-kotlin/issues/889
        auto jenv = get_env(true);
        jni_check_exception(jenv);
        jenv->CallVoidMethod(static_cast<jobject>(userdata),
                             on_change_method,
                             reinterpret_cast<jlong>(changes));
        jni_check_exception(jenv);
    };
}

realm_notification_token_t *
register_notification_cb(int64_t collection_ptr, realm_collection_type_e collection_type,
                         jobject callback) {
    auto user_data = static_cast<jobject>(get_env()->NewGlobalRef(callback));
    auto user_data_free = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };

    switch (collection_type) {
        case RLM_COLLECTION_TYPE_NONE: return realm_object_add_notification_callback(
                    reinterpret_cast<realm_object_t*>(collection_ptr),
                    user_data, // Use the callback as user data
                    user_data_free,
                    NULL, // See https://github.com/realm/realm-kotlin/issues/661
                    get_on_object_change()
            );
        case RLM_COLLECTION_TYPE_LIST: return realm_list_add_notification_callback(
                    reinterpret_cast<realm_list_t*>(collection_ptr),
                    user_data, // Use the callback as user data
                    user_data_free,
                    NULL, // See https://github.com/realm/realm-kotlin/issues/661
                    get_on_collection_change()
            );
        case RLM_COLLECTION_TYPE_SET: return realm_set_add_notification_callback(
                    reinterpret_cast<realm_set_t*>(collection_ptr),
                    user_data, // Use the callback as user data
                    user_data_free,
                    NULL, // See https://github.com/realm/realm-kotlin/issues/661
                    get_on_collection_change()
            );
        case RLM_COLLECTION_TYPE_DICTIONARY: return realm_dictionary_add_notification_callback(
                    reinterpret_cast<realm_dictionary_t*>(collection_ptr),
                    user_data, // Use the callback as user data
                    user_data_free,
                    NULL, // See https://github.com/realm/realm-kotlin/issues/661
                    get_on_dictionary_change()
            );
    }
}

class CustomJVMScheduler {
public:
    CustomJVMScheduler(jobject dispatchScheduler) : m_id(std::this_thread::get_id()) {
        JNIEnv *jenv = get_env();
        jclass jvm_scheduler_class = jenv->FindClass("io/realm/kotlin/internal/interop/JVMScheduler");
        m_notify_method = jenv->GetMethodID(jvm_scheduler_class, "notifyCore", "(J)V");
        m_cancel_method = jenv->GetMethodID(jvm_scheduler_class, "cancel", "()V");
        m_jvm_dispatch_scheduler = jenv->NewGlobalRef(dispatchScheduler);
    }

    ~CustomJVMScheduler() {
        get_env(true)->DeleteGlobalRef(m_jvm_dispatch_scheduler);
    }

    void notify(realm_work_queue_t* work_queue) {
        // There is currently no signaling of creation/tear down of the core notifier thread, so we
        // just attach it as a daemon thread here on first notification to allow the JVM to
        // shutdown propertly. See https://github.com/realm/realm-core/issues/6429
        auto jenv = get_env(true, true, "core-notifier");
        jni_check_exception(jenv);
        jenv->CallVoidMethod(m_jvm_dispatch_scheduler, m_notify_method,
                             reinterpret_cast<jlong>(work_queue));
        jni_check_exception(jenv);
    }

    bool is_on_thread() const noexcept {
        return m_id == std::this_thread::get_id();
    }

    bool can_invoke() const noexcept {
        return true;
    }

    void cancel() {
        auto jenv = get_env(true, true, "core-notifier");
        jenv->CallVoidMethod(m_jvm_dispatch_scheduler, m_cancel_method);
        jni_check_exception(jenv);
    }


private:
    std::thread::id m_id;
    jmethodID m_notify_method;
    jmethodID m_cancel_method;
    jobject m_jvm_dispatch_scheduler;
};

// Note: using jlong here will create a linker issue
// Undefined symbols for architecture x86_64:
//  "invoke_core_notify_callback(long long, long long)", referenced from:
//      _Java_io.realm.kotlin.internal_interop_realmcJNI_invoke_1core_1notify_1callback in realmc.cpp.o
//ld: symbol(s) not found for architecture x86_64
//
// I suspect this could be related to the fact that jni.h defines jlong differently between Android (typedef int64_t)
// and JVM which is a (typedef long long) resulting in a different signature of the method that could be found by the linker.
void invoke_core_notify_callback(int64_t work_queue) {
    realm_scheduler_perform_work(reinterpret_cast<realm_work_queue_t *>(work_queue));
}

realm_scheduler_t*
realm_create_scheduler(jobject dispatchScheduler) {
    if (dispatchScheduler) {
        auto jvmScheduler = new CustomJVMScheduler(dispatchScheduler);
        auto scheduler = realm_scheduler_new(
                jvmScheduler,
                [](void *userdata) {
                    auto jvmScheduler = static_cast<CustomJVMScheduler *>(userdata);
                    jvmScheduler->cancel();
                    delete(jvmScheduler);
                },
                [](void *userdata, realm_work_queue_t* work_queue) { static_cast<CustomJVMScheduler *>(userdata)->notify(work_queue); },
                [](void *userdata) { return static_cast<CustomJVMScheduler *>(userdata)->is_on_thread(); },
                [](const void *userdata, const void *userdata_other) { return userdata == userdata_other; },
                [](void *userdata) { return static_cast<CustomJVMScheduler *>(userdata)->can_invoke(); }
        );
        return scheduler;
    }
    throw std::runtime_error("Null dispatchScheduler");
}

jobject convert_to_jvm_app_error(JNIEnv* env, const realm_app_error_t* error) {
    static JavaMethod app_error_constructor(env,
                                                JavaClassGlobalDef::app_error(),
                                                "newInstance",
                                                "(IIILjava/lang/String;Ljava/lang/String;)Lio/realm/kotlin/internal/interop/sync/AppError;",
                                                true);
    env->PushLocalFrame(3);
    jint category = static_cast<jint>(error->categories);
    jint code = static_cast<jint>(error->error);
    jint httpCode = static_cast<jint>(error->http_status_code);
    jstring message = to_jstring(env, error->message);
    jstring serverLogs = to_jstring(env, error->link_to_server_logs);

    auto result = env->CallStaticObjectMethod(JavaClassGlobalDef::app_error(),
                          app_error_constructor,
                          category,
                          code,
                          httpCode,
                          message,
                          serverLogs);
    jni_check_exception(env);
    return env->PopLocalFrame(result);
}


void app_complete_void_callback(void *userdata, const realm_app_error_t *error) {
    auto env = get_env(true);
    static JavaMethod java_notify_onerror(env, JavaClassGlobalDef::app_callback(), "onError",
                                          "(Lio/realm/kotlin/internal/interop/sync/AppError;)V");
    static JavaMethod java_notify_onsuccess(env, JavaClassGlobalDef::app_callback(), "onSuccess",
                                            "(Ljava/lang/Object;)V");
    static JavaClass unit_class(env, "kotlin/Unit");
    static JavaMethod unit_constructor(env, unit_class, "<init>", "()V");

    env->PushLocalFrame(1);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->PopLocalFrame(NULL);
        throw std::runtime_error("An unexpected Error was thrown from Java. See LogCat");
    } else if (error) {
        jobject app_error = convert_to_jvm_app_error(env, error);
        env->CallVoidMethod(static_cast<jobject>(userdata), java_notify_onerror, app_error);
    } else {
        jobject unit = env->NewObject(unit_class, unit_constructor);
        env->CallVoidMethod(static_cast<jobject>(userdata), java_notify_onsuccess, unit);
    }
    jni_check_exception(env);
    env->PopLocalFrame(NULL);
}

void app_complete_result_callback(void* userdata, void* result, const realm_app_error_t* error) {
    auto env = get_env(true);
    static JavaMethod java_notify_onerror(env, JavaClassGlobalDef::app_callback(), "onError",
                                          "(Lio/realm/kotlin/internal/interop/sync/AppError;)V");
    static JavaMethod java_notify_onsuccess(env, JavaClassGlobalDef::app_callback(), "onSuccess",
                                            "(Ljava/lang/Object;)V");

    static JavaClass native_pointer_class(env, "io/realm/kotlin/internal/interop/LongPointerWrapper");
    static JavaMethod native_pointer_constructor(env, native_pointer_class, "<init>", "(JZ)V");

    env->PushLocalFrame(1);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->PopLocalFrame(NULL);
        throw std::runtime_error("An unexpected Error was thrown from Java. See LogCat");
    } else if (error) {
        jobject app_exception = convert_to_jvm_app_error(env, error);
        env->CallVoidMethod(static_cast<jobject>(userdata), java_notify_onerror, app_exception);
    } else {
        // Remember to clone user object or else it will be invalidated right after we leave this callback
        void* cloned_result = realm_clone(result);
        jobject pointer = env->NewObject(native_pointer_class, native_pointer_constructor,
                                         reinterpret_cast<jlong>(cloned_result), false);
        env->CallVoidMethod(static_cast<jobject>(userdata), java_notify_onsuccess, pointer);
    }
    jni_check_exception(env);
    env->PopLocalFrame(NULL);
}

jobject create_api_key_wrapper(JNIEnv* env, const realm_app_user_apikey_t* key_data) {
    static JavaClass api_key_wrapper_class(env, "io/realm/kotlin/internal/interop/sync/ApiKeyWrapper");
    static JavaMethod api_key_wrapper_constructor(env, api_key_wrapper_class, "<init>", "([BLjava/lang/String;Ljava/lang/String;Z)V");
    auto id_size = sizeof(key_data->id.bytes);
    jbyteArray id = env->NewByteArray(id_size);
    env->SetByteArrayRegion(id, 0, id_size, reinterpret_cast<const jbyte*>(key_data->id.bytes));
    jstring key = to_jstring(env, key_data->key);
    jstring name = to_jstring(env, key_data->name);
    jboolean disabled = key_data->disabled;
    auto result = env->NewObject(api_key_wrapper_class,
                          api_key_wrapper_constructor,
                          id,
                          key,
                          name,
                          disabled,
                          false);
    return result;
}

void app_apikey_callback(realm_userdata_t userdata, realm_app_user_apikey_t* apikey, const realm_app_error_t* error) {
    auto env = get_env(true);
    static JavaMethod java_notify_onerror(env, JavaClassGlobalDef::app_callback(), "onError",
                                          "(Lio/realm/kotlin/internal/interop/sync/AppError;)V");
    static JavaMethod java_notify_onsuccess(env, JavaClassGlobalDef::app_callback(), "onSuccess",
                                            "(Ljava/lang/Object;)V");
    env->PushLocalFrame(1);
    if (error) {
        jobject app_exception = convert_to_jvm_app_error(env, error);
        env->CallVoidMethod(static_cast<jobject>(userdata), java_notify_onerror, app_exception);
    } else {
        jobject api_key_wrapper_obj = create_api_key_wrapper(env, apikey);
        env->CallVoidMethod(static_cast<jobject>(userdata), java_notify_onsuccess, api_key_wrapper_obj);
    }
    jni_check_exception(env);
    env->PopLocalFrame(NULL);
}

void app_string_callback(realm_userdata_t userdata, const char *serialized_ejson_response,
                         const realm_app_error_t *error) {
    auto env = get_env(true);
    static JavaMethod java_notify_onerror(
            env,
            JavaClassGlobalDef::app_callback(),
            "onError",

            "(Lio/realm/kotlin/internal/interop/sync/AppError;)V"
    );
    static JavaMethod java_notify_onsuccess(
            env,
            JavaClassGlobalDef::app_callback(),
            "onSuccess",
            "(Ljava/lang/Object;)V"
    );

    env->PushLocalFrame(1);
    if (error) {
        jobject app_exception = convert_to_jvm_app_error(env, error);
        env->CallVoidMethod(static_cast<jobject>(userdata), java_notify_onerror, app_exception);
    } else {
        jstring jserialized_ejson_response = to_jstring(env, serialized_ejson_response);
        env->CallVoidMethod(static_cast<jobject>(userdata), java_notify_onsuccess, jserialized_ejson_response);
    }
    jni_check_exception(env);
    env->PopLocalFrame(NULL);
}

void app_apikey_list_callback(realm_userdata_t userdata, realm_app_user_apikey_t* keys, size_t count, realm_app_error_t* error) {
    auto env = get_env(true);
    static JavaClass api_key_wrapper_class(env, "io/realm/kotlin/internal/interop/sync/ApiKeyWrapper");

    static JavaMethod java_notify_onerror(env, JavaClassGlobalDef::app_callback(), "onError",
                                          "(Lio/realm/kotlin/internal/interop/sync/AppError;)V");
    static JavaMethod java_notify_onsuccess(env, JavaClassGlobalDef::app_callback(), "onSuccess",
                                            "(Ljava/lang/Object;)V");

    env->PushLocalFrame(1);
    if (error) {
        jobject app_exception = convert_to_jvm_app_error(env, error);
        env->CallVoidMethod(static_cast<jobject>(userdata), java_notify_onerror, app_exception);
    } else {
        // Create Object[] array
        jobjectArray key_array = env->NewObjectArray(count, api_key_wrapper_class, nullptr);

        // For each ApiKey, create the Kotlin Wrapper and insert into array
        for (int i = 0; i < count; i++) {
            realm_app_user_apikey_t api_key = keys[i];
            jobject api_key_wrapper_obj = create_api_key_wrapper(env, &api_key);
            env->SetObjectArrayElement(key_array, i, api_key_wrapper_obj);
            env->DeleteLocalRef(api_key_wrapper_obj);
        }

        // Return Object[] to Kotlin
        env->CallVoidMethod(static_cast<jobject>(userdata), java_notify_onsuccess, key_array);
    }
    jni_check_exception(env);
    env->PopLocalFrame(NULL);
}

bool realm_should_compact_callback(void* userdata, uint64_t total_bytes, uint64_t used_bytes) {
    auto env = get_env(true);
    static JavaClass java_should_compact_class(env, "io/realm/kotlin/internal/interop/CompactOnLaunchCallback");
    static JavaMethod java_should_compact_method(env, java_should_compact_class, "invoke", "(JJ)Z");

    jobject callback = static_cast<jobject>(userdata);
    jboolean result = env->CallBooleanMethod(callback, java_should_compact_method, jlong(total_bytes), jlong(used_bytes));
    return jni_check_exception(env, true) && result;
}

bool realm_data_initialization_callback(void* userdata, realm_t*) {
    auto env = get_env(true);
    static JavaClass java_data_init_class(env, "io/realm/kotlin/internal/interop/DataInitializationCallback");
    static JavaMethod java_data_init_method(env, java_data_init_class, "invoke", "()V");

    jobject callback = static_cast<jobject>(userdata);
    env->CallVoidMethod(callback, java_data_init_method);
    return jni_check_exception(env, true);
}

static void send_request_via_jvm_transport(JNIEnv *jenv, jobject network_transport, const realm_http_request_t request, jobject j_response_callback) {
    static JavaMethod m_send_request_method(jenv,
                                            JavaClassGlobalDef::network_transport_class(),
                                            "sendRequest",
                                            "(Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;Ljava/lang/String;Lio/realm/kotlin/internal/interop/sync/ResponseCallback;)V");

    // Prepare request fields to be consumable by JVM
    std::string method;
    switch (request.method) {
        case realm_http_request_method::RLM_HTTP_REQUEST_METHOD_GET:
            method = "get";
            break;
        case realm_http_request_method::RLM_HTTP_REQUEST_METHOD_POST:
            method = "post";
            break;
        case realm_http_request_method::RLM_HTTP_REQUEST_METHOD_PATCH:
            method = "patch";
            break;
        case realm_http_request_method::RLM_HTTP_REQUEST_METHOD_PUT:
            method = "put";
            break;
        case realm_http_request_method::RLM_HTTP_REQUEST_METHOD_DELETE:
            method = "delete";
            break;
    }

    static JavaMethod init(jenv,
                           JavaClassGlobalDef::java_util_hashmap(),
                           "<init>",
                           "(I)V");

    static JavaMethod put_method(jenv,
                                 JavaClassGlobalDef::java_util_hashmap(),
                                 "put",
                                 "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    size_t map_size = request.num_headers;
    push_local_frame(jenv, 1);
    jobject request_headers = jenv->NewObject(JavaClassGlobalDef::java_util_hashmap(), init, (jsize) map_size);
    for (int i = 0; i < map_size; i++) {
        push_local_frame(jenv, 2);

        realm_http_header_t header_pair = request.headers[i];

        jstring key = to_jstring(jenv, header_pair.name);
        jstring value = to_jstring(jenv, header_pair.value);

        jenv->CallObjectMethod(request_headers, put_method, key, value);
        jni_check_exception(jenv);
        jenv->PopLocalFrame(NULL);
    }

    // Send request
    jenv->CallVoidMethod(network_transport,
                                  m_send_request_method,
                                  to_jstring(jenv, method),
                                  to_jstring(jenv, request.url),
                                  request_headers,
                                  to_jstring(jenv, request.body),
                                  j_response_callback
    );
    jni_check_exception(jenv);
    jenv->PopLocalFrame(NULL);
}

void complete_http_request(void* request_context, jobject j_response) {
    auto jenv = get_env(false); // will always be attached
    static JavaMethod get_http_code_method(jenv,
                                           JavaClassGlobalDef::network_transport_response_class(),
                                           "getHttpResponseCode",
                                           "()I");
    static JavaMethod get_custom_code_method(jenv,
                                             JavaClassGlobalDef::network_transport_response_class(),
                                             "getCustomResponseCode",
                                             "()I");
    static JavaMethod get_headers_method(jenv,
                                         JavaClassGlobalDef::network_transport_response_class(),
                                         "getJNIFriendlyHeaders",
                                         "()[Ljava/lang/String;");
    static JavaMethod get_body_method(jenv,
                                      JavaClassGlobalDef::network_transport_response_class(),
                                      "getBody", "()Ljava/lang/String;");

    // Extract JVM response fields
    jint http_code = jenv->CallIntMethod(j_response, get_http_code_method);
    jint custom_code = jenv->CallIntMethod(j_response, get_custom_code_method);
    JStringAccessor java_body(jenv, (jstring) jenv->CallObjectMethod(j_response, get_body_method), true);
    std::string body = java_body;

    JObjectArrayAccessor<JStringAccessor, jstring> java_headers(jenv, static_cast<jobjectArray>(jenv->CallObjectMethod(
            j_response, get_headers_method)));

    auto stacked_headers = std::vector<std::string>(); // Pins headers to function stack
    for (int i = 0; i < java_headers.size(); i = i + 2) {
        JStringAccessor key = java_headers[i];
        JStringAccessor value = java_headers[i + 1];
        stacked_headers.push_back(std::move(key));
        stacked_headers.push_back(std::move(value));
    }
    auto response_headers = std::vector<realm_http_header_t>();
    for (int i = 0; i < java_headers.size(); i = i + 2) {
        // FIXME REFACTOR when C++20 will be available
        realm_http_header header;
        header.name = stacked_headers[i].c_str();
        header.value = stacked_headers[i + 1].c_str();
        response_headers.push_back(header);
    }

    realm_http_response response;
    response.status_code = http_code;
    response.custom_status_code = custom_code;
    response.headers = response_headers.data();
    response.num_headers = response_headers.size();
    response.body = body.c_str();
    response.body_size = body.size();

    realm_http_transport_complete_request(request_context, &response);
}

/**
 * Perform a network request on JVM
 *
 * 1. Cast userdata to the network transport
 * 2. Transform core request to JVM request
 * 3. Perform request
 * 4. Transform JVM response to core response
 */
static void network_request_lambda_function(void* userdata,
                                            const realm_http_request_t request,
                                            void* request_context) {
    auto jenv = get_env(true);

    // Initialize pointer to JVM class and methods
    jobject network_transport = static_cast<jobject>(userdata);

    try {
        jclass response_callback_class = JavaClassGlobalDef::app_response_callback();
        static jmethodID response_callback_constructor = jenv->GetMethodID(response_callback_class,
                                                                           "<init>",
                                                                           "(Lio/realm/kotlin/internal/interop/sync/NetworkTransport;J)V");
        push_local_frame(jenv, 1);
        jobject response_callback = jenv->NewObject(response_callback_class,
                                                    response_callback_constructor,
                                                    reinterpret_cast<jobject>(userdata),
                                                    reinterpret_cast<jlong>(request_context));

        send_request_via_jvm_transport(jenv, network_transport, request, response_callback);
        jenv->PopLocalFrame(NULL);
    } catch (std::runtime_error &e) {
        // Runtime exception while processing the request/response
        realm_http_response_t response_error;
        // FIXME: validate we propagate the custom codes as an actual exception to the user
        // see: https://github.com/realm/realm-kotlin/issues/451
        response_error.custom_status_code = -4;
        response_error.num_headers = 0;
        response_error.body_size = 0;

        realm_http_transport_complete_request(request_context, &response_error);
    }
}

realm_http_transport_t* realm_network_transport_new(jobject network_transport) {
    auto jenv = get_env(false); // Always called from JVM
    return realm_http_transport_new(&network_request_lambda_function,
                                    jenv->NewGlobalRef(network_transport), // userdata is the transport object
                                    [](void* userdata) {
                                        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
                                    });
}

void set_log_callback(jint j_log_level, jobject log_callback) {
auto jenv = get_env(false);
auto log_level = static_cast<realm_log_level_e>(j_log_level);
realm_set_log_callback([](void *userdata, realm_log_level_e level, const char *message) {
                               auto log_callback = static_cast<jobject>(userdata);
                               auto jenv = get_env(true);


                               auto java_level = static_cast<jshort>(level);
                               static JavaMethod log_method(jenv,
                                                            JavaClassGlobalDef::log_callback(),
                                                            "log",
                                                            "(SLjava/lang/String;)V");

                               push_local_frame(jenv, 1);
                               jenv->CallVoidMethod(log_callback, log_method, java_level, to_jstring(jenv, message));
                               jni_check_exception(jenv);
                               jenv->PopLocalFrame(NULL);
                          },
                          log_level,
                          jenv->NewGlobalRef(log_callback), // userdata is the log callback
                          [](void* userdata) {
                              // The log callback is a static global method that is intended to
                              // live for the lifetime of the application. On JVM it looks like
                              // is being destroyed after the JNIEnv has been destroyed, which
                              // will e.g. crash the Gradle test setup. So instead, we just do a
                              // best effort of cleaning up the registered callback.
                              JNIEnv *env = get_env_or_null();
                              if (env) {
                                  env->DeleteGlobalRef(static_cast<jobject>(userdata));
                              }
                          });
}

jobject convert_to_jvm_sync_error(JNIEnv* jenv, const realm_sync_error_t& error) {

    static JavaMethod sync_error_constructor(jenv,
                                             JavaClassGlobalDef::sync_error(),
                                             "<init>",
    "(IILjava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZZ[Lio/realm/kotlin/internal/interop/sync/CoreCompensatingWriteInfo;)V");

    jint category = static_cast<jint>(error.status.categories);
    jint value = static_cast<jint>(error.status.error);
    jstring msg = to_jstring(jenv, error.status.message);
    jstring joriginal_file_path = nullptr;
    jstring jrecovery_file_path = nullptr;
    jboolean is_fatal = error.is_fatal;
    jboolean is_unrecognized_by_client = error.is_unrecognized_by_client;
    jboolean is_client_reset_requested = error.is_client_reset_requested;

    auto user_info_map = std::map<std::string, std::string>();
    for (int i = 0; i < error.user_info_length; i++) {
        realm_sync_error_user_info_t user_info = error.user_info_map[i];
        user_info_map.insert(std::make_pair(user_info.key, user_info.value));
    }

    static JavaMethod core_compensating_write_info_constructor(
            jenv,
            JavaClassGlobalDef::core_compensating_write_info(),
            "<init>",
            "(Ljava/lang/String;Ljava/lang/String;J)V"
    );

    push_local_frame(jenv, 3);
    auto j_compensating_write_info_array = jenv->NewObjectArray(
            error.compensating_writes_length,
            JavaClassGlobalDef::core_compensating_write_info(),
            NULL
    );

    for (int index = 0; index < error.compensating_writes_length; index++) {
        realm_sync_error_compensating_write_info_t& compensating_write_info = error.compensating_writes[index];

        push_local_frame(jenv, 3);

        auto reason = to_jstring(jenv, compensating_write_info.reason);
        auto object_name = to_jstring(jenv, compensating_write_info.object_name);

        jobject j_compensating_write_info = jenv->NewObject(
                JavaClassGlobalDef::core_compensating_write_info(),
                core_compensating_write_info_constructor,
                reason,
                object_name,
                &compensating_write_info.primary_key
        );

        jenv->SetObjectArrayElement(
                j_compensating_write_info_array,
                index,
                j_compensating_write_info
        );

        jenv->PopLocalFrame(NULL);
    }

    // We can't only rely on 'error.is_client_reset_requested' (even though we should) to extract
    // user info from the error since 'PermissionDenied' are fatal (non-client-reset) errors that
    // mark the file for deletion. Having 'original path' in the user_info_map is a side effect of
    // using the same code for client reset.
    if (error.user_info_length > 0) {
        auto end_it = user_info_map.end();

        auto original_it = user_info_map.find(error.c_original_file_path_key);
        if (end_it != original_it) {
            auto original_file_path = original_it->second;
            joriginal_file_path = to_jstring(jenv, original_file_path);
        }

        // Sync errors may not have the path to the recovery file unless a Client Reset is requested
        auto recovery_it = user_info_map.find(error.c_recovery_file_path_key);
        if (error.is_client_reset_requested && (end_it != recovery_it)) {
            auto recovery_file_path = recovery_it->second;
            jrecovery_file_path = to_jstring(jenv, recovery_file_path);
        }
    }

    jobject result = jenv->NewObject(
            JavaClassGlobalDef::sync_error(),
            sync_error_constructor,
            category,
            value,
            msg,
            joriginal_file_path,
            jrecovery_file_path,
            is_fatal,
            is_unrecognized_by_client,
            is_client_reset_requested,
            j_compensating_write_info_array
    );

    jni_check_exception(jenv);
    return jenv->PopLocalFrame(result);
}

void sync_set_error_handler(realm_sync_config_t* sync_config, jobject error_handler) {
    realm_sync_config_set_error_handler(sync_config,
                                        [](void* userdata, realm_sync_session_t* session, const realm_sync_error_t error) {
                                            auto jenv = get_env(true);
                                            auto sync_error_callback = static_cast<jobject>(userdata);
                                            static JavaMethod sync_error_method(jenv,
                                                                                JavaClassGlobalDef::sync_error_callback(),
                                                                                "onSyncError",
                                                                                "(Lio/realm/kotlin/internal/interop/NativePointer;Lio/realm/kotlin/internal/interop/sync/SyncError;)V");

                                            push_local_frame(jenv, 2);

                                            jobject session_pointer_wrapper = wrap_pointer(jenv,reinterpret_cast<jlong>(session));
                                            jobject sync_error = convert_to_jvm_sync_error(jenv, error);

                                            jenv->CallVoidMethod(sync_error_callback,
                                                                 sync_error_method,
                                                                 session_pointer_wrapper,
                                                                 sync_error);
                                            jni_check_exception(jenv);
                                            jenv->PopLocalFrame(NULL);
                                        },
                                        static_cast<jobject>(get_env()->NewGlobalRef(error_handler)),
                                        [](void *userdata) {
                                            get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
                                        });
}

void transfer_completion_callback(void* userdata, realm_error_t* error) {
    auto env = get_env(true);
    static JavaMethod java_success_callback_method(env,
                                           JavaClassGlobalDef::sync_session_transfer_completion_callback(),
                                           "onSuccess",
                                           "()V");
    static JavaMethod java_error_callback_method(env,
                                                   JavaClassGlobalDef::sync_session_transfer_completion_callback(),
                                                   "onError",
                                                   "(IILjava/lang/String;)V");
    if (error) {
        jint category = static_cast<jint>(error->categories);
        jint value = error->error;
        env->PushLocalFrame(1);
        env->CallVoidMethod(static_cast<jobject>(userdata), java_error_callback_method, category, value, to_jstring(env, error->message));
        jni_check_exception(env);
        env->PopLocalFrame(NULL);
    } else {
        env->CallVoidMethod(static_cast<jobject>(userdata), java_success_callback_method);
    }
    jni_check_exception(env);
}

void realm_subscriptionset_changed_callback(void* userdata, realm_flx_sync_subscription_set_state_e state) {
    auto env = get_env(true);
    env->PushLocalFrame(1);
    jobject state_value = JavaClassGlobalDef::new_int(env, static_cast<int32_t>(state));
    env->CallObjectMethod(
            static_cast<jobject>(userdata),
            JavaClassGlobalDef::function1Method(env),
            state_value
    );
    jni_check_exception(env);
    env->PopLocalFrame(NULL);
}

void realm_async_open_task_callback(void* userdata, realm_thread_safe_reference_t* realm, const realm_async_error_t* error) {
    auto env = get_env(true);
    static JavaMethod java_invoke_method(env,
                                         JavaClassGlobalDef::async_open_callback(),
                                         "invoke",
                                         "(Ljava/lang/Throwable;)V");
    jobject callback = static_cast<jobject>(userdata);

    env->PushLocalFrame(1);
    jobject exception = nullptr;
    if (error) {
        realm_error_t err;
        realm_get_async_error(error, &err);
        exception = create_java_exception(env, err);
    } else {
        realm_release(realm);
    }
    env->CallVoidMethod(callback, java_invoke_method, exception);
    jni_check_exception(env);
    env->PopLocalFrame(NULL);
}

bool
before_client_reset(void* userdata, realm_t* before_realm) {
    auto env = get_env(true);
    static JavaMethod java_before_callback_function(env,
                                                    JavaClassGlobalDef::sync_before_client_reset(),
                                                    "onBeforeReset",
                                                    "(Lio/realm/kotlin/internal/interop/NativePointer;)V");
    env->PushLocalFrame(1);
    jobject before_pointer = wrap_pointer(env, reinterpret_cast<jlong>(before_realm), false);
    env->CallVoidMethod(static_cast<jobject>(userdata), java_before_callback_function, before_pointer);

    bool result = true;
    if (env->ExceptionCheck()) {
        std::string exception_message = get_exception_message(env);
        std::string message_template = "An error has occurred in the 'onBefore' callback: ";
        system_out_println(env, message_template.append(exception_message));
        result = false;
    }
    env->PopLocalFrame(NULL);
    return result;
}

bool
after_client_reset(void* userdata, realm_t* before_realm,
                   realm_thread_safe_reference_t* after_realm, bool did_recover) {
    auto env = get_env(true);
    static JavaMethod java_after_callback_function(env,
                                                   JavaClassGlobalDef::sync_after_client_reset(),
                                                   "onAfterReset",
                                                   "(Lio/realm/kotlin/internal/interop/NativePointer;Lio/realm/kotlin/internal/interop/NativePointer;Z)V");
    env->PushLocalFrame(2);
    jobject before_pointer = wrap_pointer(env, reinterpret_cast<jlong>(before_realm), false);
    // Reuse the scheduler from the beforeRealm, otherwise Core will attempt to recreate a new one,
    // which will fail on platforms that hasn't defined a default scheduler factory.
    realm_scheduler_t scheduler = realm_scheduler(before_realm->get()->scheduler());
    realm_t* after_realm_ptr = realm_from_thread_safe_reference(after_realm, &scheduler);

    jobject after_pointer = wrap_pointer(env, reinterpret_cast<jlong>(after_realm_ptr), false);
    env->CallVoidMethod(static_cast<jobject>(userdata), java_after_callback_function, before_pointer, after_pointer, did_recover);
    realm_close(after_realm_ptr);
    bool result = true;
    if (env->ExceptionCheck()) {
        std::string exception_message = get_exception_message(env);
        std::string message_template = "An error has occurred in the 'onAfter' callback: ";
        system_out_println(env, message_template.append(exception_message));
        result = false;
    }
    env->PopLocalFrame(NULL);
    return result;
}

void
sync_before_client_reset_handler(realm_sync_config_t* config, jobject before_handler) {
    // TODO use typemap patterns in realm.i
    auto jenv = get_env(true);
    auto before_func = reinterpret_cast<realm_sync_before_client_reset_func_t>(before_client_reset);
    void* user_data = static_cast<jobject>(jenv->NewGlobalRef(before_handler));
    realm_free_userdata_func_t free_func = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
    realm_sync_config_set_before_client_reset_handler(config, before_func, user_data, free_func);
}

void
sync_after_client_reset_handler(realm_sync_config_t* config, jobject after_handler) {
    // TODO use typemap patterns in realm.i
    auto jenv = get_env(true);
    auto after_func = reinterpret_cast<realm_sync_after_client_reset_func_t>(after_client_reset);
    void* user_data = static_cast<jobject>(jenv->NewGlobalRef(after_handler));
    realm_free_userdata_func_t free_func = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };
    realm_sync_config_set_after_client_reset_handler(config, after_func, user_data, free_func);
}

void
realm_sync_session_progress_notifier_callback(void *userdata, uint64_t transferred_bytes, uint64_t total_bytes) {
    auto env = get_env(true);

    static JavaMethod java_callback_method(env, JavaClassGlobalDef::progress_callback(), "onChange", "(JJ)V");

    jni_check_exception(env);
    env->CallVoidMethod(static_cast<jobject>(userdata), java_callback_method, jlong(transferred_bytes), jlong(total_bytes));
    jni_check_exception(env);
}

void
realm_sync_session_connection_state_change_callback(void *userdata, realm_sync_connection_state_e old_state, realm_sync_connection_state_e new_state) {
    auto env = get_env(true);

    static JavaMethod java_callback_method(env, JavaClassGlobalDef::connection_state_change_callback(), "onChange", "(II)V");

    jni_check_exception(env);
    env->CallVoidMethod(static_cast<jobject>(userdata), java_callback_method, jint(old_state), jint(new_state));
    jni_check_exception(env);
}

jlong
realm_sync_session_register_progress_notifier_wrapper(
        realm_sync_session_t* session, realm_sync_progress_direction_e direction, bool is_streaming, jobject callback
) {
    auto jenv = get_env(true);
    jlong jresult = 0;
    realm_sync_session_connection_state_notification_token_t *result =
            realm_sync_session_register_progress_notifier(
                    session,
                    realm_sync_session_progress_notifier_callback,
                    direction,
                    is_streaming,
                    static_cast<jobject>(jenv->NewGlobalRef(
                            callback)),
                    [](void *userdata) {
                        get_env(true)->DeleteGlobalRef(
                                static_cast<jobject>(userdata));
                    }
            );
    if (!result) {
        bool exception_thrown = throw_last_error_as_java_exception(jenv);
        if (exception_thrown) {
            // Return immediately if there was an error in which case the exception will be raised when returning to JVM
            return 0;
        }
    }
    *(realm_sync_session_connection_state_notification_token_t **)&jresult = result;
    return jresult;
}

// Explicit clean up method for releasing heap allocated data of a realm_value_t instance
void
realm_value_t_cleanup(realm_value_t* value) {
    switch (value->type) {
        case RLM_TYPE_STRING:  {
            const char* buf = value->string.data;
            if (buf) delete buf;
            break;
        }
        case RLM_TYPE_BINARY: {
            const uint8_t* buf = value->binary.data;
            if (buf) delete buf;
            break;
        }
        default:
            break;
    }
}

void
realm_sync_thread_created(realm_userdata_t userdata) {
    // Attach the sync client thread to the JVM so errors can be returned properly
    // Note, we need to hardcode the name as there is no good way to inject it from JVM as that itself
    // would require access to the JNiEnv.
    auto env = get_env(true, false, util::Optional<std::string>("SyncThread"));
    static JavaMethod java_callback_method(env, JavaClassGlobalDef::sync_thread_observer(), "onCreated", "()V");
    jni_check_exception(env);
    env->CallVoidMethod(static_cast<jobject>(userdata), java_callback_method);
    jni_check_exception(env);
}

void
realm_sync_thread_destroyed(realm_userdata_t userdata) {
    auto env = get_env(true);
    // Avoid touching any JNI methods if we have a pending exception
    // otherwise we will crash with  "JNI called with pending exception" instead of the real
    // error.
    if (env->ExceptionCheck() == JNI_FALSE) {
        static JavaMethod java_callback_method(env, JavaClassGlobalDef::sync_thread_observer(), "onDestroyed", "()V");
        env->CallVoidMethod(static_cast<jobject>(userdata), java_callback_method);
        jni_check_exception(env);
    }
    // Detach from the Java thread associated with the Sync Client Thread, otherwise
    // the JVM will not be able to shutdown.
    detach_current_thread();
}

void
realm_sync_thread_error(realm_userdata_t userdata, const char* error) {
    JNIEnv* env = get_env(true);
    std::string msg = util::format("An exception has been thrown on the sync client thread:\n%1", error);
    static JavaMethod java_callback_method(env, JavaClassGlobalDef::sync_thread_observer(), "onError", "(Ljava/lang/String;)V");
    env->CallVoidMethod(static_cast<jobject>(userdata), java_callback_method, to_jstring(env, msg));
    jni_check_exception(env);
}

realm_scheduler_t*
realm_create_generic_scheduler() {
    return new realm_scheduler_t { realm::util::Scheduler::make_dummy() };
}

void
realm_property_info_t_cleanup(realm_property_info_t* value) {
    delete[] value->link_origin_property_name;
    delete[] value->link_target;
    delete[] value->name;
    delete[] value->public_name;
}

void
realm_class_info_t_cleanup(realm_class_info_t * value) {
    delete[] value->primary_key;
    delete[] value->name;
}
