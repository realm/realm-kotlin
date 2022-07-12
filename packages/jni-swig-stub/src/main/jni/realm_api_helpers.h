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

void
realm_changed_callback(void* userdata);

void
schema_changed_callback(void* userdata, const realm_schema_t* new_schema);

bool
migration_callback(void* userdata, realm_t* old_realm, realm_t* new_realm,
                   const realm_schema_t* schema);

realm_notification_token_t*
register_results_notification_cb(realm_results_t *results, jobject callback);

realm_notification_token_t *
register_notification_cb(int64_t collection_ptr, realm_collection_type_e collection_type,
                         jobject callback);

realm_http_transport_t*
realm_network_transport_new(jobject network_transport);

void
set_log_callback(realm_sync_client_config_t* sync_client_config, jobject log_callback);

realm_t*
open_realm_with_scheduler(int64_t config_ptr, jobject dispatchScheduler);

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
transfer_completion_callback(void* userdata, realm_sync_error_code_t* error);

void
realm_subscriptionset_changed_callback(void* userdata, realm_flx_sync_subscription_set_state_e state);

void
before_client_reset(void* userdata, realm_t* before_realm);

void
after_client_reset(void* userdata, realm_t* before_realm, realm_t* after_realm,
                   bool did_recover);

void
sync_before_client_reset_handler(realm_sync_config_t* config, jobject before_handler);

void
sync_after_client_reset_handler(realm_sync_config_t* config, jobject after_handler);

// Explicit clean up method for releasing heap allocated data of a realm_value_t instance
void
realm_value_t_cleanup(realm_value_t* value);

#endif //TEST_REALM_API_HELPERS_H
