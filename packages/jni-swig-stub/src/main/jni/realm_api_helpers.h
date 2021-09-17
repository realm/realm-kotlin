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
#include "utils.h"

realm_notification_token_t *
register_results_notification_cb(realm_results_t *results, jobject callback);

realm_notification_token_t *
register_list_notification_cb(realm_list_t *list, jobject callback);

realm_notification_token_t *
register_object_notification_cb(realm_object_t *object, jobject callback);

realm_app_config_t *
new_app_config(const char* app_id, jobject app_instance);

void
register_login_cb(realm_app_t* app, realm_app_credentials_t* credentials, jobject callback);

#endif //TEST_REALM_API_HELPERS_H
