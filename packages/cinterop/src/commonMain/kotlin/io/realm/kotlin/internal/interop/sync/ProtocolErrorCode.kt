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

import io.realm.kotlin.internal.interop.CodeDescription

/**
 * Wrapper for C-API `realm_sync_errno_client`.
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L3214
 */
expect enum class ProtocolClientErrorCode : CodeDescription {
    RLM_SYNC_ERR_CLIENT_CONNECTION_CLOSED,
    RLM_SYNC_ERR_CLIENT_UNKNOWN_MESSAGE,
    RLM_SYNC_ERR_CLIENT_BAD_SYNTAX,
    RLM_SYNC_ERR_CLIENT_LIMITS_EXCEEDED,
    RLM_SYNC_ERR_CLIENT_BAD_SESSION_IDENT,
    RLM_SYNC_ERR_CLIENT_BAD_MESSAGE_ORDER,
    RLM_SYNC_ERR_CLIENT_BAD_CLIENT_FILE_IDENT,
    RLM_SYNC_ERR_CLIENT_BAD_PROGRESS,
    RLM_SYNC_ERR_CLIENT_BAD_CHANGESET_HEADER_SYNTAX,
    RLM_SYNC_ERR_CLIENT_BAD_CHANGESET_SIZE,
    RLM_SYNC_ERR_CLIENT_BAD_ORIGIN_FILE_IDENT,
    RLM_SYNC_ERR_CLIENT_BAD_SERVER_VERSION,
    RLM_SYNC_ERR_CLIENT_BAD_CHANGESET,
    RLM_SYNC_ERR_CLIENT_BAD_REQUEST_IDENT,
    RLM_SYNC_ERR_CLIENT_BAD_ERROR_CODE,
    RLM_SYNC_ERR_CLIENT_BAD_COMPRESSION,
    RLM_SYNC_ERR_CLIENT_BAD_CLIENT_VERSION,
    RLM_SYNC_ERR_CLIENT_SSL_SERVER_CERT_REJECTED,
    RLM_SYNC_ERR_CLIENT_PONG_TIMEOUT,
    RLM_SYNC_ERR_CLIENT_BAD_CLIENT_FILE_IDENT_SALT,
    RLM_SYNC_ERR_CLIENT_BAD_FILE_IDENT,
    RLM_SYNC_ERR_CLIENT_CONNECT_TIMEOUT,
    RLM_SYNC_ERR_CLIENT_BAD_TIMESTAMP,
    RLM_SYNC_ERR_CLIENT_BAD_PROTOCOL_FROM_SERVER,
    RLM_SYNC_ERR_CLIENT_CLIENT_TOO_OLD_FOR_SERVER,
    RLM_SYNC_ERR_CLIENT_CLIENT_TOO_NEW_FOR_SERVER,
    RLM_SYNC_ERR_CLIENT_PROTOCOL_MISMATCH,
    RLM_SYNC_ERR_CLIENT_BAD_STATE_MESSAGE,
    RLM_SYNC_ERR_CLIENT_MISSING_PROTOCOL_FEATURE,
    RLM_SYNC_ERR_CLIENT_HTTP_TUNNEL_FAILED,
    RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE;

    companion object {
        internal fun of(nativeValue: Int): ProtocolClientErrorCode?
    }
}

/**
 * Wrapper for C-API `realm_sync_errno_connection`.
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L2942
 */
expect enum class ProtocolConnectionErrorCode : CodeDescription {
    RLM_SYNC_ERR_CONNECTION_CONNECTION_CLOSED,
    RLM_SYNC_ERR_CONNECTION_OTHER_ERROR,
    RLM_SYNC_ERR_CONNECTION_UNKNOWN_MESSAGE,
    RLM_SYNC_ERR_CONNECTION_BAD_SYNTAX,
    RLM_SYNC_ERR_CONNECTION_LIMITS_EXCEEDED,
    RLM_SYNC_ERR_CONNECTION_WRONG_PROTOCOL_VERSION,
    RLM_SYNC_ERR_CONNECTION_BAD_SESSION_IDENT,
    RLM_SYNC_ERR_CONNECTION_REUSE_OF_SESSION_IDENT,
    RLM_SYNC_ERR_CONNECTION_BOUND_IN_OTHER_SESSION,
    RLM_SYNC_ERR_CONNECTION_BAD_MESSAGE_ORDER,
    RLM_SYNC_ERR_CONNECTION_BAD_DECOMPRESSION,
    RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_HEADER_SYNTAX,
    RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_SIZE,
    RLM_SYNC_ERR_CONNECTION_SWITCH_TO_FLX_SYNC,
    RLM_SYNC_ERR_CONNECTION_SWITCH_TO_PBS;

    companion object {
        internal fun of(nativeValue: Int): ProtocolConnectionErrorCode?
    }
}

/**
 * Wrapper for C-API `realm_sync_errno_session`.
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L2960
 */
expect enum class ProtocolSessionErrorCode : CodeDescription {
    RLM_SYNC_ERR_SESSION_SESSION_CLOSED,
    RLM_SYNC_ERR_SESSION_OTHER_SESSION_ERROR,
    RLM_SYNC_ERR_SESSION_TOKEN_EXPIRED,
    RLM_SYNC_ERR_SESSION_BAD_AUTHENTICATION,
    RLM_SYNC_ERR_SESSION_ILLEGAL_REALM_PATH,
    RLM_SYNC_ERR_SESSION_NO_SUCH_REALM,
    RLM_SYNC_ERR_SESSION_PERMISSION_DENIED,
    RLM_SYNC_ERR_SESSION_BAD_SERVER_FILE_IDENT,
    RLM_SYNC_ERR_SESSION_BAD_CLIENT_FILE_IDENT,
    RLM_SYNC_ERR_SESSION_BAD_SERVER_VERSION,
    RLM_SYNC_ERR_SESSION_BAD_CLIENT_VERSION,
    RLM_SYNC_ERR_SESSION_DIVERGING_HISTORIES,
    RLM_SYNC_ERR_SESSION_BAD_CHANGESET,
    RLM_SYNC_ERR_SESSION_PARTIAL_SYNC_DISABLED,
    RLM_SYNC_ERR_SESSION_UNSUPPORTED_SESSION_FEATURE,
    RLM_SYNC_ERR_SESSION_BAD_ORIGIN_FILE_IDENT,
    RLM_SYNC_ERR_SESSION_BAD_CLIENT_FILE,
    RLM_SYNC_ERR_SESSION_SERVER_FILE_DELETED,
    RLM_SYNC_ERR_SESSION_CLIENT_FILE_BLACKLISTED,
    RLM_SYNC_ERR_SESSION_USER_BLACKLISTED,
    RLM_SYNC_ERR_SESSION_TRANSACT_BEFORE_UPLOAD,
    RLM_SYNC_ERR_SESSION_CLIENT_FILE_EXPIRED,
    RLM_SYNC_ERR_SESSION_USER_MISMATCH,
    RLM_SYNC_ERR_SESSION_TOO_MANY_SESSIONS,
    RLM_SYNC_ERR_SESSION_INVALID_SCHEMA_CHANGE,
    RLM_SYNC_ERR_SESSION_BAD_QUERY,
    RLM_SYNC_ERR_SESSION_OBJECT_ALREADY_EXISTS,
    RLM_SYNC_ERR_SESSION_SERVER_PERMISSIONS_CHANGED,
    RLM_SYNC_ERR_SESSION_INITIAL_SYNC_NOT_COMPLETED,
    RLM_SYNC_ERR_SESSION_WRITE_NOT_ALLOWED,
    RLM_SYNC_ERR_SESSION_COMPENSATING_WRITE,
    RLM_SYNC_ERR_SESSION_MIGRATE_TO_FLX,
    RLM_SYNC_ERR_SESSION_BAD_PROGRESS,
    RLM_SYNC_ERR_SESSION_REVERT_TO_PBS;

    companion object {
        internal fun of(nativeValue: Int): ProtocolSessionErrorCode?
    }
}
