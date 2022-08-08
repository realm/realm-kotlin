/*
 * Copyright 2022 Realm Inc.
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

package io.realm.kotlin.internal.interop.sync

import realm_wrapper.realm_app_errno_service

actual enum class ServiceErrorCode(
    actual override val description: String,
    val nativeValue: realm_app_errno_service
) : ErrorCode {
    RLM_APP_ERR_SERVICE_MISSING_AUTH_REQ("MissingAuthReq", realm_wrapper.RLM_APP_ERR_SERVICE_MISSING_AUTH_REQ),
    RLM_APP_ERR_SERVICE_INVALID_SESSION("InvalidSession", realm_wrapper.RLM_APP_ERR_SERVICE_INVALID_SESSION),
    RLM_APP_ERR_SERVICE_USER_APP_DOMAIN_MISMATCH("UserAppDomainMismatch", realm_wrapper.RLM_APP_ERR_SERVICE_USER_APP_DOMAIN_MISMATCH),
    RLM_APP_ERR_SERVICE_DOMAIN_NOT_ALLOWED("DomainNotAllowed", realm_wrapper.RLM_APP_ERR_SERVICE_DOMAIN_NOT_ALLOWED),
    RLM_APP_ERR_SERVICE_READ_SIZE_LIMIT_EXCEEDED("ReadSizeLimitExceeded", realm_wrapper.RLM_APP_ERR_SERVICE_READ_SIZE_LIMIT_EXCEEDED),
    RLM_APP_ERR_SERVICE_INVALID_PARAMETER("InvalidParameter", realm_wrapper.RLM_APP_ERR_SERVICE_INVALID_PARAMETER),
    RLM_APP_ERR_SERVICE_MISSING_PARAMETER("MissingParameter", realm_wrapper.RLM_APP_ERR_SERVICE_MISSING_PARAMETER),
    RLM_APP_ERR_SERVICE_TWILIO_ERROR("TwilioError", realm_wrapper.RLM_APP_ERR_SERVICE_TWILIO_ERROR),
    RLM_APP_ERR_SERVICE_GCM_ERROR("GcmError", realm_wrapper.RLM_APP_ERR_SERVICE_GCM_ERROR),
    RLM_APP_ERR_SERVICE_HTTP_ERROR("HttpError", realm_wrapper.RLM_APP_ERR_SERVICE_HTTP_ERROR),
    RLM_APP_ERR_SERVICE_AWS_ERROR("AwsError", realm_wrapper.RLM_APP_ERR_SERVICE_AWS_ERROR),
    RLM_APP_ERR_SERVICE_MONGODB_ERROR("MongodbError", realm_wrapper.RLM_APP_ERR_SERVICE_MONGODB_ERROR),
    RLM_APP_ERR_SERVICE_ARGUMENTS_NOT_ALLOWED("ArgumentsNotAllowed", realm_wrapper.RLM_APP_ERR_SERVICE_ARGUMENTS_NOT_ALLOWED),
    RLM_APP_ERR_SERVICE_FUNCTION_EXECUTION_ERROR("FunctionExecutionError", realm_wrapper.RLM_APP_ERR_SERVICE_FUNCTION_EXECUTION_ERROR),
    RLM_APP_ERR_SERVICE_NO_MATCHING_RULE_FOUND("NoMatchingRuleFound", realm_wrapper.RLM_APP_ERR_SERVICE_NO_MATCHING_RULE_FOUND),
    RLM_APP_ERR_SERVICE_INTERNAL_SERVER_ERROR("InternalServerError", realm_wrapper.RLM_APP_ERR_SERVICE_INTERNAL_SERVER_ERROR),
    RLM_APP_ERR_SERVICE_AUTH_PROVIDER_NOT_FOUND("AuthProviderNotFound", realm_wrapper.RLM_APP_ERR_SERVICE_AUTH_PROVIDER_NOT_FOUND),
    RLM_APP_ERR_SERVICE_AUTH_PROVIDER_ALREADY_EXISTS("AuthProviderAlreadyExists", realm_wrapper.RLM_APP_ERR_SERVICE_AUTH_PROVIDER_ALREADY_EXISTS),
    RLM_APP_ERR_SERVICE_SERVICE_NOT_FOUND("ServiceNotFound", realm_wrapper.RLM_APP_ERR_SERVICE_SERVICE_NOT_FOUND),
    RLM_APP_ERR_SERVICE_SERVICE_TYPE_NOT_FOUND("ServiceTypeNotFound", realm_wrapper.RLM_APP_ERR_SERVICE_SERVICE_TYPE_NOT_FOUND),
    RLM_APP_ERR_SERVICE_SERVICE_ALREADY_EXISTS("ServiceAlreadyExists", realm_wrapper.RLM_APP_ERR_SERVICE_SERVICE_ALREADY_EXISTS),
    RLM_APP_ERR_SERVICE_SERVICE_COMMAND_NOT_FOUND("ServiceCommandNotFound", realm_wrapper.RLM_APP_ERR_SERVICE_SERVICE_COMMAND_NOT_FOUND),
    RLM_APP_ERR_SERVICE_VALUE_NOT_FOUND("ValueNotFound", realm_wrapper.RLM_APP_ERR_SERVICE_VALUE_NOT_FOUND),
    RLM_APP_ERR_SERVICE_VALUE_ALREADY_EXISTS("ValueAlreadyExists", realm_wrapper.RLM_APP_ERR_SERVICE_VALUE_ALREADY_EXISTS),
    RLM_APP_ERR_SERVICE_VALUE_DUPLICATE_NAME("ValueDuplicateName", realm_wrapper.RLM_APP_ERR_SERVICE_VALUE_DUPLICATE_NAME),
    RLM_APP_ERR_SERVICE_FUNCTION_NOT_FOUND("FunctionNotFound", realm_wrapper.RLM_APP_ERR_SERVICE_FUNCTION_NOT_FOUND),
    RLM_APP_ERR_SERVICE_FUNCTION_ALREADY_EXISTS("FunctionAlreadyExists", realm_wrapper.RLM_APP_ERR_SERVICE_FUNCTION_ALREADY_EXISTS),
    RLM_APP_ERR_SERVICE_FUNCTION_DUPLICATE_NAME("FunctionDuplicateName", realm_wrapper.RLM_APP_ERR_SERVICE_FUNCTION_DUPLICATE_NAME),
    RLM_APP_ERR_SERVICE_FUNCTION_SYNTAX_ERROR("FunctionSyntaxError", realm_wrapper.RLM_APP_ERR_SERVICE_FUNCTION_SYNTAX_ERROR),
    RLM_APP_ERR_SERVICE_FUNCTION_INVALID("FunctionInvalid", realm_wrapper.RLM_APP_ERR_SERVICE_FUNCTION_INVALID),
    RLM_APP_ERR_SERVICE_INCOMING_WEBHOOK_NOT_FOUND("IncomingWebhookNotFound", realm_wrapper.RLM_APP_ERR_SERVICE_INCOMING_WEBHOOK_NOT_FOUND),
    RLM_APP_ERR_SERVICE_INCOMING_WEBHOOK_ALREADY_EXISTS("IncomingWebhookAlreadyExists", realm_wrapper.RLM_APP_ERR_SERVICE_INCOMING_WEBHOOK_ALREADY_EXISTS),
    RLM_APP_ERR_SERVICE_INCOMING_WEBHOOK_DUPLICATE_NAME("IncomingWebhookDuplicateName", realm_wrapper.RLM_APP_ERR_SERVICE_INCOMING_WEBHOOK_DUPLICATE_NAME),
    RLM_APP_ERR_SERVICE_RULE_NOT_FOUND("ServiceRuleNotFound", realm_wrapper.RLM_APP_ERR_SERVICE_RULE_NOT_FOUND),
    RLM_APP_ERR_SERVICE_API_KEY_NOT_FOUND("ApiKeyNotFound", realm_wrapper.RLM_APP_ERR_SERVICE_API_KEY_NOT_FOUND),
    RLM_APP_ERR_SERVICE_RULE_ALREADY_EXISTS("RuleAlreadyExists", realm_wrapper.RLM_APP_ERR_SERVICE_RULE_ALREADY_EXISTS),
    RLM_APP_ERR_SERVICE_RULE_DUPLICATE_NAME("RuleDuplicateName", realm_wrapper.RLM_APP_ERR_SERVICE_RULE_DUPLICATE_NAME),
    RLM_APP_ERR_SERVICE_AUTH_PROVIDER_DUPLICATE_NAME("AuthProviderDuplicateName", realm_wrapper.RLM_APP_ERR_SERVICE_AUTH_PROVIDER_DUPLICATE_NAME),
    RLM_APP_ERR_SERVICE_RESTRICTED_HOST("RestrictedHost", realm_wrapper.RLM_APP_ERR_SERVICE_RESTRICTED_HOST),
    RLM_APP_ERR_SERVICE_API_KEY_ALREADY_EXISTS("ApiKeyAlreadyExists", realm_wrapper.RLM_APP_ERR_SERVICE_API_KEY_ALREADY_EXISTS),
    RLM_APP_ERR_SERVICE_INCOMING_WEBHOOK_AUTH_FAILED("IncomingWebhookAuthFailed", realm_wrapper.RLM_APP_ERR_SERVICE_INCOMING_WEBHOOK_AUTH_FAILED),
    RLM_APP_ERR_SERVICE_EXECUTION_TIME_LIMIT_EXCEEDED("ExecutionTimeLimitExceeded", realm_wrapper.RLM_APP_ERR_SERVICE_EXECUTION_TIME_LIMIT_EXCEEDED),
    RLM_APP_ERR_SERVICE_NOT_CALLABLE("NotCallable", realm_wrapper.RLM_APP_ERR_SERVICE_NOT_CALLABLE),
    RLM_APP_ERR_SERVICE_USER_ALREADY_CONFIRMED("UserAlreadyConfirmed", realm_wrapper.RLM_APP_ERR_SERVICE_USER_ALREADY_CONFIRMED),
    RLM_APP_ERR_SERVICE_USER_NOT_FOUND("UserNotFound", realm_wrapper.RLM_APP_ERR_SERVICE_USER_NOT_FOUND),
    RLM_APP_ERR_SERVICE_USER_DISABLED("UserDisabled", realm_wrapper.RLM_APP_ERR_SERVICE_USER_DISABLED),
    RLM_APP_ERR_SERVICE_AUTH_ERROR("AuthError", realm_wrapper.RLM_APP_ERR_SERVICE_AUTH_ERROR),
    RLM_APP_ERR_SERVICE_BAD_REQUEST("BadRequest", realm_wrapper.RLM_APP_ERR_SERVICE_BAD_REQUEST),
    RLM_APP_ERR_SERVICE_ACCOUNT_NAME_IN_USE("AccountNameInUse", realm_wrapper.RLM_APP_ERR_SERVICE_ACCOUNT_NAME_IN_USE),
    RLM_APP_ERR_SERVICE_INVALID_EMAIL_PASSWORD("InvalidEmailPassword", realm_wrapper.RLM_APP_ERR_SERVICE_INVALID_EMAIL_PASSWORD),
    RLM_APP_ERR_SERVICE_UNKNOWN("Unknown", realm_wrapper.RLM_APP_ERR_SERVICE_UNKNOWN),
    RLM_APP_ERR_SERVICE_NONE("None", realm_wrapper.RLM_APP_ERR_SERVICE_NONE);

    actual companion object {
        actual fun fromInt(nativeValue: Int): ServiceErrorCode? {
            for (value in values()) {
                if (value.nativeValue == nativeValue) {
                    return value
                }
            }
            return null
        }
    }
}
