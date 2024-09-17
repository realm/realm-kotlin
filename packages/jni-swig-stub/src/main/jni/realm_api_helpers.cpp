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

inline jboolean jni_check_exception(JNIEnv *jenv = get_env()) {
    // FIXME https://github.com/realm/realm-kotlin/issues/665 This function is catching and swallowing
    //  the exception. This behavior could leave the SDK in an illegal state.
    if (jenv->ExceptionCheck()) {
        // Print the exception stacktrace in stderr.
        jenv->ExceptionDescribe();
        jenv->ExceptionClear();
        return false;
    }
    return true;
}

inline jboolean jni_check_exception_for_callback(JNIEnv *jenv = get_env()) {
    if (jenv->ExceptionCheck()) {
        // setting the user code error is only propagated on certain callbacks.
        jthrowable exception = jenv->ExceptionOccurred();
        jenv->ExceptionClear();
        // This global ref would be released once core has propagated the exception back
        // see: create_java_exception and convert_to_jvm_sync_error
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
            static_cast<jobject>(error.user_code_error)
    );
    if (error.user_code_error) {
        (jenv)->DeleteGlobalRef(static_cast<jobject>(error.user_code_error));
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
    bool failed = jni_check_exception_for_callback(env);
    env->PopLocalFrame(NULL);
    return failed;
}

// TODO OPTIMIZE Abstract pattern for all notification registrations for collections that receives
//  changes as realm_collection_changes_t.
realm_notification_token_t *
register_results_notification_cb(realm_results_t *results,
                                 int64_t key_path_array_ptr,
                                 jobject callback) {
    auto jenv = get_env();
    static JavaMethod on_change_method(jenv, JavaClassGlobalDef::notification_callback(),
                                       "onChange", "(J)V");

    return realm_results_add_notification_callback(
            results,
            // Use the callback as user data
            static_cast<jobject>(get_env()->NewGlobalRef(callback)),
            [](void *userdata) {
                get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
            },
            reinterpret_cast<realm_key_path_array_t*>(key_path_array_ptr),
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
    static JavaMethod on_change_method(jenv, JavaClassGlobalDef::notification_callback(),
                                       "onChange", "(J)V");
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
    static JavaMethod on_change_method(jenv, JavaClassGlobalDef::notification_callback(),
                                       "onChange", "(J)V");
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
    static JavaMethod on_change_method(jenv, JavaClassGlobalDef::notification_callback(),
                                       "onChange", "(J)V");
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
register_notification_cb(
        int64_t collection_ptr,
        realm_collection_type_e collection_type,
        int64_t key_path_array_ptr,
        jobject callback
) {
    auto user_data = static_cast<jobject>(get_env()->NewGlobalRef(callback));
    auto user_data_free = [](void *userdata) {
        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
    };

    switch (collection_type) {
        case RLM_COLLECTION_TYPE_NONE:
            return realm_object_add_notification_callback(
                    reinterpret_cast<realm_object_t*>(collection_ptr),
                    user_data, // Use the callback as user data
                    user_data_free,
                    (key_path_array_ptr == 0) ? NULL : reinterpret_cast<realm_key_path_array_t*>(key_path_array_ptr),
                    get_on_object_change()
            );
        case RLM_COLLECTION_TYPE_LIST: return realm_list_add_notification_callback(
                    reinterpret_cast<realm_list_t*>(collection_ptr),
                    user_data, // Use the callback as user data
                    user_data_free,
                    reinterpret_cast<realm_key_path_array_t*>(key_path_array_ptr),
                    get_on_collection_change()
            );
        case RLM_COLLECTION_TYPE_SET: return realm_set_add_notification_callback(
                    reinterpret_cast<realm_set_t*>(collection_ptr),
                    user_data, // Use the callback as user data
                    user_data_free,
                    reinterpret_cast<realm_key_path_array_t*>(key_path_array_ptr),
                    get_on_collection_change()
            );
        case RLM_COLLECTION_TYPE_DICTIONARY: return realm_dictionary_add_notification_callback(
                    reinterpret_cast<realm_dictionary_t*>(collection_ptr),
                    user_data, // Use the callback as user data
                    user_data_free,
                    reinterpret_cast<realm_key_path_array_t*>(key_path_array_ptr),
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
        // shutdown property. See https://github.com/realm/realm-core/issues/6429
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

bool realm_should_compact_callback(void* userdata, uint64_t total_bytes, uint64_t used_bytes) {
    auto env = get_env(true);
    static JavaClass java_should_compact_class(env, "io/realm/kotlin/internal/interop/CompactOnLaunchCallback");
    static JavaMethod java_should_compact_method(env, java_should_compact_class, "invoke", "(JJ)Z");

    jobject callback = static_cast<jobject>(userdata);
    jboolean result = env->CallBooleanMethod(callback, java_should_compact_method, jlong(total_bytes), jlong(used_bytes));
    return jni_check_exception_for_callback(env) && result;
}

bool realm_data_initialization_callback(void* userdata, realm_t*) {
    auto env = get_env(true);
    static JavaClass java_data_init_class(env, "io/realm/kotlin/internal/interop/DataInitializationCallback");
    static JavaMethod java_data_init_method(env, java_data_init_class, "invoke", "()V");

    jobject callback = static_cast<jobject>(userdata);
    env->CallVoidMethod(callback, java_data_init_method);
    return jni_check_exception_for_callback(env);
}

void set_log_callback(jobject log_callback) {
auto jenv = get_env(false);
realm_set_log_callback([](void *userdata, const char *category, realm_log_level_e level, const char *message) {
                               auto log_callback = static_cast<jobject>(userdata);
                               auto jenv = get_env(true);


                               auto java_level = static_cast<jshort>(level);
                               static JavaMethod log_method(jenv,
                                                            JavaClassGlobalDef::log_callback(),
                                                            "log",
                                                    "(SLjava/lang/String;Ljava/lang/String;)V");

                               push_local_frame(jenv, 2);
                               jstring j_message = NULL;
                               try {
                                   j_message = to_jstring(jenv, message);
                               } catch (RuntimeError exception) {
                                   std::ostringstream ret;
                                   ret << "Invalid data: " << exception.reason();
                                   j_message = to_jstring(jenv, ret.str());
                               }
                               jenv->CallVoidMethod(log_callback, log_method, java_level, to_jstring(jenv, category), j_message);
                               jni_check_exception(jenv);
                               jenv->PopLocalFrame(NULL);
                          },
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

jobjectArray realm_get_log_category_names() {
    JNIEnv* env = get_env(true);

    size_t namesCount = realm_get_category_names(0, nullptr);

    const char** category_names = new const char*[namesCount];
    realm_get_category_names(namesCount, category_names);

    auto array = env->NewObjectArray(namesCount, JavaClassGlobalDef::java_lang_string(), nullptr);

    for(size_t i = 0; i < namesCount; i++) {
        jstring string = env->NewStringUTF(category_names[i]);
        env->SetObjectArrayElement(array, i, string);
    }

    delete[] category_names;

    return array;
}
