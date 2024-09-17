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

// Explicit clean up method for releasing heap allocated data of a realm_value_t instance
void
realm_value_t_cleanup(realm_value_t* value);

realm_scheduler_t*
realm_create_generic_scheduler();

void
realm_property_info_t_cleanup(realm_property_info_t* value);

void
realm_class_info_t_cleanup(realm_class_info_t * value);

jobjectArray realm_get_log_category_names();

#endif //TEST_REALM_API_HELPERS_H
