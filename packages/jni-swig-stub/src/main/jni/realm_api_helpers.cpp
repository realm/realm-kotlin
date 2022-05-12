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

inline void jni_check_exception(JNIEnv *jenv = get_env()) {
    if (jenv->ExceptionCheck()) {
        jenv->ExceptionDescribe();
        jenv->ExceptionClear();
        throw std::runtime_error("An unexpected Error was thrown from Java.");
    }
}

void
realm_changed_callback(void* userdata) {
    auto env = get_env(true);
    static JavaClass java_callback_class(env, "kotlin/jvm/functions/Function0");
    static JavaMethod java_callback_method(env, java_callback_class, "invoke",
                                           "()Ljava/lang/Object;");
    // TODOO Align exceptions handling https://github.com/realm/realm-kotlin/issues/665
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
    jobject schema_pointer_wrapper = wrap_pointer(env,reinterpret_cast<jlong>(new_schema));
    // TODOO Align exceptions handling https://github.com/realm/realm-kotlin/issues/665
    jni_check_exception(env);
    env->CallObjectMethod(static_cast<jobject>(userdata), java_callback_method, schema_pointer_wrapper);
    jni_check_exception(env);
}

bool migration_callback(void *userdata, realm_t *old_realm, realm_t *new_realm,
                        const realm_schema_t *schema) {
    auto env = get_env(true);
    static JavaClass java_callback_class(env, "io/realm/internal/interop/MigrationCallback");
    static JavaMethod java_callback_method(env, java_callback_class, "migrate",
                                           "(Lio/realm/internal/interop/NativePointer;Lio/realm/internal/interop/NativePointer;Lio/realm/internal/interop/NativePointer;)Z");
    // These realm/schema pointers are only valid for the duraction of the
    // migration so don't let ownership follow the NativePointer-objects
    bool result = env->CallBooleanMethod(static_cast<jobject>(userdata), java_callback_method,
                                        wrap_pointer(env, reinterpret_cast<jlong>(old_realm), false),
                                        wrap_pointer(env, reinterpret_cast<jlong>(new_realm), false),
                                        wrap_pointer(env, reinterpret_cast<jlong>(schema))
    );
    jni_check_exception(env);
    return result;
}

// TODO OPTIMIZE Abstract pattern for all notification registrations for collections that receives
//  changes as realm_collection_changes_t.
realm_notification_token_t *
register_results_notification_cb(realm_results_t *results, jobject callback) {
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
            NULL, // See https://github.com/realm/realm-kotlin/issues/661
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
            [](void *userdata,
               const realm_async_error_t *async_error) {
                // TODO Propagate errors to callback
                //  https://github.com/realm/realm-kotlin/issues/303
            },
            // C-API currently uses the realm's default scheduler no matter what passed here
            NULL
    );
}

// TODO OPTIMIZE Abstract pattern for all notification registrations for collections that receives
//  changes as realm_collection_changes_t.
realm_notification_token_t *
register_list_notification_cb(realm_list_t *list, jobject callback) {
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
            NULL, // See https://github.com/realm/realm-kotlin/issues/661
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
            [](void *userdata,
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
            NULL, // See https://github.com/realm/realm-kotlin/issues/661
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
            [](void *userdata,
               const realm_async_error_t *async_error) {
                // TODO Propagate errors to callback
                //  https://github.com/realm/realm-kotlin/issues/303
            },
            // C-API currently uses the realm's default scheduler no matter what passed here
            NULL
    );
}


class CustomJVMScheduler {
public:
    CustomJVMScheduler(jobject dispatchScheduler) : m_id(std::this_thread::get_id()) {
        JNIEnv *jenv = get_env();
        jclass jvm_scheduler_class = jenv->FindClass("io/realm/internal/interop/JVMScheduler");
        m_notify_method = jenv->GetMethodID(jvm_scheduler_class, "notifyCore", "(J)V");
        m_jvm_dispatch_scheduler = jenv->NewGlobalRef(dispatchScheduler);
    }

    ~CustomJVMScheduler() {
        get_env(true)->DeleteGlobalRef(m_jvm_dispatch_scheduler);
    }

    void set_scheduler(realm_scheduler_t* scheduler) {
        m_scheduler = scheduler;
    }

    void notify() {
        auto jenv = get_env(true);
        jni_check_exception(jenv);
        jenv->CallVoidMethod(m_jvm_dispatch_scheduler, m_notify_method,
                             reinterpret_cast<jlong>(m_scheduler));
    }

    bool is_on_thread() const noexcept {
        return m_id == std::this_thread::get_id();
    }

    bool can_invoke() const noexcept {
        return true;
    }


private:
    std::thread::id m_id;
    jmethodID m_notify_method;
    jobject m_jvm_dispatch_scheduler;
    realm_scheduler_t *m_scheduler;
};

// Note: using jlong here will create a linker issue
// Undefined symbols for architecture x86_64:
//  "invoke_core_notify_callback(long long, long long)", referenced from:
//      _Java_io_realm_internal_interop_realmcJNI_invoke_1core_1notify_1callback in realmc.cpp.o
//ld: symbol(s) not found for architecture x86_64
//
// I suspect this could be related to the fact that jni.h defines jlong differently between Android (typedef int64_t)
// and JVM which is a (typedef long long) resulting in a different signature of the method that could be found by the linker.
void invoke_core_notify_callback(int64_t scheduler) {
    realm_scheduler_perform_work(reinterpret_cast<realm_scheduler_t *>(scheduler));
}

realm_t *open_realm_with_scheduler(int64_t config_ptr, jobject dispatchScheduler) {
    auto config = reinterpret_cast<realm_config_t *>(config_ptr);
    if (dispatchScheduler) {
        auto jvmScheduler = new CustomJVMScheduler(dispatchScheduler);
        auto scheduler = realm_scheduler_new(
                jvmScheduler,
                [](void *userdata) { delete(static_cast<CustomJVMScheduler *>(userdata)); },
                [](void *userdata) { static_cast<CustomJVMScheduler *>(userdata)->notify(); },
                [](void *userdata) { return static_cast<CustomJVMScheduler *>(userdata)->is_on_thread(); },
                [](const void *userdata, const void *userdata_other) { return userdata == userdata_other; },
                [](void *userdata) { return static_cast<CustomJVMScheduler *>(userdata)->can_invoke(); }
        );
        jvmScheduler->set_scheduler(scheduler);
        realm_config_set_scheduler(config, scheduler);
    } else {
        // TODO refactor to use public C-API https://github.com/realm/realm-kotlin/issues/496
        auto scheduler =  new realm_scheduler_t{realm::util::Scheduler::make_generic()};
        realm_config_set_scheduler(config, scheduler);
    }
    return realm_open(config);
}

jobject convert_to_jvm_app_error(JNIEnv* env, const realm_app_error_t* error) {
    static JavaMethod app_error_constructor(env,
                                                JavaClassGlobalDef::app_error(),
                                                "<init>",
                                                "(IIILjava/lang/String;Ljava/lang/String;)V");
    jint category = static_cast<jint>(error->error_category);
    jint code = static_cast<jint>(error->error_code);
    jint httpCode = static_cast<jint>(error->http_status_code);
    jstring message = to_jstring(env, error->message);
    jstring serverLogs = to_jstring(env, error->link_to_server_logs);
    return env->NewObject(JavaClassGlobalDef::app_error(),
                          app_error_constructor,
                          category,
                          code,
                          httpCode,
                          message,
                          serverLogs);
}

void app_complete_void_callback(void *userdata, const realm_app_error_t *error) {
    auto env = get_env(true);
    static JavaClass java_callback_class(env, "io/realm/internal/interop/AppCallback");
    static JavaMethod java_notify_onerror(env, java_callback_class, "onError",
                                          "(Lio/realm/internal/interop/sync/AppError;)V");
    static JavaMethod java_notify_onsuccess(env, java_callback_class, "onSuccess",
                                            "(Ljava/lang/Object;)V");
    static JavaClass unit_class(env, "kotlin/Unit");
    static JavaMethod unit_constructor(env, unit_class, "<init>", "()V");

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        throw std::runtime_error("An unexpected Error was thrown from Java. See LogCat");
    } else if (error) {
        jobject app_error = convert_to_jvm_app_error(env, error);
        env->CallVoidMethod(static_cast<jobject>(userdata), java_notify_onerror, app_error);
    } else {
        jobject unit = env->NewObject(unit_class, unit_constructor);
        env->CallVoidMethod(static_cast<jobject>(userdata), java_notify_onsuccess, unit);
    }
}

void app_complete_result_callback(void* userdata, void* result, const realm_app_error_t* error) {
    auto env = get_env(true);
    static JavaClass java_callback_class(env, "io/realm/internal/interop/AppCallback");
    static JavaMethod java_notify_onerror(env, java_callback_class, "onError",
                                          "(Lio/realm/internal/interop/sync/AppError;)V");
    static JavaMethod java_notify_onsuccess(env, java_callback_class, "onSuccess",
                                            "(Ljava/lang/Object;)V");

    static JavaClass native_pointer_class(env, "io/realm/internal/interop/LongPointerWrapper");
    static JavaMethod native_pointer_constructor(env, native_pointer_class, "<init>", "(JZ)V");

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
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
}

bool realm_should_compact_callback(void* userdata, uint64_t total_bytes, uint64_t used_bytes) {
    auto env = get_env(true);
    static JavaClass java_should_compact_class(env, "io/realm/internal/interop/CompactOnLaunchCallback");
    static JavaMethod java_should_compact_method(env, java_should_compact_class, "invoke", "(JJ)Z");

    jobject callback = static_cast<jobject>(userdata);
    jboolean result = env->CallBooleanMethod(callback, java_should_compact_method, jlong(total_bytes), jlong(used_bytes));
    jni_check_exception(env);
    return result;
}

void realm_data_initialization_callback(void* userdata) {
    auto env = get_env(true);
    static JavaClass java_data_init_class(env, "io/realm/internal/interop/DataInitializationCallback");
    static JavaMethod java_data_init_method(env, java_data_init_class, "invoke", "()Z");

    jobject callback = static_cast<jobject>(userdata);
    env->CallVoidMethod(callback, java_data_init_method);
    jni_check_exception(env);
}

static void send_request_via_jvm_transport(JNIEnv *jenv, jobject network_transport, const realm_http_request_t request, jobject j_response_callback) {
    static JavaMethod m_send_request_method(jenv,
                                            JavaClassGlobalDef::network_transport_class(),
                                            "sendRequest",
                                            "(Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;Ljava/lang/String;Lio/realm/internal/interop/sync/ResponseCallback;)V");

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
    jobject request_headers = jenv->NewObject(JavaClassGlobalDef::java_util_hashmap(), init, (jsize) map_size);
    for (int i = 0; i < map_size; i++) {
        realm_http_header_t header_pair = request.headers[i];

        jstring key = to_jstring(jenv, header_pair.name);
        jstring value = to_jstring(jenv, header_pair.value);
        jenv->CallObjectMethod(request_headers, put_method, key, value);
        jenv->DeleteLocalRef(key);
        jenv->DeleteLocalRef(value);
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
                                                                           "(Lio/realm/internal/interop/sync/NetworkTransport;J)V");
        jobject response_callback = jenv->NewObject(response_callback_class,
                                                    response_callback_constructor,
                                                    reinterpret_cast<jobject>(userdata),
                                                    reinterpret_cast<jlong>(request_context));

        send_request_via_jvm_transport(jenv, network_transport, request, response_callback);
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

void set_log_callback(realm_sync_client_config_t* sync_client_config, jobject log_callback) {
    auto jenv = get_env(false);
    realm_sync_client_config_set_log_callback(sync_client_config,
                                              [](void* userdata, realm_log_level_e level, const char* message) {
                                                  auto log_callback = static_cast<jobject>(userdata);
                                                  auto jenv = get_env(true);
                                                  static JavaMethod log_method(jenv,
                                                                               JavaClassGlobalDef::sync_log_callback(),
                                                                               "log",
                                                                               "(SLjava/lang/String;)V");
                                                  jenv->CallVoidMethod(log_callback, log_method, level, to_jstring(jenv, message));
                                              },
                                              jenv->NewGlobalRef(log_callback), // userdata is the log callback
                                              [](void* userdata) {
                                                  get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
                                              });
}

jobject convert_to_jvm_sync_error(JNIEnv* jenv, const realm_sync_error_t& error) {

    static JavaMethod sync_error_constructor(jenv,
                                             JavaClassGlobalDef::sync_error(),
                                             "<init>",
                                             "(IILjava/lang/String;Ljava/lang/String;ZZ)V");

    jint category = static_cast<jint>(error.error_code.category);
    jint value = error.error_code.value;
    jstring msg = to_jstring(jenv, error.error_code.message);
    jstring detailed_msg = to_jstring(jenv, error.detailed_message);
    jboolean is_fatal = error.is_fatal;
    jboolean is_unrecognized_by_client = error.is_unrecognized_by_client;

    return jenv->NewObject(JavaClassGlobalDef::sync_error(),
                           sync_error_constructor,
                           category, value, msg, detailed_msg, is_fatal, is_unrecognized_by_client);
}

void sync_set_error_handler(realm_sync_config_t* sync_config, jobject error_handler) {
    realm_sync_config_set_error_handler(sync_config,
                                        [](void* userdata, realm_sync_session_t* session, const realm_sync_error_t error) {
                                            auto jenv = get_env(true);
                                            auto sync_error_callback = static_cast<jobject>(userdata);

                                            jobject session_pointer_wrapper = wrap_pointer(jenv,reinterpret_cast<jlong>(session));
                                            jobject sync_error = convert_to_jvm_sync_error(jenv, error);

                                            static JavaMethod sync_error_method(jenv,
                                                                                JavaClassGlobalDef::sync_error_callback(),
                                                                                "onSyncError",
                                                                                "(Lio/realm/internal/interop/NativePointer;Lio/realm/internal/interop/sync/SyncError;)V");
                                            jenv->CallVoidMethod(sync_error_callback,
                                                                 sync_error_method,
                                                                 session_pointer_wrapper,
                                                                 sync_error);
                                        },
                                        static_cast<jobject>(get_env()->NewGlobalRef(error_handler)),
                                        [](void *userdata) {
                                            get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
                                        });
}

void transfer_completion_callback(void* userdata, realm_sync_error_code_t* error) {
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
        jint category = static_cast<jint>(error->category);
        jint value = error->value;
        jstring msg = to_jstring(env, error->message);
        env->CallVoidMethod(static_cast<jobject>(userdata), java_error_callback_method, category, value, msg);
    } else {
        env->CallVoidMethod(static_cast<jobject>(userdata), java_success_callback_method);
    }
    jni_check_exception(env);
}

void
before_client_reset(void* userdata, realm_t* before_realm) {
    auto env = get_env(true);
    static JavaClass java_callback_class(env, "io/realm/internal/interop/SyncBeforeClientResetHandler");
    static JavaMethod java_callback_method(env, java_callback_class, "onBeforeReset",
                                           "(Lio/realm/internal/interop/NativePointer;)V");
    jni_check_exception(env);
    env->CallObjectMethod(static_cast<jobject>(userdata), java_callback_method, before_realm);
    jni_check_exception(env);
}

void
after_client_reset(void* userdata, realm_t* before_realm, realm_t* after_realm,
                   bool did_recover) {
    auto env = get_env(true);
    static JavaClass java_callback_class(env, "io/realm/internal/interop/SyncAfterClientResetHandler");
    static JavaMethod java_callback_method(env, java_callback_class, "onAfterReset",
                                           "(Lio/realm/internal/interop/NativePointer;Lio/realm/internal/interop/NativePointer;Z)V");
    jni_check_exception(env);
    env->CallObjectMethod(static_cast<jobject>(userdata), java_callback_method, before_realm, after_realm, did_recover);
    jni_check_exception(env);
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
