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

using namespace realm::jni_util;

realm_notification_token_t *
register_results_notification_cb(realm_results_t *results, jobject callback) {
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
register_list_notification_cb(realm_list_t *list, jobject callback) {
    auto jenv = get_env();
    static jclass notification_class = jenv->FindClass("io/realm/interop/NotificationCallback");
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

/**
 * Perform a network request on JVM
 *
 * 1. Cast userdata to the network transport
 * 2. Transform core request to JVM request
 * 3. Perform request
 * 4. Transform JVM request to core request
 */
realm_http_request_func_t network_request_lambda_function = [](void *userdata, // Network transport
                                                               const realm_http_request_t request,             // Request
                                                               void *completion_data,                  // Call backs
                                                               realm_http_completion_func_t completion_callback) {
    auto jenv = get_env(true);

    // Initialize pointer to JVM class and me
    jobject network_transport = static_cast<jobject>(userdata);
    static jclass network_transport_class = jenv->FindClass("io/realm/mongodb/sync/NetworkTransport");
    static jmethodID m_send_request_method = jenv->GetMethodID(
            network_transport_class,
            "sendRequest",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;Ljava/lang/String;JZ)Lio/realm/mongodb/sync/Response;"
            );

    std::string method;
    switch(request.method) {
        case realm_http_method::RLM_HTTP_METHOD_GET: method = "get"; break;
        case realm_http_method::RLM_HTTP_METHOD_POST: method = "post"; break;
        case realm_http_method::RLM_HTTP_METHOD_PATCH: method = "patch"; break;
        case realm_http_method::RLM_HTTP_METHOD_PUT: method = "put"; break;
        case realm_http_method::RLM_HTTP_METHOD_DELETE: method = "delete"; break;
    }

    // Create headers
    static jclass mapClass = jenv->FindClass("java/util/HashMap");
    static jmethodID init = jenv->GetMethodID(mapClass, "<init>", "(I)V");
    static jmethodID put_method = jenv->GetMethodID(mapClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

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

    jobject jresponse = jenv->CallObjectMethod(network_transport,
                m_send_request_method,
                to_jstring(jenv, method),
                to_jstring(jenv, request.url),
                request_headers,
                to_jstring(jenv, request.body),
                static_cast<jlong>(request.timeout_ms),
                jboolean(request.uses_refresh_token)
        );

    // TODO: transform JVM response -> realm_http_response_t
    realm_http_response_t response{}; // Fill up with the JVM response data

    // Notify response ready
    completion_callback(completion_data, std::move(response));
};

/**
 * Provides access to the network transport to core
 *
 * 1. Cast userdata to the network transport factory
 * 2. Create a global reference for the network transport
 */
realm_http_transport_factory_func_t new_network_transport_lambda_function = [](void *userdata) // transport generator
{
    auto jenv = get_env(true);
    jobject app_ref = static_cast<jobject>(userdata);

    // TODO: Replace with proper classes and methods
    static jclass app_class = jenv->FindClass("kotlin/jvm/functions/Function0");
    static jmethodID get_network_transport_method = jenv->GetMethodID(app_class, "invoke", "()Ljava/lang/Object;");
    jobject network_transport = jenv->CallObjectMethod(app_ref, get_network_transport_method);

    return realm_http_transport_new(jenv->NewGlobalRef(network_transport),
                                    [](void *userdata) {
                                        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
                                    },
                                    network_request_lambda_function);
};

realm_app_config_t *
new_app_config(const char *app_id, jobject network_factory) {
    auto jenv = get_env();

    return realm_app_config_new(app_id,
                                new_network_transport_lambda_function,
                                static_cast<jobject>(jenv->NewGlobalRef(network_factory)), // keep app reference
                                [](void *userdata) {
                                    get_env(true)->DeleteGlobalRef(
                                            static_cast<jobject>(userdata)); // free app reference
                                }
    );
}

