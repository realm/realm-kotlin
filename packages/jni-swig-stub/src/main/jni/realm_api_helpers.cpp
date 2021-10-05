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

using namespace realm::jni_util;

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

inline void jni_check_exception(JNIEnv *jenv = get_env()) {
    if (jenv->ExceptionCheck()) {
        jenv->ExceptionDescribe();
        throw std::runtime_error("An unexpected Error was thrown from Java.");
    }
}

class CustomJVMScheduler : public realm::util::Scheduler {
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

    void notify() override {
        auto jenv = get_env(true);
        jni_check_exception(jenv);
        jenv->CallVoidMethod(m_jvm_dispatch_scheduler, m_notify_method,
                             reinterpret_cast<jlong>(&m_callback));
    }

    void set_notify_callback(std::function<void()> fn) override {
        m_callback = std::move(fn);
    }

    bool is_on_thread() const noexcept override {
        return m_id == std::this_thread::get_id();
    }

    bool is_same_as(const Scheduler *other) const noexcept override {
        auto o = dynamic_cast<const CustomJVMScheduler *>(other);
        return (o && (o->m_id == m_id));
    }

    bool can_deliver_notifications() const noexcept override {
        return true;
    }

private:
    std::function<void()> m_callback;
    std::thread::id m_id;
    jmethodID m_notify_method;
    jobject m_jvm_dispatch_scheduler;
};

// Note: using jlong here will create a linker issue
// Undefined symbols for architecture x86_64:
//  "invoke_core_notify_callback(long long, long long)", referenced from:
//      _Java_io_realm_internal_interop_realmcJNI_invoke_1core_1notify_1callback in realmc.cpp.o
//ld: symbol(s) not found for architecture x86_64
//
// I suspect this could be related to the fact that jni.h defines jlong differently between Android (typedef int64_t)
// and JVM which is a (typedef long long) resulting in a different signature of the method that could be found by the linker.
void invoke_core_notify_callback(int64_t core_notify_function) {
    auto notify = reinterpret_cast<std::function<void()> *>(core_notify_function);
    (*notify)();
}

realm_t *open_realm_with_scheduler(int64_t config_ptr, jobject dispatchScheduler) {
    auto *cfg = reinterpret_cast<realm_config_t * >(config_ptr);
    // copy construct to not set the scheduler on the original Conf which could be used
    // to open Frozen Realm for instance.
    auto copyConf = *cfg;
    copyConf.scheduler = std::make_shared<CustomJVMScheduler>(dispatchScheduler);
    return realm_open(&copyConf);
}

void register_login_cb(realm_app_t *app, realm_app_credentials_t *credentials, jobject callback) {
    auto jenv = get_env();
    // TODO OPTIMIZE Makes multiple lookups of CinteropCallback, but at least only once when
    //  initializing static variables
    //  https://github.com/realm/realm-kotlin/issues/460
    static jmethodID on_success_method = lookup(jenv, "io/realm/internal/interop/CinteropCallback",
                                                "onSuccess",
                                                "(Lio/realm/internal/interop/NativePointer;)V");
    static jmethodID on_error_method = lookup(jenv, "io/realm/internal/interop/CinteropCallback",
                                              "onError", "(Ljava/lang/Throwable;)V");

    realm_app_log_in_with_credentials(
            app,
            credentials,
            // FIXME Refactor into generic handling of network requests, like
            //  https://github.com/realm/realm-java/blob/master/realm/realm-library/src/main/cpp/io_realm_internal_objectstore_OsApp.cpp#L192
            [](void *userdata, realm_user_t *user, realm_error_t *error) {
                auto jenv = get_env(true);

                if (jenv->ExceptionCheck()) {
                    jenv->CallVoidMethod(static_cast<jobject>(userdata),
                                         on_error_method,
                                         jenv->ExceptionOccurred());
                } else if (error) {
                    // TODO OPTIMIZE Make central global reference table of classes
                    //  https://github.com/realm/realm-kotlin/issues/460
                    jclass exception_class = jenv->FindClass("io/realm/mongodb/AppException");
                    static jmethodID exception_constructor = jenv->GetMethodID(exception_class, "<init>",
                                                                               "()V");

                    jobject throwable = jenv->NewObject(exception_class, exception_constructor);

                    jenv->CallVoidMethod(static_cast<jobject>(userdata),
                                         on_error_method,
                                         throwable);
                } else {
                    // TODO OPTIMIZE Make central global reference table of classes
                    //  https://github.com/realm/realm-kotlin/issues/460
                    jclass exception_class = jenv->FindClass("io/realm/internal/interop/LongPointerWrapper");
                    static jmethodID exception_constructor = jenv->GetMethodID(exception_class, "<init>", "(JZ)V");

                    jobject pointer = jenv->NewObject(exception_class, exception_constructor,
                                                      reinterpret_cast<jlong>(user), false);

                    jenv->CallVoidMethod(static_cast<jobject>(userdata),
                                         on_success_method,
                                         pointer);
                }
            },
            // Use the callback as user data
            static_cast<jobject>(get_env()->NewGlobalRef(callback)),
            [](void *userdata) {
                get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
            }
    );
}

static jobject send_request_via_jvm_transport(JNIEnv *jenv, jobject network_transport, const realm_http_request_t request) {
    static jmethodID m_send_request_method = lookup(jenv,
            "io/realm/internal/interop/sync/NetworkTransport",
            "sendRequest",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;Ljava/lang/String;Z)Lio/realm/internal/interop/sync/Response;"
    );

    // Prepare request fields to be consumable by JVM
    std::string method;
    switch (request.method) {
        case realm_http_method::RLM_HTTP_METHOD_GET:
            method = "get";
            break;
        case realm_http_method::RLM_HTTP_METHOD_POST:
            method = "post";
            break;
        case realm_http_method::RLM_HTTP_METHOD_PATCH:
            method = "patch";
            break;
        case realm_http_method::RLM_HTTP_METHOD_PUT:
            method = "put";
            break;
        case realm_http_method::RLM_HTTP_METHOD_DELETE:
            method = "delete";
            break;
    }

    // TODO OPTIMIZE Make central global reference table of classes
    //  https://github.com/realm/realm-kotlin/issues/460
    jclass mapClass = jenv->FindClass("java/util/HashMap");
    static jmethodID init = jenv->GetMethodID(mapClass, "<init>", "(I)V");
    static jmethodID put_method = jenv->GetMethodID(mapClass, "put",
                                             "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    size_t map_size = request.num_headers;
    jobject request_headers = jenv->NewObject(mapClass, init, (jsize) map_size);
    for (int i = 0; i < map_size; i++) {
        realm_http_header_t header_pair = request.headers[i];

        jstring key = to_jstring(jenv, header_pair.name);
        jstring value = to_jstring(jenv, header_pair.value);
        jenv->CallObjectMethod(request_headers, put_method, key, value);
        jenv->DeleteLocalRef(key);
        jenv->DeleteLocalRef(value);
    }

    // Send request and retrieve JVM response
    return jenv->CallObjectMethod(network_transport,
                                  m_send_request_method,
                                  to_jstring(jenv, method),
                                  to_jstring(jenv, request.url),
                                  request_headers,
                                  to_jstring(jenv, request.body),
                                  jboolean(request.uses_refresh_token)
    );
}

static void pass_jvm_response_to_core(JNIEnv *jenv,
                                      jobject j_response,
                                      void *completion_data,
                                      realm_http_completion_func_t completion_callback) {
    static jclass response_class = jenv->FindClass("io/realm/internal/interop/sync/Response");
    static jmethodID get_http_code_method = jenv->GetMethodID(response_class, "getHttpResponseCode", "()I");
    static jmethodID get_custom_code_method = jenv->GetMethodID(response_class, "getCustomResponseCode", "()I");
    static jmethodID get_headers_method = jenv->GetMethodID(response_class, "getJNIFriendlyHeaders",
                                                            "()[Ljava/lang/String;");
    static jmethodID get_body_method = jenv->GetMethodID(response_class, "getBody", "()Ljava/lang/String;");

    // Extract JVM response fields
    jint http_code = jenv->CallIntMethod(j_response, get_http_code_method);
    jint custom_code = jenv->CallIntMethod(j_response, get_custom_code_method);
    JStringAccessor java_body(jenv, (jstring) jenv->CallObjectMethod(j_response, get_body_method), true);
    std::string body = java_body;

    JObjectArrayAccessor<JStringAccessor, jstring> java_headers(jenv, static_cast<jobjectArray>(jenv->CallObjectMethod(
            j_response, get_headers_method)));

    auto stacked_headers = std::vector<std::string>(); // Pins headers to function stack
    auto response_headers = std::vector<realm_http_header_t>();

    for (int i = 0; i < java_headers.size(); i = i + 2) {
        JStringAccessor key = java_headers[i];
        JStringAccessor value = java_headers[i + 1];
        stacked_headers.push_back(std::move(key));
        stacked_headers.push_back(std::move(value));

        realm_http_header header = {
                .name = stacked_headers[i].c_str(),
                .value = stacked_headers[i + 1].c_str()
        };

        response_headers.push_back(header);
    }

    // transform JVM response -> realm_http_response_t
    {
        realm_http_response_t response = {
                .status_code = http_code,
                .custom_status_code = custom_code,
                .headers = response_headers.data(),
                .num_headers = response_headers.size(),
                .body = body.c_str(),
                .body_size = body.size(),
        };
        completion_callback(completion_data, &response);
    }
}

/**
 * Perform a network request on JVM
 *
 * 1. Cast userdata to the network transport
 * 2. Transform core request to JVM request
 * 3. Perform request
 * 4. Transform JVM response to core respone
 */
static void network_request_lambda_function(void *userdata, // Network transport
                                            const realm_http_request_t request, // Request
                                            void *completion_data,// Call backs
                                            realm_http_completion_func_t completion_callback) {
    auto jenv = get_env(true);

    // Initialize pointer to JVM class and methods
    auto network_transport = static_cast<jobject>(userdata);

    try {
        jobject j_response = send_request_via_jvm_transport(jenv, network_transport, request);
        pass_jvm_response_to_core(jenv, j_response, completion_data, completion_callback);
    } catch (std::runtime_error &e) {
        // Runtime exception while processing the request/response
        realm_http_response_t response_error;
        // FIXME: validate we propagate the custom codes as an actual exception to the user
        // see: https://github.com/realm/realm-kotlin/issues/451
        response_error.custom_status_code = -4;
        response_error.num_headers = 0;
        response_error.body_size = 0;

        completion_callback(completion_data, &response_error);
    }
};

realm_http_transport_t *realm_network_transport_new(jobject network_transport) {
    auto jenv = get_env(false); // Always called from JVM
    return realm_http_transport_new(jenv->NewGlobalRef(network_transport),
                                    [](void *userdata) {
                                        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
                                    },
                                    &network_request_lambda_function);
}

static realm_logger_t* new_logger_lambda_function(void* userdata, realm_log_level_e level) {
    JNIEnv* jenv = get_env(true);
    auto logger_factory = static_cast<jobject>(userdata);

    static jmethodID get_logger_factory_method = lookup(jenv, "kotlin/jvm/functions/Function0", "invoke", "()Ljava/lang/Object;");
    jobject logger_ref = jenv->CallObjectMethod(logger_factory, get_logger_factory_method);
    jobject global_logger_ref = jenv->NewGlobalRef(logger_ref); // FIXME: Cleanup

    return realm_logger_new([](void* userdata, realm_log_level_e level, const char* message) {
                                auto logger = static_cast<jobject>(userdata);
                                auto jenv = get_env(true);
                                static jmethodID get_logger_log_method = lookup(jenv, "io/realm/log/RealmLogger", "log", "(SLjava/lang/String;)V");
                                jenv->CallVoidMethod(logger, get_logger_log_method, level, to_jstring(jenv, message));
                            },
                            [](void* userdata) {
                                auto logger = static_cast<jobject>(userdata);
                                auto jenv = get_env(true);

                                static jclass realm_logger_class = jenv->FindClass("io/realm/log/RealmLogger");
                                static jmethodID get_log_level_method = jenv->GetMethodID(realm_logger_class, "getLevel", "()Lio/realm/log/LogLevel;");
                                static jclass log_level_class = jenv->FindClass("io/realm/log/LogLevel");
                                static jmethodID get_priority_method = jenv->GetMethodID(log_level_class, "getPriority", "()I");

//                                jmethodID get_log_level_method = lookup(jenv, "io/realm/log/RealmLogger", "getLevel", "()Lio/realm/log/RealmLogger;");
                                jobject log_level = jenv->CallObjectMethod(logger, get_log_level_method);
//                                jmethodID get_priority_method = lookup(jenv, "io/realm/log/LogLeve", "getPriority", "()I");
                                jint j_log_level = jenv->CallIntMethod(log_level, get_priority_method);
                                if (j_log_level == RLM_LOG_LEVEL_ALL) {
                                    return RLM_LOG_LEVEL_ALL;
                                } else if (j_log_level == RLM_LOG_LEVEL_TRACE) {
                                    return RLM_LOG_LEVEL_TRACE;
                                } else if (j_log_level == RLM_LOG_LEVEL_DEBUG) {
                                    return RLM_LOG_LEVEL_DEBUG;
                                } else if (j_log_level == RLM_LOG_LEVEL_DETAIL) {
                                    return RLM_LOG_LEVEL_DETAIL;
                                } else if (j_log_level == RLM_LOG_LEVEL_INFO) {
                                    return RLM_LOG_LEVEL_INFO;
                                } else if (j_log_level == RLM_LOG_LEVEL_WARNING) {
                                    return RLM_LOG_LEVEL_WARNING;
                                } else if (j_log_level == RLM_LOG_LEVEL_ERROR) {
                                    return RLM_LOG_LEVEL_ERROR;
                                } else if (j_log_level == RLM_LOG_LEVEL_FATAL) {
                                    return RLM_LOG_LEVEL_FATAL;
                                } else if (j_log_level == RLM_LOG_LEVEL_OFF) {
                                    return RLM_LOG_LEVEL_OFF;
                                }
                                return RLM_LOG_LEVEL_OFF;
                            },
                            global_logger_ref,
                            [](void* userdata) {
                                get_env(true)->DeleteGlobalRef(
                                        static_cast<jobject>(userdata));
                            });
}

void
sync_config_set_logger(realm_sync_client_config_t* sync_config, jobject logger_factory) {
    auto jenv = get_env();
    return realm_sync_client_config_set_logger_factory(sync_config,
                                                       &new_logger_lambda_function,
                                                       static_cast<jobject>(jenv->NewGlobalRef(logger_factory)),
                                                       [](void *userdata) {
                                                           get_env(true)->DeleteGlobalRef(
                                                                   static_cast<jobject>(userdata));
                                                       }
    );
}
