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

// TODO refactor to use public C-API https://github.com/realm/realm-kotlin/issues/496
realm_t *open_realm_with_scheduler(int64_t config_ptr, jobject dispatchScheduler) {
    auto *cfg = reinterpret_cast<realm_config_t * >(config_ptr);
    // copy construct to not set the scheduler on the original Conf which could be used
    // to open Frozen Realm for instance.
    auto copyConf = *cfg;
    if (dispatchScheduler) {
        copyConf.scheduler = std::make_shared<CustomJVMScheduler>(dispatchScheduler);
    } else {
        copyConf.scheduler = realm::util::Scheduler::make_generic();
    }

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
            [](void* userdata, realm_user_t* user, const realm_app_error_t* error) {
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

                    // Remember to clone user object or else it will be invalidated right after we leave this callback
                    void* cloned_user = realm_clone(user);
                    jobject pointer = jenv->NewObject(exception_class, exception_constructor,
                                                      reinterpret_cast<jlong>(cloned_user), false);

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
    static JavaMethod m_send_request_method(jenv,
                                            JavaClassGlobalDef::network_transport_class(),
                                            "sendRequest",
                                            "(Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;Ljava/lang/String;)Lio/realm/internal/interop/sync/Response;");

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

    // Send request and retrieve JVM response
    return jenv->CallObjectMethod(network_transport,
                                  m_send_request_method,
                                  to_jstring(jenv, method),
                                  to_jstring(jenv, request.url),
                                  request_headers,
                                  to_jstring(jenv, request.body)
    );
}

static void pass_jvm_response_to_core(JNIEnv *jenv, jobject j_response, void* request_context) {
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
    auto response_headers = std::vector<realm_http_header_t>();

    for (int i = 0; i < java_headers.size(); i = i + 2) {
        JStringAccessor key = java_headers[i];
        JStringAccessor value = java_headers[i + 1];
        stacked_headers.push_back(std::move(key));
        stacked_headers.push_back(std::move(value));

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
        jobject j_response = send_request_via_jvm_transport(jenv, network_transport, request);
        pass_jvm_response_to_core(jenv, j_response, request_context);
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

                                                  static jclass core_log_level_enum_class = JavaClassGlobalDef::core_log_level();

                                                  jobject core_log_level;
                                                  if (level == RLM_LOG_LEVEL_ALL) {
                                                      static jfieldID level_all_field = jenv->GetStaticFieldID(core_log_level_enum_class, "RLM_LOG_LEVEL_ALL", "Lio/realm/internal/interop/CoreLogLevel;");
                                                      core_log_level = jenv->GetStaticObjectField(core_log_level_enum_class, level_all_field);
                                                  } else if (level == RLM_LOG_LEVEL_TRACE) {
                                                      static jfieldID level_trace_field = jenv->GetStaticFieldID(core_log_level_enum_class, "RLM_LOG_LEVEL_TRACE", "Lio/realm/internal/interop/CoreLogLevel;");
                                                      core_log_level = jenv->GetStaticObjectField(core_log_level_enum_class, level_trace_field);
                                                  } else if (level == RLM_LOG_LEVEL_DEBUG) {
                                                      static jfieldID level_debug_field = jenv->GetStaticFieldID(core_log_level_enum_class, "RLM_LOG_LEVEL_DEBUG", "Lio/realm/internal/interop/CoreLogLevel;");
                                                      core_log_level = jenv->GetStaticObjectField(core_log_level_enum_class, level_debug_field);
                                                  } else if (level == RLM_LOG_LEVEL_DETAIL) {
                                                      static jfieldID level_detail_field = jenv->GetStaticFieldID(core_log_level_enum_class, "RLM_LOG_LEVEL_DETAIL", "Lio/realm/internal/interop/CoreLogLevel;");
                                                      core_log_level = jenv->GetStaticObjectField(core_log_level_enum_class, level_detail_field);
                                                  } else if (level == RLM_LOG_LEVEL_INFO) {
                                                      static jfieldID level_info_field = jenv->GetStaticFieldID(core_log_level_enum_class, "RLM_LOG_LEVEL_INFO", "Lio/realm/internal/interop/CoreLogLevel;");
                                                      core_log_level = jenv->GetStaticObjectField(core_log_level_enum_class, level_info_field);
                                                  } else if (level == RLM_LOG_LEVEL_WARNING) {
                                                      static jfieldID level_warning_field = jenv->GetStaticFieldID(core_log_level_enum_class, "RLM_LOG_LEVEL_WARNING", "Lio/realm/internal/interop/CoreLogLevel;");
                                                      core_log_level = jenv->GetStaticObjectField(core_log_level_enum_class, level_warning_field);
                                                  } else if (level == RLM_LOG_LEVEL_ERROR) {
                                                      static jfieldID level_error_field = jenv->GetStaticFieldID(core_log_level_enum_class, "RLM_LOG_LEVEL_ERROR", "Lio/realm/internal/interop/CoreLogLevel;");
                                                      core_log_level = jenv->GetStaticObjectField(core_log_level_enum_class, level_error_field);
                                                  } else if (level == RLM_LOG_LEVEL_FATAL) {
                                                      static jfieldID level_fatal_field = jenv->GetStaticFieldID(core_log_level_enum_class, "RLM_LOG_LEVEL_FATAL", "Lio/realm/internal/interop/CoreLogLevel;");
                                                      core_log_level = jenv->GetStaticObjectField(core_log_level_enum_class, level_fatal_field);
                                                  } else if (level == RLM_LOG_LEVEL_OFF) {
                                                      static jfieldID level_off_field = jenv->GetStaticFieldID(core_log_level_enum_class, "RLM_LOG_LEVEL_OFF", "Lio/realm/internal/interop/CoreLogLevel;");
                                                      core_log_level = jenv->GetStaticObjectField(core_log_level_enum_class, level_off_field);
                                                  } else {
                                                      throw std::runtime_error("Invalid core log level: " + std::to_string(level));
                                                  }

                                                  static jclass log_callback_class = JavaClassGlobalDef::log_callback();
                                                  static jmethodID log_method = jenv->GetMethodID(log_callback_class, "log", "(Lio/realm/internal/interop/CoreLogLevel;Ljava/lang/String;)V");
                                                  jenv->CallVoidMethod(log_callback, log_method, core_log_level, to_jstring(jenv, message));
                                              },
                                              jenv->NewGlobalRef(log_callback), // userdata is the log callback
                                              [](void* userdata) {
                                                  get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
                                              });
}

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

jobject convert_exception(JNIEnv* jenv, const realm_sync_error_t error) {
    static JavaMethod pointer_wrapper_constructor(jenv,
                                                  JavaClassGlobalDef::app_exception(),
                                                  "<init>",
                                                  "()V");
    return jenv->NewObject(JavaClassGlobalDef::app_exception(),
                           pointer_wrapper_constructor);
}

void
sync_set_error_handler(realm_sync_config_t* sync_config, jobject error_handler){
    realm_sync_config_set_error_handler(sync_config,
                                        [](void* userdata, realm_sync_session_t* session, const realm_sync_error_t error) {
                                            auto jenv = get_env(true);

                                            jobject session_pointer_wrapper = wrap_pointer(jenv,reinterpret_cast<jlong>(session));
                                            jobject app_exception = convert_exception(jenv, error);

                                            static JavaMethod function2_invoke(jenv,
                                                                               JavaClassGlobalDef::kotlin_function2(),
                                                                               "invoke",
                                                                               "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

                                            jenv->CallObjectMethod(static_cast<jobject>(userdata), // Function0 type
                                                                 function2_invoke,
                                                                 session_pointer_wrapper,
                                                                 app_exception);
                                        },
                                        static_cast<jobject>(get_env()->NewGlobalRef(error_handler)),
                                        [](void *userdata) {
                                            get_env(true)->DeleteGlobalRef(
                                                    static_cast<jobject>(userdata));
                                        });
}
