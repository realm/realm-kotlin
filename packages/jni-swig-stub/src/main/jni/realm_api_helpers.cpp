//
// Created by Clemente Tort Barbero on 6/9/21.
//

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

realm_app_config_t *
new_app_config(const char* app_id, jobject app_instance) {
    auto jenv = get_env();

    return realm_app_config_new(app_id,
                                [] (void* userdata) // transport generator
                                {
                                    // Called when Core requires to access the App's network transport
                                    auto jenv = get_env(true);
                                    jobject app_ref = static_cast<jobject>(userdata);
                                    // TODO: Replace with proper classes
                                    static jclass app_class = jenv->FindClass("io/realm/mongodb/AppImpl");
                                    static jmethodID get_network_transport_method = jenv->GetMethodID(app_class, "getNetworkTransport", "()Lio/realm/mongodb/sync/NetworkTransport;");
                                    jobject network_transport = jenv->CallObjectMethod(app_ref, get_network_transport_method);

                                    return realm_http_transport_new(network_transport, //ktornetwork
                                                                    [] (void* userdata)
                                                                    {
                                                                        get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
                                                                    },
                                                                    // realm_http_request_func_t
                                                                    [] (void* userdata,             // Network transport
                                                                        const realm_http_request_t, // Request
                                                                        void* completion_data,      // Response
                                                                        realm_http_completion_func_t completion_callback)
                                                                    {
                                                                        // Called to perform an actual request
                                                                        jobject network_transport = static_cast<jobject>(userdata);
                                                                        // Call network transport with realm_http_request_t

                                                                        // transform JVM response -> realm_http_response_t
//                                                                        auto response = static_cast<realm_http_response*>(completion_data);
//                                                                        const realm_http_response_t response = {};
                                                                        // Notify response ready
//                                                                        realm_http_completion_func_t({});
                                                                    });
                                },
                                static_cast<jobject>(jenv->NewGlobalRef(app_instance)), // hold app reference
                                [](void *userdata) { // free app reference
                                    get_env(true)->DeleteGlobalRef(static_cast<jobject>(userdata));
                                }
    );
}
