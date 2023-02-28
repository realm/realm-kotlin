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

package io.realm.kotlin.internal.interop

actual enum class ErrorCode(override val description: String, override val nativeValue: Int) :
    CodeDescription {
    RLM_ERR_NONE("None", realm_errno_e.RLM_ERR_NONE),
    RLM_ERR_RUNTIME("Runtime", realm_errno_e.RLM_ERR_RUNTIME),
    RLM_ERR_RANGE_ERROR("RangeError", realm_errno_e.RLM_ERR_RANGE_ERROR),
    RLM_ERR_BROKEN_INVARIANT("BrokenInvariant", realm_errno_e.RLM_ERR_BROKEN_INVARIANT),
    RLM_ERR_OUT_OF_MEMORY("OutOfMemory", realm_errno_e.RLM_ERR_OUT_OF_MEMORY),
    RLM_ERR_OUT_OF_DISK_SPACE("OutOfDiskSpace", realm_errno_e.RLM_ERR_OUT_OF_DISK_SPACE),
    RLM_ERR_ADDRESS_SPACE_EXHAUSTED("AddressSpaceExhausted", realm_errno_e.RLM_ERR_ADDRESS_SPACE_EXHAUSTED),
    RLM_ERR_MAXIMUM_FILE_SIZE_EXCEEDED("MaximumFileSizeExceeded", realm_errno_e.RLM_ERR_MAXIMUM_FILE_SIZE_EXCEEDED),
    RLM_ERR_INCOMPATIBLE_SESSION("IncompatibleSession", realm_errno_e.RLM_ERR_INCOMPATIBLE_SESSION),
    RLM_ERR_INCOMPATIBLE_LOCK_FILE("IncompatibleLockFile", realm_errno_e.RLM_ERR_INCOMPATIBLE_LOCK_FILE),
    RLM_ERR_INVALID_QUERY("InvalidQuery", realm_errno_e.RLM_ERR_INVALID_QUERY),
    RLM_ERR_BAD_VERSION("BadVersion", realm_errno_e.RLM_ERR_BAD_VERSION),
    RLM_ERR_UNSUPPORTED_FILE_FORMAT_VERSION("UnsupportedFileFormatVersion", realm_errno_e.RLM_ERR_UNSUPPORTED_FILE_FORMAT_VERSION),
    RLM_ERR_MULTIPLE_SYNC_AGENTS("MultipleSyncAgents", realm_errno_e.RLM_ERR_MULTIPLE_SYNC_AGENTS),
    RLM_ERR_OBJECT_ALREADY_EXISTS("ObjectAlreadyExists", realm_errno_e.RLM_ERR_OBJECT_ALREADY_EXISTS),
    RLM_ERR_NOT_CLONABLE("NotClonable", realm_errno_e.RLM_ERR_NOT_CLONABLE),
    RLM_ERR_BAD_CHANGESET("BadChangeset", realm_errno_e.RLM_ERR_BAD_CHANGESET),
    RLM_ERR_SUBSCRIPTION_FAILED("SubscriptionFailed", realm_errno_e.RLM_ERR_SUBSCRIPTION_FAILED),
    RLM_ERR_FILE_OPERATION_FAILED("FileOperationFailed", realm_errno_e.RLM_ERR_FILE_OPERATION_FAILED),
    RLM_ERR_FILE_PERMISSION_DENIED("FilePermissionDenied", realm_errno_e.RLM_ERR_FILE_PERMISSION_DENIED),
    RLM_ERR_FILE_NOT_FOUND("FileNotFound", realm_errno_e.RLM_ERR_FILE_NOT_FOUND),
    RLM_ERR_FILE_ALREADY_EXISTS("FileAlreadyExists", realm_errno_e.RLM_ERR_FILE_ALREADY_EXISTS),
    RLM_ERR_INVALID_DATABASE("InvalidDatabase", realm_errno_e.RLM_ERR_INVALID_DATABASE),
    RLM_ERR_DECRYPTION_FAILED("DecryptionFailed", realm_errno_e.RLM_ERR_DECRYPTION_FAILED),
    RLM_ERR_INCOMPATIBLE_HISTORIES("IncompatibleHistories", realm_errno_e.RLM_ERR_INCOMPATIBLE_HISTORIES),
    RLM_ERR_FILE_FORMAT_UPGRADE_REQUIRED("FileFormatUpgradeRequired", realm_errno_e.RLM_ERR_FILE_FORMAT_UPGRADE_REQUIRED),
    RLM_ERR_SCHEMA_VERSION_MISMATCH("SchemaVersionMismatch", realm_errno_e.RLM_ERR_SCHEMA_VERSION_MISMATCH),
    RLM_ERR_NO_SUBSCRIPTION_FOR_WRITE("NoSubscriptionForWrite", realm_errno_e.RLM_ERR_NO_SUBSCRIPTION_FOR_WRITE),
    RLM_ERR_OPERATION_ABORTED("OperationAborted", realm_errno_e.RLM_ERR_OPERATION_ABORTED),
    RLM_ERR_SYSTEM_ERROR("SystemError", realm_errno_e.RLM_ERR_SYSTEM_ERROR),
    RLM_ERR_LOGIC("Logic", realm_errno_e.RLM_ERR_LOGIC),
    RLM_ERR_NOT_SUPPORTED("NotSupported", realm_errno_e.RLM_ERR_NOT_SUPPORTED),
    RLM_ERR_BROKEN_PROMISE("BrokenPromise", realm_errno_e.RLM_ERR_BROKEN_PROMISE),
    RLM_ERR_CROSS_TABLE_LINK_TARGET("CrossTableLinkTarget", realm_errno_e.RLM_ERR_CROSS_TABLE_LINK_TARGET),
    RLM_ERR_KEY_ALREADY_USED("KeyAlreadyUsed", realm_errno_e.RLM_ERR_KEY_ALREADY_USED),
    RLM_ERR_WRONG_TRANSACTION_STATE("WrongTransactionState", realm_errno_e.RLM_ERR_WRONG_TRANSACTION_STATE),
    RLM_ERR_WRONG_THREAD("WrongThread", realm_errno_e.RLM_ERR_WRONG_THREAD),
    RLM_ERR_ILLEGAL_OPERATION("IllegalOperation", realm_errno_e.RLM_ERR_ILLEGAL_OPERATION),
    RLM_ERR_SERIALIZATION_ERROR("SerializationError", realm_errno_e.RLM_ERR_SERIALIZATION_ERROR),
    RLM_ERR_STALE_ACCESSOR("StaleAccessor", realm_errno_e.RLM_ERR_STALE_ACCESSOR),
    RLM_ERR_INVALIDATED_OBJECT("InvalidatedObject", realm_errno_e.RLM_ERR_INVALIDATED_OBJECT),
    RLM_ERR_READ_ONLY_DB("ReadOnlyDb", realm_errno_e.RLM_ERR_READ_ONLY_DB),
    RLM_ERR_DELETE_OPENED_REALM("DeleteOpenedRealm", realm_errno_e.RLM_ERR_DELETE_OPENED_REALM),
    RLM_ERR_MISMATCHED_CONFIG("MismatchedConfig", realm_errno_e.RLM_ERR_MISMATCHED_CONFIG),
    RLM_ERR_CLOSED_REALM("ClosedRealm", realm_errno_e.RLM_ERR_CLOSED_REALM),
    RLM_ERR_INVALID_TABLE_REF("InvalidTableRef", realm_errno_e.RLM_ERR_INVALID_TABLE_REF),
    RLM_ERR_SCHEMA_VALIDATION_FAILED("SchemaValidationFailed", realm_errno_e.RLM_ERR_SCHEMA_VALIDATION_FAILED),
    RLM_ERR_SCHEMA_MISMATCH("SchemaMismatch", realm_errno_e.RLM_ERR_SCHEMA_MISMATCH),
    RLM_ERR_INVALID_SCHEMA_VERSION("InvalidSchemaVersion", realm_errno_e.RLM_ERR_INVALID_SCHEMA_VERSION),
    RLM_ERR_INVALID_SCHEMA_CHANGE("InvalidSchemaChange", realm_errno_e.RLM_ERR_INVALID_SCHEMA_CHANGE),
    RLM_ERR_MIGRATION_FAILED("MigrationFailed", realm_errno_e.RLM_ERR_MIGRATION_FAILED),
    RLM_ERR_TOP_LEVEL_OBJECT("TopLevelObject", realm_errno_e.RLM_ERR_TOP_LEVEL_OBJECT),
    RLM_ERR_INVALID_ARGUMENT("InvalidArgument", realm_errno_e.RLM_ERR_INVALID_ARGUMENT),
    RLM_ERR_PROPERTY_TYPE_MISMATCH("PropertyTypeMismatch", realm_errno_e.RLM_ERR_PROPERTY_TYPE_MISMATCH),
    RLM_ERR_PROPERTY_NOT_NULLABLE("PropertyNotNullable", realm_errno_e.RLM_ERR_PROPERTY_NOT_NULLABLE),
    RLM_ERR_READ_ONLY_PROPERTY("ReadOnlyProperty", realm_errno_e.RLM_ERR_READ_ONLY_PROPERTY),
    RLM_ERR_MISSING_PROPERTY_VALUE("MissingPropertyValue", realm_errno_e.RLM_ERR_MISSING_PROPERTY_VALUE),
    RLM_ERR_MISSING_PRIMARY_KEY("MissingPrimaryKey", realm_errno_e.RLM_ERR_MISSING_PRIMARY_KEY),
    RLM_ERR_UNEXPECTED_PRIMARY_KEY("UnexpectedPrimaryKey", realm_errno_e.RLM_ERR_UNEXPECTED_PRIMARY_KEY),
    RLM_ERR_MODIFY_PRIMARY_KEY("ModifyPrimaryKey", realm_errno_e.RLM_ERR_MODIFY_PRIMARY_KEY),
    RLM_ERR_INVALID_QUERY_STRING("InvalidQueryString", realm_errno_e.RLM_ERR_INVALID_QUERY_STRING),
    RLM_ERR_INVALID_PROPERTY("InvalidProperty", realm_errno_e.RLM_ERR_INVALID_PROPERTY),
    RLM_ERR_INVALID_NAME("InvalidName", realm_errno_e.RLM_ERR_INVALID_NAME),
    RLM_ERR_INVALID_DICTIONARY_KEY("InvalidDictionaryKey", realm_errno_e.RLM_ERR_INVALID_DICTIONARY_KEY),
    RLM_ERR_INVALID_DICTIONARY_VALUE("InvalidDictionaryValue", realm_errno_e.RLM_ERR_INVALID_DICTIONARY_VALUE),
    RLM_ERR_INVALID_SORT_DESCRIPTOR("InvalidSortDescriptor", realm_errno_e.RLM_ERR_INVALID_SORT_DESCRIPTOR),
    RLM_ERR_INVALID_ENCRYPTION_KEY("InvalidEncryptionKey", realm_errno_e.RLM_ERR_INVALID_ENCRYPTION_KEY),
    RLM_ERR_INVALID_QUERY_ARG("InvalidQueryArg", realm_errno_e.RLM_ERR_INVALID_QUERY_ARG),
    RLM_ERR_NO_SUCH_OBJECT("NoSuchObject", realm_errno_e.RLM_ERR_NO_SUCH_OBJECT),
    RLM_ERR_INDEX_OUT_OF_BOUNDS("IndexOutOfBounds", realm_errno_e.RLM_ERR_INDEX_OUT_OF_BOUNDS),
    RLM_ERR_LIMIT_EXCEEDED("LimitExceeded", realm_errno_e.RLM_ERR_LIMIT_EXCEEDED),
    RLM_ERR_OBJECT_TYPE_MISMATCH("ObjectTypeMismatch", realm_errno_e.RLM_ERR_OBJECT_TYPE_MISMATCH),
    RLM_ERR_NO_SUCH_TABLE("NoSuchTable", realm_errno_e.RLM_ERR_NO_SUCH_TABLE),
    RLM_ERR_TABLE_NAME_IN_USE("TableNameInUse", realm_errno_e.RLM_ERR_TABLE_NAME_IN_USE),
    RLM_ERR_ILLEGAL_COMBINATION("IllegalCombination", realm_errno_e.RLM_ERR_ILLEGAL_COMBINATION),
    RLM_ERR_BAD_SERVER_URL("BadServerUrl", realm_errno_e.RLM_ERR_BAD_SERVER_URL),
    RLM_ERR_CUSTOM_ERROR("CustomError", realm_errno_e.RLM_ERR_CUSTOM_ERROR),
    RLM_ERR_CLIENT_USER_NOT_FOUND("ClientUserNotFound", realm_errno_e.RLM_ERR_CLIENT_USER_NOT_FOUND),
    RLM_ERR_CLIENT_USER_NOT_LOGGED_IN("ClientUserNotLoggedIn", realm_errno_e.RLM_ERR_CLIENT_USER_NOT_LOGGED_IN),
    RLM_ERR_CLIENT_APP_DEALLOCATED("ClientAppDeallocated", realm_errno_e.RLM_ERR_CLIENT_APP_DEALLOCATED),
    RLM_ERR_CLIENT_REDIRECT_ERROR("ClientRedirectError", realm_errno_e.RLM_ERR_CLIENT_REDIRECT_ERROR),
    RLM_ERR_CLIENT_TOO_MANY_REDIRECTS("ClientTooManyRedirects", realm_errno_e.RLM_ERR_CLIENT_TOO_MANY_REDIRECTS),
    RLM_ERR_BAD_TOKEN("BadToken", realm_errno_e.RLM_ERR_BAD_TOKEN),
    RLM_ERR_MALFORMED_JSON("MalformedJson", realm_errno_e.RLM_ERR_MALFORMED_JSON),
    RLM_ERR_MISSING_JSON_KEY("MissingJsonKey", realm_errno_e.RLM_ERR_MISSING_JSON_KEY),
    RLM_ERR_BAD_BSON_PARSE("BadBsonParse", realm_errno_e.RLM_ERR_BAD_BSON_PARSE),
    RLM_ERR_MISSING_AUTH_REQ("MissingAuthReq", realm_errno_e.RLM_ERR_MISSING_AUTH_REQ),
    RLM_ERR_INVALID_SESSION("InvalidSession", realm_errno_e.RLM_ERR_INVALID_SESSION),
    RLM_ERR_USER_APP_DOMAIN_MISMATCH("UserAppDomainMismatch", realm_errno_e.RLM_ERR_USER_APP_DOMAIN_MISMATCH),
    RLM_ERR_DOMAIN_NOT_ALLOWED("DomainNotAllowed", realm_errno_e.RLM_ERR_DOMAIN_NOT_ALLOWED),
    RLM_ERR_READ_SIZE_LIMIT_EXCEEDED("ReadSizeLimitExceeded", realm_errno_e.RLM_ERR_READ_SIZE_LIMIT_EXCEEDED),
    RLM_ERR_INVALID_PARAMETER("InvalidParameter", realm_errno_e.RLM_ERR_INVALID_PARAMETER),
    RLM_ERR_MISSING_PARAMETER("MissingParameter", realm_errno_e.RLM_ERR_MISSING_PARAMETER),
    RLM_ERR_TWILIO_ERROR("TwilioError", realm_errno_e.RLM_ERR_TWILIO_ERROR),
    RLM_ERR_GCM_ERROR("GcmError", realm_errno_e.RLM_ERR_GCM_ERROR),
    RLM_ERR_HTTP_ERROR("HttpError", realm_errno_e.RLM_ERR_HTTP_ERROR),
    RLM_ERR_AWS_ERROR("AwsError", realm_errno_e.RLM_ERR_AWS_ERROR),
    RLM_ERR_MONGODB_ERROR("MongodbError", realm_errno_e.RLM_ERR_MONGODB_ERROR),
    RLM_ERR_ARGUMENTS_NOT_ALLOWED("ArgumentsNotAllowed", realm_errno_e.RLM_ERR_ARGUMENTS_NOT_ALLOWED),
    RLM_ERR_FUNCTION_EXECUTION_ERROR("FunctionExecutionError", realm_errno_e.RLM_ERR_FUNCTION_EXECUTION_ERROR),
    RLM_ERR_NO_MATCHING_RULE("NoMatchingRule", realm_errno_e.RLM_ERR_NO_MATCHING_RULE),
    RLM_ERR_INTERNAL_SERVER_ERROR("InternalServerError", realm_errno_e.RLM_ERR_INTERNAL_SERVER_ERROR),
    RLM_ERR_AUTH_PROVIDER_NOT_FOUND("AuthProviderNotFound", realm_errno_e.RLM_ERR_AUTH_PROVIDER_NOT_FOUND),
    RLM_ERR_AUTH_PROVIDER_ALREADY_EXISTS("AuthProviderAlreadyExists", realm_errno_e.RLM_ERR_AUTH_PROVIDER_ALREADY_EXISTS),
    RLM_ERR_SERVICE_NOT_FOUND("ServiceNotFound", realm_errno_e.RLM_ERR_SERVICE_NOT_FOUND),
    RLM_ERR_SERVICE_TYPE_NOT_FOUND("ServiceTypeNotFound", realm_errno_e.RLM_ERR_SERVICE_TYPE_NOT_FOUND),
    RLM_ERR_SERVICE_ALREADY_EXISTS("ServiceAlreadyExists", realm_errno_e.RLM_ERR_SERVICE_ALREADY_EXISTS),
    RLM_ERR_SERVICE_COMMAND_NOT_FOUND("ServiceCommandNotFound", realm_errno_e.RLM_ERR_SERVICE_COMMAND_NOT_FOUND),
    RLM_ERR_VALUE_NOT_FOUND("ValueNotFound", realm_errno_e.RLM_ERR_VALUE_NOT_FOUND),
    RLM_ERR_VALUE_ALREADY_EXISTS("ValueAlreadyExists", realm_errno_e.RLM_ERR_VALUE_ALREADY_EXISTS),
    RLM_ERR_VALUE_DUPLICATE_NAME("ValueDuplicateName", realm_errno_e.RLM_ERR_VALUE_DUPLICATE_NAME),
    RLM_ERR_FUNCTION_NOT_FOUND("FunctionNotFound", realm_errno_e.RLM_ERR_FUNCTION_NOT_FOUND),
    RLM_ERR_FUNCTION_ALREADY_EXISTS("FunctionAlreadyExists", realm_errno_e.RLM_ERR_FUNCTION_ALREADY_EXISTS),
    RLM_ERR_FUNCTION_DUPLICATE_NAME("FunctionDuplicateName", realm_errno_e.RLM_ERR_FUNCTION_DUPLICATE_NAME),
    RLM_ERR_FUNCTION_SYNTAX_ERROR("FunctionSyntaxError", realm_errno_e.RLM_ERR_FUNCTION_SYNTAX_ERROR),
    RLM_ERR_FUNCTION_INVALID("FunctionInvalid", realm_errno_e.RLM_ERR_FUNCTION_INVALID),
    RLM_ERR_INCOMING_WEBHOOK_NOT_FOUND("IncomingWebhookNotFound", realm_errno_e.RLM_ERR_INCOMING_WEBHOOK_NOT_FOUND),
    RLM_ERR_INCOMING_WEBHOOK_ALREADY_EXISTS("IncomingWebhookAlreadyExists", realm_errno_e.RLM_ERR_INCOMING_WEBHOOK_ALREADY_EXISTS),
    RLM_ERR_INCOMING_WEBHOOK_DUPLICATE_NAME("IncomingWebhookDuplicateName", realm_errno_e.RLM_ERR_INCOMING_WEBHOOK_DUPLICATE_NAME),
    RLM_ERR_RULE_NOT_FOUND("RuleNotFound", realm_errno_e.RLM_ERR_RULE_NOT_FOUND),
    RLM_ERR_API_KEY_NOT_FOUND("ApiKeyNotFound", realm_errno_e.RLM_ERR_API_KEY_NOT_FOUND),
    RLM_ERR_RULE_ALREADY_EXISTS("RuleAlreadyExists", realm_errno_e.RLM_ERR_RULE_ALREADY_EXISTS),
    RLM_ERR_RULE_DUPLICATE_NAME("RuleDuplicateName", realm_errno_e.RLM_ERR_RULE_DUPLICATE_NAME),
    RLM_ERR_AUTH_PROVIDER_DUPLICATE_NAME("AuthProviderDuplicateName", realm_errno_e.RLM_ERR_AUTH_PROVIDER_DUPLICATE_NAME),
    RLM_ERR_RESTRICTED_HOST("RestrictedHost", realm_errno_e.RLM_ERR_RESTRICTED_HOST),
    RLM_ERR_API_KEY_ALREADY_EXISTS("ApiKeyAlreadyExists", realm_errno_e.RLM_ERR_API_KEY_ALREADY_EXISTS),
    RLM_ERR_INCOMING_WEBHOOK_AUTH_FAILED("IncomingWebhookAuthFailed", realm_errno_e.RLM_ERR_INCOMING_WEBHOOK_AUTH_FAILED),
    RLM_ERR_EXECUTION_TIME_LIMIT_EXCEEDED("ExecutionTimeLimitExceeded", realm_errno_e.RLM_ERR_EXECUTION_TIME_LIMIT_EXCEEDED),
    RLM_ERR_NOT_CALLABLE("NotCallable", realm_errno_e.RLM_ERR_NOT_CALLABLE),
    RLM_ERR_USER_ALREADY_CONFIRMED("UserAlreadyConfirmed", realm_errno_e.RLM_ERR_USER_ALREADY_CONFIRMED),
    RLM_ERR_USER_NOT_FOUND("UserNotFound", realm_errno_e.RLM_ERR_USER_NOT_FOUND),
    RLM_ERR_USER_DISABLED("UserDisabled", realm_errno_e.RLM_ERR_USER_DISABLED),
    RLM_ERR_AUTH_ERROR("AuthError", realm_errno_e.RLM_ERR_AUTH_ERROR),
    RLM_ERR_BAD_REQUEST("BadRequest", realm_errno_e.RLM_ERR_BAD_REQUEST),
    RLM_ERR_ACCOUNT_NAME_IN_USE("AccountNameInUse", realm_errno_e.RLM_ERR_ACCOUNT_NAME_IN_USE),
    RLM_ERR_INVALID_PASSWORD("InvalidPassword", realm_errno_e.RLM_ERR_INVALID_PASSWORD),
    RLM_ERR_SCHEMA_VALIDATION_FAILED_WRITE("SchemaValidationFailedWrite", realm_errno_e.RLM_ERR_SCHEMA_VALIDATION_FAILED_WRITE),
    RLM_ERR_APP_UNKNOWN("Unknown", realm_errno_e.RLM_ERR_APP_UNKNOWN),
    RLM_ERR_MAINTENANCE_IN_PROGRESS("MaintenanceInProgress", realm_errno_e.RLM_ERR_MAINTENANCE_IN_PROGRESS),
    RLM_ERR_USERPASS_TOKEN_INVALID("UserpassTokenInvalid", realm_errno_e.RLM_ERR_USERPASS_TOKEN_INVALID),
    RLM_ERR_WEBSOCKET_RESOLVE_FAILED_ERROR("ResolveFailedError", realm_errno_e.RLM_ERR_WEBSOCKET_RESOLVE_FAILED_ERROR),
    RLM_ERR_WEBSOCKET_CONNECTION_CLOSED_CLIENT_ERROR("ConnectionClosedClientError", realm_errno_e.RLM_ERR_WEBSOCKET_CONNECTION_CLOSED_CLIENT_ERROR),
    RLM_ERR_WEBSOCKET_CONNECTION_CLOSED_SERVER_ERROR("ConnectionClosedServerError", realm_errno_e.RLM_ERR_WEBSOCKET_CONNECTION_CLOSED_SERVER_ERROR),
    RLM_ERR_CALLBACK("Callback", realm_errno_e.RLM_ERR_CALLBACK),
    RLM_ERR_UNKNOWN("Unknown", realm_errno_e.RLM_ERR_UNKNOWN);

    actual companion object {
        actual fun of(nativeValue: Int): ErrorCode =
            values().first() { value ->
                value.nativeValue == nativeValue
            }
    }
}
