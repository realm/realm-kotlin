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

package io.realm.internal.interop.sync

import io.realm.internal.interop.NativeEnumerated
import io.realm.internal.interop.realm_sync_errno_client_e
import io.realm.internal.interop.realm_sync_errno_connection_e
import io.realm.internal.interop.realm_sync_errno_session_e

actual enum class ProtocolClientErrorCode(override val nativeValue: Int) : NativeEnumerated {
    RLM_SYNC_ERR_CLIENT_CONNECTION_CLOSED(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_CONNECTION_CLOSED),
    RLM_SYNC_ERR_CLIENT_UNKNOWN_MESSAGE(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_UNKNOWN_MESSAGE),
    RLM_SYNC_ERR_CLIENT_BAD_SYNTAX(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_SYNTAX),
    RLM_SYNC_ERR_CLIENT_LIMITS_EXCEEDED(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_LIMITS_EXCEEDED),
    RLM_SYNC_ERR_CLIENT_BAD_SESSION_IDENT(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_SESSION_IDENT),
    RLM_SYNC_ERR_CLIENT_BAD_MESSAGE_ORDER(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_MESSAGE_ORDER),
    RLM_SYNC_ERR_CLIENT_BAD_CLIENT_FILE_IDENT(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_CLIENT_FILE_IDENT),
    RLM_SYNC_ERR_CLIENT_BAD_PROGRESS(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_PROGRESS),
    RLM_SYNC_ERR_CLIENT_BAD_CHANGESET_HEADER_SYNTAX(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_CHANGESET_HEADER_SYNTAX),
    RLM_SYNC_ERR_CLIENT_BAD_CHANGESET_SIZE(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_CHANGESET_SIZE),
    RLM_SYNC_ERR_CLIENT_BAD_ORIGIN_FILE_IDENT(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_ORIGIN_FILE_IDENT),
    RLM_SYNC_ERR_CLIENT_BAD_SERVER_VERSION(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_SERVER_VERSION),
    RLM_SYNC_ERR_CLIENT_BAD_CHANGESET(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_CHANGESET),
    RLM_SYNC_ERR_CLIENT_BAD_REQUEST_IDENT(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_REQUEST_IDENT),
    RLM_SYNC_ERR_CLIENT_BAD_ERROR_CODE(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_ERROR_CODE),
    RLM_SYNC_ERR_CLIENT_BAD_COMPRESSION(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_COMPRESSION),
    RLM_SYNC_ERR_CLIENT_BAD_CLIENT_VERSION(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_CLIENT_VERSION),
    RLM_SYNC_ERR_CLIENT_SSL_SERVER_CERT_REJECTED(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_SSL_SERVER_CERT_REJECTED),
    RLM_SYNC_ERR_CLIENT_PONG_TIMEOUT(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_PONG_TIMEOUT),
    RLM_SYNC_ERR_CLIENT_BAD_CLIENT_FILE_IDENT_SALT(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_CLIENT_FILE_IDENT_SALT),
    RLM_SYNC_ERR_CLIENT_BAD_FILE_IDENT(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_FILE_IDENT),
    RLM_SYNC_ERR_CLIENT_CONNECT_TIMEOUT(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_CONNECT_TIMEOUT),
    RLM_SYNC_ERR_CLIENT_BAD_TIMESTAMP(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_TIMESTAMP),
    RLM_SYNC_ERR_CLIENT_BAD_PROTOCOL_FROM_SERVER(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_PROTOCOL_FROM_SERVER),
    RLM_SYNC_ERR_CLIENT_CLIENT_TOO_OLD_FOR_SERVER(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_CLIENT_TOO_OLD_FOR_SERVER),
    RLM_SYNC_ERR_CLIENT_CLIENT_TOO_NEW_FOR_SERVER(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_CLIENT_TOO_NEW_FOR_SERVER),
    RLM_SYNC_ERR_CLIENT_PROTOCOL_MISMATCH(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_PROTOCOL_MISMATCH),
    RLM_SYNC_ERR_CLIENT_BAD_STATE_MESSAGE(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_BAD_STATE_MESSAGE),
    RLM_SYNC_ERR_CLIENT_MISSING_PROTOCOL_FEATURE(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_MISSING_PROTOCOL_FEATURE),
    RLM_SYNC_ERR_CLIENT_HTTP_TUNNEL_FAILED(realm_sync_errno_client_e.RLM_SYNC_ERR_CLIENT_HTTP_TUNNEL_FAILED);

    actual companion object {
        actual fun fromInt(nativeValue: Int): ProtocolClientErrorCode {
            for (value in values()) {
                if (value.nativeValue == nativeValue) {
                    return value
                }
            }
            error("Unknown protocol client error code: $nativeValue")
        }
    }
}

actual enum class ProtocolConnectionErrorCode(override val nativeValue: Int) : NativeEnumerated {
    RLM_SYNC_ERR_CONNECTION_CONNECTION_CLOSED(realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_CONNECTION_CLOSED),
    RLM_SYNC_ERR_CONNECTION_OTHER_ERROR(realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_OTHER_ERROR),
    RLM_SYNC_ERR_CONNECTION_UNKNOWN_MESSAGE(realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_UNKNOWN_MESSAGE),
    RLM_SYNC_ERR_CONNECTION_BAD_SYNTAX(realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_BAD_SYNTAX),
    RLM_SYNC_ERR_CONNECTION_LIMITS_EXCEEDED(realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_LIMITS_EXCEEDED),
    RLM_SYNC_ERR_CONNECTION_WRONG_PROTOCOL_VERSION(realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_WRONG_PROTOCOL_VERSION),
    RLM_SYNC_ERR_CONNECTION_BAD_SESSION_IDENT(realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_BAD_SESSION_IDENT),
    RLM_SYNC_ERR_CONNECTION_REUSE_OF_SESSION_IDENT(realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_REUSE_OF_SESSION_IDENT),
    RLM_SYNC_ERR_CONNECTION_BOUND_IN_OTHER_SESSION(realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_BOUND_IN_OTHER_SESSION),
    RLM_SYNC_ERR_CONNECTION_BAD_MESSAGE_ORDER(realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_BAD_MESSAGE_ORDER),
    RLM_SYNC_ERR_CONNECTION_BAD_DECOMPRESSION(realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_BAD_DECOMPRESSION),
    RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_HEADER_SYNTAX(realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_HEADER_SYNTAX),
    RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_SIZE(realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_SIZE),
    RLM_SYNC_ERR_CONNECTION_SWITCH_TO_FLX_SYNC(realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_SWITCH_TO_FLX_SYNC),
    RLM_SYNC_ERR_CONNECTION_SWITCH_TO_PBS(realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_SWITCH_TO_PBS);

    actual companion object {
        actual fun fromInt(nativeValue: Int): ProtocolConnectionErrorCode {
            for (value in values()) {
                if (value.nativeValue == nativeValue) {
                    return value
                }
            }
            error("Unknown protocol connection error code: $nativeValue")
        }
    }
}

actual enum class ProtocolSessionErrorCode(override val nativeValue: Int) : NativeEnumerated {
    RLM_SYNC_ERR_SESSION_SESSION_CLOSED(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_SESSION_CLOSED),
    RLM_SYNC_ERR_SESSION_OTHER_SESSION_ERROR(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_OTHER_SESSION_ERROR),
    RLM_SYNC_ERR_SESSION_TOKEN_EXPIRED(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_TOKEN_EXPIRED),
    RLM_SYNC_ERR_SESSION_BAD_AUTHENTICATION(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_AUTHENTICATION),
    RLM_SYNC_ERR_SESSION_ILLEGAL_REALM_PATH(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_ILLEGAL_REALM_PATH),
    RLM_SYNC_ERR_SESSION_NO_SUCH_REALM(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_NO_SUCH_REALM),
    RLM_SYNC_ERR_SESSION_PERMISSION_DENIED(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_PERMISSION_DENIED),
    RLM_SYNC_ERR_SESSION_BAD_SERVER_FILE_IDENT(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_SERVER_FILE_IDENT),
    RLM_SYNC_ERR_SESSION_BAD_CLIENT_FILE_IDENT(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_CLIENT_FILE_IDENT),
    RLM_SYNC_ERR_SESSION_BAD_SERVER_VERSION(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_SERVER_VERSION),
    RLM_SYNC_ERR_SESSION_BAD_CLIENT_VERSION(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_CLIENT_VERSION),
    RLM_SYNC_ERR_SESSION_DIVERGING_HISTORIES(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_DIVERGING_HISTORIES),
    RLM_SYNC_ERR_SESSION_BAD_CHANGESET(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_CHANGESET),
    RLM_SYNC_ERR_SESSION_SUPERSEDED(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_SUPERSEDED),
    RLM_SYNC_ERR_SESSION_DISABLED_SESSION(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_DISABLED_SESSION),
    RLM_SYNC_ERR_SESSION_PARTIAL_SYNC_DISABLED(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_PARTIAL_SYNC_DISABLED),
    RLM_SYNC_ERR_SESSION_UNSUPPORTED_SESSION_FEATURE(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_UNSUPPORTED_SESSION_FEATURE),
    RLM_SYNC_ERR_SESSION_BAD_ORIGIN_FILE_IDENT(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_ORIGIN_FILE_IDENT),
    RLM_SYNC_ERR_SESSION_BAD_CLIENT_FILE(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_CLIENT_FILE),
    RLM_SYNC_ERR_SESSION_SERVER_FILE_DELETED(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_SERVER_FILE_DELETED),
    RLM_SYNC_ERR_SESSION_CLIENT_FILE_BLACKLISTED(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_CLIENT_FILE_BLACKLISTED),
    RLM_SYNC_ERR_SESSION_USER_BLACKLISTED(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_USER_BLACKLISTED),
    RLM_SYNC_ERR_SESSION_TRANSACT_BEFORE_UPLOAD(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_TRANSACT_BEFORE_UPLOAD),
    RLM_SYNC_ERR_SESSION_CLIENT_FILE_EXPIRED(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_CLIENT_FILE_EXPIRED),
    RLM_SYNC_ERR_SESSION_USER_MISMATCH(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_USER_MISMATCH),
    RLM_SYNC_ERR_SESSION_TOO_MANY_SESSIONS(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_TOO_MANY_SESSIONS),
    RLM_SYNC_ERR_SESSION_INVALID_SCHEMA_CHANGE(realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_INVALID_SCHEMA_CHANGE);

    actual companion object {
        actual fun fromInt(nativeValue: Int): ProtocolSessionErrorCode {
            for (value in values()) {
                if (value.nativeValue == nativeValue) {
                    return value
                }
            }
            error("Unknown protocol session error code: $nativeValue")
        }
    }
}
