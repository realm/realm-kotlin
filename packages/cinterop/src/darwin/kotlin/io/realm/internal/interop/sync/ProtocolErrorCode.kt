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

import realm_wrapper.realm_sync_errno_client
import realm_wrapper.realm_sync_errno_connection
import realm_wrapper.realm_sync_errno_session

actual enum class ProtocolClientErrorCode(actual val description: String, val nativeValue: realm_sync_errno_client) {
    RLM_SYNC_ERR_CLIENT_CONNECTION_CLOSED("ConnectionClosed", realm_wrapper.RLM_SYNC_ERR_CLIENT_CONNECTION_CLOSED),
    RLM_SYNC_ERR_CLIENT_UNKNOWN_MESSAGE("UnknownMessage", realm_wrapper.RLM_SYNC_ERR_CLIENT_UNKNOWN_MESSAGE),
    RLM_SYNC_ERR_CLIENT_BAD_SYNTAX("BadSyntax", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_SYNTAX),
    RLM_SYNC_ERR_CLIENT_LIMITS_EXCEEDED("LimitsExceeded", realm_wrapper.RLM_SYNC_ERR_CLIENT_LIMITS_EXCEEDED),
    RLM_SYNC_ERR_CLIENT_BAD_SESSION_IDENT("BadSessionIdent", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_SESSION_IDENT),
    RLM_SYNC_ERR_CLIENT_BAD_MESSAGE_ORDER("BadMessageOrder", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_MESSAGE_ORDER),
    RLM_SYNC_ERR_CLIENT_BAD_CLIENT_FILE_IDENT("BadClientFileIdent", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_CLIENT_FILE_IDENT),
    RLM_SYNC_ERR_CLIENT_BAD_PROGRESS("BadProgress", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_PROGRESS),
    RLM_SYNC_ERR_CLIENT_BAD_CHANGESET_HEADER_SYNTAX("BadChangesetHeaderSyntax", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_CHANGESET_HEADER_SYNTAX),
    RLM_SYNC_ERR_CLIENT_BAD_CHANGESET_SIZE("BadChangesetSize", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_CHANGESET_SIZE),
    RLM_SYNC_ERR_CLIENT_BAD_ORIGIN_FILE_IDENT("BadOriginFileIdent", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_ORIGIN_FILE_IDENT),
    RLM_SYNC_ERR_CLIENT_BAD_SERVER_VERSION("BadServerVersion", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_SERVER_VERSION),
    RLM_SYNC_ERR_CLIENT_BAD_CHANGESET("BadChangeset", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_CHANGESET),
    RLM_SYNC_ERR_CLIENT_BAD_REQUEST_IDENT("BadRequestIdent", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_REQUEST_IDENT),
    RLM_SYNC_ERR_CLIENT_BAD_ERROR_CODE("BadErrorCode", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_ERROR_CODE),
    RLM_SYNC_ERR_CLIENT_BAD_COMPRESSION("BadCompression", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_COMPRESSION),
    RLM_SYNC_ERR_CLIENT_BAD_CLIENT_VERSION("BadClientVersion", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_CLIENT_VERSION),
    RLM_SYNC_ERR_CLIENT_SSL_SERVER_CERT_REJECTED("SslServerCertRejected", realm_wrapper.RLM_SYNC_ERR_CLIENT_SSL_SERVER_CERT_REJECTED),
    RLM_SYNC_ERR_CLIENT_PONG_TIMEOUT("PongTimeout", realm_wrapper.RLM_SYNC_ERR_CLIENT_PONG_TIMEOUT),
    RLM_SYNC_ERR_CLIENT_BAD_CLIENT_FILE_IDENT_SALT("BadClientFileIdentSalt", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_CLIENT_FILE_IDENT_SALT),
    RLM_SYNC_ERR_CLIENT_BAD_FILE_IDENT("BadFileIdent", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_FILE_IDENT),
    RLM_SYNC_ERR_CLIENT_CONNECT_TIMEOUT("ConnectTimeout", realm_wrapper.RLM_SYNC_ERR_CLIENT_CONNECT_TIMEOUT),
    RLM_SYNC_ERR_CLIENT_BAD_TIMESTAMP("BadTimestamp", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_TIMESTAMP),
    RLM_SYNC_ERR_CLIENT_BAD_PROTOCOL_FROM_SERVER("BadProtocolFromServer", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_PROTOCOL_FROM_SERVER),
    RLM_SYNC_ERR_CLIENT_CLIENT_TOO_OLD_FOR_SERVER("ClientTooOldForServer", realm_wrapper.RLM_SYNC_ERR_CLIENT_CLIENT_TOO_OLD_FOR_SERVER),
    RLM_SYNC_ERR_CLIENT_CLIENT_TOO_NEW_FOR_SERVER("ClientTooNewForServer", realm_wrapper.RLM_SYNC_ERR_CLIENT_CLIENT_TOO_NEW_FOR_SERVER),
    RLM_SYNC_ERR_CLIENT_PROTOCOL_MISMATCH("ProtocolMismatch", realm_wrapper.RLM_SYNC_ERR_CLIENT_PROTOCOL_MISMATCH),
    RLM_SYNC_ERR_CLIENT_BAD_STATE_MESSAGE("BadStateMessage", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_STATE_MESSAGE),
    RLM_SYNC_ERR_CLIENT_MISSING_PROTOCOL_FEATURE("MissingProtocolFeature", realm_wrapper.RLM_SYNC_ERR_CLIENT_MISSING_PROTOCOL_FEATURE),
    RLM_SYNC_ERR_CLIENT_HTTP_TUNNEL_FAILED("HttpTunnelFailed", realm_wrapper.RLM_SYNC_ERR_CLIENT_HTTP_TUNNEL_FAILED),
    RLM_SYNC_ERR_SESSION_INVALID_SCHEMA_CHANGE("InvalidSchemaChange", realm_wrapper.RLM_SYNC_ERR_SESSION_INVALID_SCHEMA_CHANGE);

    actual companion object {
        actual fun fromInt(nativeValue: Int): ProtocolClientErrorCode {
            for (value in ProtocolClientErrorCode.values()) {
                if (value.nativeValue.toInt() == nativeValue) {
                    return value
                }
            }
            error("Unknown protocol client error code: $nativeValue")
        }
    }
}

actual enum class ProtocolConnectionErrorCode(actual val description: String, val nativeValue: realm_sync_errno_connection) {
    RLM_SYNC_ERR_CONNECTION_CONNECTION_CLOSED("ConnectionClosed", realm_wrapper.RLM_SYNC_ERR_CONNECTION_CONNECTION_CLOSED),
    RLM_SYNC_ERR_CONNECTION_OTHER_ERROR("OtherError", realm_wrapper.RLM_SYNC_ERR_CONNECTION_OTHER_ERROR),
    RLM_SYNC_ERR_CONNECTION_UNKNOWN_MESSAGE("UnknownMessage", realm_wrapper.RLM_SYNC_ERR_CONNECTION_UNKNOWN_MESSAGE),
    RLM_SYNC_ERR_CONNECTION_BAD_SYNTAX("BadSyntax", realm_wrapper.RLM_SYNC_ERR_CONNECTION_BAD_SYNTAX),
    RLM_SYNC_ERR_CONNECTION_LIMITS_EXCEEDED("LimitsExceeded", realm_wrapper.RLM_SYNC_ERR_CONNECTION_LIMITS_EXCEEDED),
    RLM_SYNC_ERR_CONNECTION_WRONG_PROTOCOL_VERSION("WrongProtocolVersion", realm_wrapper.RLM_SYNC_ERR_CONNECTION_WRONG_PROTOCOL_VERSION),
    RLM_SYNC_ERR_CONNECTION_BAD_SESSION_IDENT("BadSessionIdent", realm_wrapper.RLM_SYNC_ERR_CONNECTION_BAD_SESSION_IDENT),
    RLM_SYNC_ERR_CONNECTION_REUSE_OF_SESSION_IDENT("ReuseOfSessionIdent", realm_wrapper.RLM_SYNC_ERR_CONNECTION_REUSE_OF_SESSION_IDENT),
    RLM_SYNC_ERR_CONNECTION_BOUND_IN_OTHER_SESSION("BoundInOtherSession", realm_wrapper.RLM_SYNC_ERR_CONNECTION_BOUND_IN_OTHER_SESSION),
    RLM_SYNC_ERR_CONNECTION_BAD_MESSAGE_ORDER("BadMessageOrder", realm_wrapper.RLM_SYNC_ERR_CONNECTION_BAD_MESSAGE_ORDER),
    RLM_SYNC_ERR_CONNECTION_BAD_DECOMPRESSION("BadDecompression", realm_wrapper.RLM_SYNC_ERR_CONNECTION_BAD_DECOMPRESSION),
    RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_HEADER_SYNTAX("BadChangesetHeaderSyntax", realm_wrapper.RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_HEADER_SYNTAX),
    RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_SIZE("BadChangesetSize", realm_wrapper.RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_SIZE),
    RLM_SYNC_ERR_CONNECTION_SWITCH_TO_FLX_SYNC("SwitchToFlxSync", realm_wrapper.RLM_SYNC_ERR_CONNECTION_SWITCH_TO_FLX_SYNC),
    RLM_SYNC_ERR_CONNECTION_SWITCH_TO_PBS("SwitchToPbs", realm_wrapper.RLM_SYNC_ERR_CONNECTION_SWITCH_TO_PBS);

    actual companion object {
        actual fun fromInt(nativeValue: Int): ProtocolConnectionErrorCode {
            for (value in ProtocolConnectionErrorCode.values()) {
                if (value.nativeValue.toInt() == nativeValue) {
                    return value
                }
            }
            error("Unknown protocol connection error code: $nativeValue")
        }
    }
}

actual enum class ProtocolSessionErrorCode(actual val description: String, val nativeValue: realm_sync_errno_session) {
    RLM_SYNC_ERR_SESSION_SESSION_CLOSED("SessionClosed", realm_wrapper.RLM_SYNC_ERR_SESSION_SESSION_CLOSED),
    RLM_SYNC_ERR_SESSION_OTHER_SESSION_ERROR("OtherSessioError", realm_wrapper.RLM_SYNC_ERR_SESSION_OTHER_SESSION_ERROR),
    RLM_SYNC_ERR_SESSION_TOKEN_EXPIRED("TokenExpired", realm_wrapper.RLM_SYNC_ERR_SESSION_TOKEN_EXPIRED),
    RLM_SYNC_ERR_SESSION_BAD_AUTHENTICATION("BadAuthentication", realm_wrapper.RLM_SYNC_ERR_SESSION_BAD_AUTHENTICATION),
    RLM_SYNC_ERR_SESSION_ILLEGAL_REALM_PATH("IllegalRealmPath", realm_wrapper.RLM_SYNC_ERR_SESSION_ILLEGAL_REALM_PATH),
    RLM_SYNC_ERR_SESSION_NO_SUCH_REALM("NoSuchRealm", realm_wrapper.RLM_SYNC_ERR_SESSION_NO_SUCH_REALM),
    RLM_SYNC_ERR_SESSION_PERMISSION_DENIED("PermissionDenied", realm_wrapper.RLM_SYNC_ERR_SESSION_PERMISSION_DENIED),
    RLM_SYNC_ERR_SESSION_BAD_SERVER_FILE_IDENT("BadServerFileIdent", realm_wrapper.RLM_SYNC_ERR_SESSION_BAD_SERVER_FILE_IDENT),
    RLM_SYNC_ERR_SESSION_BAD_CLIENT_FILE_IDENT("BadClientFileIdent", realm_wrapper.RLM_SYNC_ERR_SESSION_BAD_CLIENT_FILE_IDENT),
    RLM_SYNC_ERR_SESSION_BAD_SERVER_VERSION("BadServerVersion", realm_wrapper.RLM_SYNC_ERR_SESSION_BAD_SERVER_VERSION),
    RLM_SYNC_ERR_SESSION_BAD_CLIENT_VERSION("BadClientVersion", realm_wrapper.RLM_SYNC_ERR_SESSION_BAD_CLIENT_VERSION),
    RLM_SYNC_ERR_SESSION_DIVERGING_HISTORIES("DivergingHistories", realm_wrapper.RLM_SYNC_ERR_SESSION_DIVERGING_HISTORIES),
    RLM_SYNC_ERR_SESSION_BAD_CHANGESET("BadChangeset", realm_wrapper.RLM_SYNC_ERR_SESSION_BAD_CHANGESET),
    RLM_SYNC_ERR_SESSION_PARTIAL_SYNC_DISABLED("PartialSyncDisabled", realm_wrapper.RLM_SYNC_ERR_SESSION_PARTIAL_SYNC_DISABLED),
    RLM_SYNC_ERR_SESSION_UNSUPPORTED_SESSION_FEATURE("UnsupportedSessionFeature", realm_wrapper.RLM_SYNC_ERR_SESSION_UNSUPPORTED_SESSION_FEATURE),
    RLM_SYNC_ERR_SESSION_BAD_ORIGIN_FILE_IDENT("BadOriginFileIdent", realm_wrapper.RLM_SYNC_ERR_SESSION_BAD_ORIGIN_FILE_IDENT),
    RLM_SYNC_ERR_SESSION_BAD_CLIENT_FILE("BadClientFile", realm_wrapper.RLM_SYNC_ERR_SESSION_BAD_CLIENT_FILE),
    RLM_SYNC_ERR_SESSION_SERVER_FILE_DELETED("ServerFileDeleted", realm_wrapper.RLM_SYNC_ERR_SESSION_SERVER_FILE_DELETED),
    RLM_SYNC_ERR_SESSION_CLIENT_FILE_BLACKLISTED("ClientFileBlacklisted", realm_wrapper.RLM_SYNC_ERR_SESSION_CLIENT_FILE_BLACKLISTED),
    RLM_SYNC_ERR_SESSION_USER_BLACKLISTED("UserBlacklisted", realm_wrapper.RLM_SYNC_ERR_SESSION_USER_BLACKLISTED),
    RLM_SYNC_ERR_SESSION_TRANSACT_BEFORE_UPLOAD("TransactBeforeUpload", realm_wrapper.RLM_SYNC_ERR_SESSION_TRANSACT_BEFORE_UPLOAD),
    RLM_SYNC_ERR_SESSION_CLIENT_FILE_EXPIRED("ClientFileExpired", realm_wrapper.RLM_SYNC_ERR_SESSION_CLIENT_FILE_EXPIRED),
    RLM_SYNC_ERR_SESSION_USER_MISMATCH("UserMismatch", realm_wrapper.RLM_SYNC_ERR_SESSION_USER_MISMATCH),
    RLM_SYNC_ERR_SESSION_TOO_MANY_SESSIONS("TooManySession", realm_wrapper.RLM_SYNC_ERR_SESSION_TOO_MANY_SESSIONS),
    RLM_SYNC_ERR_SESSION_INVALID_SCHEMA_CHANGE("InvalidSchemaChange", realm_wrapper.RLM_SYNC_ERR_SESSION_INVALID_SCHEMA_CHANGE);

    actual companion object {
        actual fun fromInt(nativeValue: Int): ProtocolSessionErrorCode {
            for (value in ProtocolSessionErrorCode.values()) {
                if (value.nativeValue.toInt() == nativeValue) {
                    return value
                }
            }
            error("Unknown protocol session error code: $nativeValue")
        }
    }
}
