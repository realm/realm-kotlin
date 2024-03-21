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

#ifndef TEST_REALM_API_HELPERS_H
#define TEST_REALM_API_HELPERS_H

#include "realm.h"
#include "env_utils.h"
#include "java_class_global_def.hpp"
#include "utils.h"

bool throw_last_error_as_java_exception(JNIEnv *jenv);

void
realm_changed_callback(void* userdata);

void
schema_changed_callback(void* userdata, const realm_schema_t* new_schema);

bool
migration_callback(void* userdata, realm_t* old_realm, realm_t* new_realm,
                   const realm_schema_t* schema);

realm_notification_token_t*
register_results_notification_cb(
        realm_results_t *results,
        int64_t key_path_array_ptr,
        jobject callback);

realm_notification_token_t *
register_notification_cb(
        int64_t collection_ptr,
        realm_collection_type_e collection_type,
        int64_t key_path_array_ptr,
        jobject callback);

realm_http_transport_t*
realm_network_transport_new(jobject network_transport);

void
set_log_callback(jobject log_callback);

realm_scheduler_t*
realm_create_scheduler(jobject dispatchScheduler);

bool
realm_should_compact_callback(void* userdata, uint64_t total_bytes, uint64_t used_bytes);

bool
realm_data_initialization_callback(void* userdata, realm_t* realm);

void
invoke_core_notify_callback(int64_t core_notify_function);

void
app_complete_void_callback(void* userdata, const realm_app_error_t* error);

void
app_complete_result_callback(void* userdata, void* result, const realm_app_error_t* error);

void
sync_set_error_handler(realm_sync_config_t* sync_config, jobject error_handler);

void
complete_http_request(void* request_context, jobject j_response);

void
transfer_completion_callback(void* userdata, realm_error_t* error);

void
realm_subscriptionset_changed_callback(void* userdata,
                                       realm_flx_sync_subscription_set_state_e state);

void
realm_async_open_task_callback(void* userdata,
                               realm_thread_safe_reference_t* realm,
                               const realm_async_error_t* error);

void
realm_subscriptionset_changed_callback(void* userdata,
                                       realm_flx_sync_subscription_set_state_e state);

bool
before_client_reset(void* userdata, realm_t* before_realm);

bool
after_client_reset(void* userdata, realm_t* before_realm,
                   realm_thread_safe_reference_t* after_realm, bool did_recover);

void
sync_before_client_reset_handler(realm_sync_config_t* config, jobject before_handler);

void
sync_after_client_reset_handler(realm_sync_config_t* config, jobject after_handler);

void
realm_sync_session_progress_notifier_callback(void *userdata, uint64_t transferred_bytes, uint64_t total_bytes);

void
realm_sync_session_connection_state_change_callback(void *userdata, realm_sync_connection_state_e old_state, realm_sync_connection_state_e new_state);

// Explicit clean up method for releasing heap allocated data of a realm_value_t instance
void
realm_value_t_cleanup(realm_value_t* value);

void
app_apikey_callback(realm_userdata_t userdata, realm_app_user_apikey_t*, const realm_app_error_t*);

void
app_apikey_list_callback(realm_userdata_t userdata, realm_app_user_apikey_t[], size_t count, realm_app_error_t*);

void
app_string_callback(realm_userdata_t userdata, const char* serialized_ejson_response, const realm_app_error_t*);

jlong
realm_sync_session_register_progress_notifier_wrapper(
        realm_sync_session_t* session, realm_sync_progress_direction_e direction, bool is_streaming,
        jobject callback
);

void
realm_sync_thread_created(realm_userdata_t userdata);

void
realm_sync_thread_destroyed(realm_userdata_t userdata);

void
realm_sync_thread_error(realm_userdata_t userdata, const char* error);

realm_scheduler_t*
realm_create_generic_scheduler();

void
realm_property_info_t_cleanup(realm_property_info_t* value);

void
realm_class_info_t_cleanup(realm_class_info_t * value);

realm_sync_socket_t* realm_sync_websocket_new(int64_t sync_client_config_ptr, jobject websocket_transport);

void realm_sync_websocket_callback_complete(bool cancelled, int64_t lambda_ptr, int status, const char* reason);

void realm_sync_websocket_connected(int64_t observer_ptr, const char* protocol);

void realm_sync_websocket_error(int64_t observer_ptr);

bool realm_sync_websocket_message(int64_t observer_ptr, jbyteArray data, size_t size);

void realm_sync_websocket_closed(int64_t observer_ptr, bool was_clean, int error_code, const char* reason);

jobjectArray realm_get_log_category_names();

#endif //TEST_REALM_API_HELPERS_H
