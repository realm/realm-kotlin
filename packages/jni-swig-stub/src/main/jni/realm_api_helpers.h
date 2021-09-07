//
// Created by Clemente Tort Barbero on 6/9/21.
//

#ifndef TEST_REALM_API_HELPERS_H
#define TEST_REALM_API_HELPERS_H

#include "realm.h"
#include "env_utils.h"

realm_notification_token_t *
register_results_notification_cb(realm_results_t *results, jobject callback);

realm_notification_token_t *
register_list_notification_cb(realm_list_t *list, jobject callback);

realm_notification_token_t *
register_object_notification_cb(realm_object_t *object, jobject callback);

realm_app_config_t *
new_app_config(const char* app_id, jobject app_instance);


#endif //TEST_REALM_API_HELPERS_H
