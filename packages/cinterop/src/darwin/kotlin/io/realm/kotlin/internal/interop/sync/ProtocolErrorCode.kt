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

actual enum class ProtocolClientErrorCode(
    override val description: String,
    override val nativeValue: Int
) : CodeDescription {
    RLM_SYNC_ERR_CLIENT_CONNECTION_CLOSED("ConnectionClosed", realm_wrapper.RLM_SYNC_ERR_CLIENT_CONNECTION_CLOSED.toInt()),
    RLM_SYNC_ERR_CLIENT_UNKNOWN_MESSAGE("UnknownMessage", realm_wrapper.RLM_SYNC_ERR_CLIENT_UNKNOWN_MESSAGE.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_SYNTAX("BadSyntax", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_SYNTAX.toInt()),
    RLM_SYNC_ERR_CLIENT_LIMITS_EXCEEDED("LimitsExceeded", realm_wrapper.RLM_SYNC_ERR_CLIENT_LIMITS_EXCEEDED.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_SESSION_IDENT("BadSessionIdent", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_SESSION_IDENT.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_MESSAGE_ORDER("BadMessageOrder", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_MESSAGE_ORDER.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_CLIENT_FILE_IDENT("BadClientFileIdent", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_CLIENT_FILE_IDENT.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_PROGRESS("BadProgress", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_PROGRESS.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_CHANGESET_HEADER_SYNTAX("BadChangesetHeaderSyntax", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_CHANGESET_HEADER_SYNTAX.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_CHANGESET_SIZE("BadChangesetSize", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_CHANGESET_SIZE.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_ORIGIN_FILE_IDENT("BadOriginFileIdent", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_ORIGIN_FILE_IDENT.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_SERVER_VERSION("BadServerVersion", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_SERVER_VERSION.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_CHANGESET("BadChangeset", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_CHANGESET.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_REQUEST_IDENT("BadRequestIdent", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_REQUEST_IDENT.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_ERROR_CODE("BadErrorCode", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_ERROR_CODE.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_COMPRESSION("BadCompression", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_COMPRESSION.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_CLIENT_VERSION("BadClientVersion", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_CLIENT_VERSION.toInt()),
    RLM_SYNC_ERR_CLIENT_SSL_SERVER_CERT_REJECTED("SslServerCertRejected", realm_wrapper.RLM_SYNC_ERR_CLIENT_SSL_SERVER_CERT_REJECTED.toInt()),
    RLM_SYNC_ERR_CLIENT_PONG_TIMEOUT("PongTimeout", realm_wrapper.RLM_SYNC_ERR_CLIENT_PONG_TIMEOUT.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_CLIENT_FILE_IDENT_SALT("BadClientFileIdentSalt", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_CLIENT_FILE_IDENT_SALT.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_FILE_IDENT("BadFileIdent", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_FILE_IDENT.toInt()),
    RLM_SYNC_ERR_CLIENT_CONNECT_TIMEOUT("ConnectTimeout", realm_wrapper.RLM_SYNC_ERR_CLIENT_CONNECT_TIMEOUT.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_TIMESTAMP("BadTimestamp", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_TIMESTAMP.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_PROTOCOL_FROM_SERVER("BadProtocolFromServer", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_PROTOCOL_FROM_SERVER.toInt()),
    RLM_SYNC_ERR_CLIENT_CLIENT_TOO_OLD_FOR_SERVER("ClientTooOldForServer", realm_wrapper.RLM_SYNC_ERR_CLIENT_CLIENT_TOO_OLD_FOR_SERVER.toInt()),
    RLM_SYNC_ERR_CLIENT_CLIENT_TOO_NEW_FOR_SERVER("ClientTooNewForServer", realm_wrapper.RLM_SYNC_ERR_CLIENT_CLIENT_TOO_NEW_FOR_SERVER.toInt()),
    RLM_SYNC_ERR_CLIENT_PROTOCOL_MISMATCH("ProtocolMismatch", realm_wrapper.RLM_SYNC_ERR_CLIENT_PROTOCOL_MISMATCH.toInt()),
    RLM_SYNC_ERR_CLIENT_BAD_STATE_MESSAGE("BadStateMessage", realm_wrapper.RLM_SYNC_ERR_CLIENT_BAD_STATE_MESSAGE.toInt()),
    RLM_SYNC_ERR_CLIENT_MISSING_PROTOCOL_FEATURE("MissingProtocolFeature", realm_wrapper.RLM_SYNC_ERR_CLIENT_MISSING_PROTOCOL_FEATURE.toInt()),
    RLM_SYNC_ERR_CLIENT_HTTP_TUNNEL_FAILED("HttpTunnelFailed", realm_wrapper.RLM_SYNC_ERR_CLIENT_HTTP_TUNNEL_FAILED.toInt()),
    RLM_SYNC_ERR_SESSION_INVALID_SCHEMA_CHANGE("InvalidSchemaChange", realm_wrapper.RLM_SYNC_ERR_SESSION_INVALID_SCHEMA_CHANGE.toInt()),
    RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE("AutoClientResetFailure", realm_wrapper.RLM_SYNC_ERR_CLIENT_AUTO_CLIENT_RESET_FAILURE.toInt());

    actual companion object {
        internal actual fun of(nativeValue: Int): ProtocolClientErrorCode? =
            values().firstOrNull { value ->
                value.nativeValue == nativeValue
            }
    }
}

actual enum class ProtocolConnectionErrorCode(
    override val description: String,
    override val nativeValue: Int
) : CodeDescription {
    RLM_SYNC_ERR_CONNECTION_CONNECTION_CLOSED("ConnectionClosed", realm_wrapper.RLM_SYNC_ERR_CONNECTION_CONNECTION_CLOSED.toInt()),
    RLM_SYNC_ERR_CONNECTION_OTHER_ERROR("OtherError", realm_wrapper.RLM_SYNC_ERR_CONNECTION_OTHER_ERROR.toInt()),
    RLM_SYNC_ERR_CONNECTION_UNKNOWN_MESSAGE("UnknownMessage", realm_wrapper.RLM_SYNC_ERR_CONNECTION_UNKNOWN_MESSAGE.toInt()),
    RLM_SYNC_ERR_CONNECTION_BAD_SYNTAX("BadSyntax", realm_wrapper.RLM_SYNC_ERR_CONNECTION_BAD_SYNTAX.toInt()),
    RLM_SYNC_ERR_CONNECTION_LIMITS_EXCEEDED("LimitsExceeded", realm_wrapper.RLM_SYNC_ERR_CONNECTION_LIMITS_EXCEEDED.toInt()),
    RLM_SYNC_ERR_CONNECTION_WRONG_PROTOCOL_VERSION("WrongProtocolVersion", realm_wrapper.RLM_SYNC_ERR_CONNECTION_WRONG_PROTOCOL_VERSION.toInt()),
    RLM_SYNC_ERR_CONNECTION_BAD_SESSION_IDENT("BadSessionIdent", realm_wrapper.RLM_SYNC_ERR_CONNECTION_BAD_SESSION_IDENT.toInt()),
    RLM_SYNC_ERR_CONNECTION_REUSE_OF_SESSION_IDENT("ReuseOfSessionIdent", realm_wrapper.RLM_SYNC_ERR_CONNECTION_REUSE_OF_SESSION_IDENT.toInt()),
    RLM_SYNC_ERR_CONNECTION_BOUND_IN_OTHER_SESSION("BoundInOtherSession", realm_wrapper.RLM_SYNC_ERR_CONNECTION_BOUND_IN_OTHER_SESSION.toInt()),
    RLM_SYNC_ERR_CONNECTION_BAD_MESSAGE_ORDER("BadMessageOrder", realm_wrapper.RLM_SYNC_ERR_CONNECTION_BAD_MESSAGE_ORDER.toInt()),
    RLM_SYNC_ERR_CONNECTION_BAD_DECOMPRESSION("BadDecompression", realm_wrapper.RLM_SYNC_ERR_CONNECTION_BAD_DECOMPRESSION.toInt()),
    RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_HEADER_SYNTAX("BadChangesetHeaderSyntax", realm_wrapper.RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_HEADER_SYNTAX.toInt()),
    RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_SIZE("BadChangesetSize", realm_wrapper.RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_SIZE.toInt()),
    RLM_SYNC_ERR_CONNECTION_SWITCH_TO_FLX_SYNC("SwitchToFlxSync", realm_wrapper.RLM_SYNC_ERR_CONNECTION_SWITCH_TO_FLX_SYNC.toInt()),
    RLM_SYNC_ERR_CONNECTION_SWITCH_TO_PBS("SwitchToPbs", realm_wrapper.RLM_SYNC_ERR_CONNECTION_SWITCH_TO_PBS.toInt());

    actual companion object {
        internal actual fun of(nativeValue: Int): ProtocolConnectionErrorCode? =
            values().firstOrNull { value ->
                value.nativeValue == nativeValue
            }
    }
}

actual enum class ProtocolSessionErrorCode(
    override val description: String,
    override val nativeValue: Int
) : CodeDescription {
    RLM_SYNC_ERR_SESSION_SESSION_CLOSED("SessionClosed", realm_wrapper.RLM_SYNC_ERR_SESSION_SESSION_CLOSED.toInt()),
    RLM_SYNC_ERR_SESSION_OTHER_SESSION_ERROR("OtherSessioError", realm_wrapper.RLM_SYNC_ERR_SESSION_OTHER_SESSION_ERROR.toInt()),
    RLM_SYNC_ERR_SESSION_TOKEN_EXPIRED("TokenExpired", realm_wrapper.RLM_SYNC_ERR_SESSION_TOKEN_EXPIRED.toInt()),
    RLM_SYNC_ERR_SESSION_BAD_AUTHENTICATION("BadAuthentication", realm_wrapper.RLM_SYNC_ERR_SESSION_BAD_AUTHENTICATION.toInt()),
    RLM_SYNC_ERR_SESSION_ILLEGAL_REALM_PATH("IllegalRealmPath", realm_wrapper.RLM_SYNC_ERR_SESSION_ILLEGAL_REALM_PATH.toInt()),
    RLM_SYNC_ERR_SESSION_NO_SUCH_REALM("NoSuchRealm", realm_wrapper.RLM_SYNC_ERR_SESSION_NO_SUCH_REALM.toInt()),
    RLM_SYNC_ERR_SESSION_PERMISSION_DENIED("PermissionDenied", realm_wrapper.RLM_SYNC_ERR_SESSION_PERMISSION_DENIED.toInt()),
    RLM_SYNC_ERR_SESSION_BAD_SERVER_FILE_IDENT("BadServerFileIdent", realm_wrapper.RLM_SYNC_ERR_SESSION_BAD_SERVER_FILE_IDENT.toInt()),
    RLM_SYNC_ERR_SESSION_BAD_CLIENT_FILE_IDENT("BadClientFileIdent", realm_wrapper.RLM_SYNC_ERR_SESSION_BAD_CLIENT_FILE_IDENT.toInt()),
    RLM_SYNC_ERR_SESSION_BAD_SERVER_VERSION("BadServerVersion", realm_wrapper.RLM_SYNC_ERR_SESSION_BAD_SERVER_VERSION.toInt()),
    RLM_SYNC_ERR_SESSION_BAD_CLIENT_VERSION("BadClientVersion", realm_wrapper.RLM_SYNC_ERR_SESSION_BAD_CLIENT_VERSION.toInt()),
    RLM_SYNC_ERR_SESSION_DIVERGING_HISTORIES("DivergingHistories", realm_wrapper.RLM_SYNC_ERR_SESSION_DIVERGING_HISTORIES.toInt()),
    RLM_SYNC_ERR_SESSION_BAD_CHANGESET("BadChangeset", realm_wrapper.RLM_SYNC_ERR_SESSION_BAD_CHANGESET.toInt()),
    RLM_SYNC_ERR_SESSION_PARTIAL_SYNC_DISABLED("PartialSyncDisabled", realm_wrapper.RLM_SYNC_ERR_SESSION_PARTIAL_SYNC_DISABLED.toInt()),
    RLM_SYNC_ERR_SESSION_UNSUPPORTED_SESSION_FEATURE("UnsupportedSessionFeature", realm_wrapper.RLM_SYNC_ERR_SESSION_UNSUPPORTED_SESSION_FEATURE.toInt()),
    RLM_SYNC_ERR_SESSION_BAD_ORIGIN_FILE_IDENT("BadOriginFileIdent", realm_wrapper.RLM_SYNC_ERR_SESSION_BAD_ORIGIN_FILE_IDENT.toInt()),
    RLM_SYNC_ERR_SESSION_BAD_CLIENT_FILE("BadClientFile", realm_wrapper.RLM_SYNC_ERR_SESSION_BAD_CLIENT_FILE.toInt()),
    RLM_SYNC_ERR_SESSION_SERVER_FILE_DELETED("ServerFileDeleted", realm_wrapper.RLM_SYNC_ERR_SESSION_SERVER_FILE_DELETED.toInt()),
    RLM_SYNC_ERR_SESSION_CLIENT_FILE_BLACKLISTED("ClientFileBlacklisted", realm_wrapper.RLM_SYNC_ERR_SESSION_CLIENT_FILE_BLACKLISTED.toInt()),
    RLM_SYNC_ERR_SESSION_USER_BLACKLISTED("UserBlacklisted", realm_wrapper.RLM_SYNC_ERR_SESSION_USER_BLACKLISTED.toInt()),
    RLM_SYNC_ERR_SESSION_TRANSACT_BEFORE_UPLOAD("TransactBeforeUpload", realm_wrapper.RLM_SYNC_ERR_SESSION_TRANSACT_BEFORE_UPLOAD.toInt()),
    RLM_SYNC_ERR_SESSION_CLIENT_FILE_EXPIRED("ClientFileExpired", realm_wrapper.RLM_SYNC_ERR_SESSION_CLIENT_FILE_EXPIRED.toInt()),
    RLM_SYNC_ERR_SESSION_USER_MISMATCH("UserMismatch", realm_wrapper.RLM_SYNC_ERR_SESSION_USER_MISMATCH.toInt()),
    RLM_SYNC_ERR_SESSION_TOO_MANY_SESSIONS("TooManySession", realm_wrapper.RLM_SYNC_ERR_SESSION_TOO_MANY_SESSIONS.toInt()),
    RLM_SYNC_ERR_SESSION_INVALID_SCHEMA_CHANGE("InvalidSchemaChange", realm_wrapper.RLM_SYNC_ERR_SESSION_INVALID_SCHEMA_CHANGE.toInt()),
    RLM_SYNC_ERR_SESSION_BAD_QUERY("BadQuery", realm_wrapper.RLM_SYNC_ERR_SESSION_BAD_QUERY.toInt()),
    RLM_SYNC_ERR_SESSION_OBJECT_ALREADY_EXISTS("ObjectAlreadyExists", realm_wrapper.RLM_SYNC_ERR_SESSION_OBJECT_ALREADY_EXISTS.toInt()),
    RLM_SYNC_ERR_SESSION_SERVER_PERMISSIONS_CHANGED("ServerPermissionsChanged", realm_wrapper.RLM_SYNC_ERR_SESSION_SERVER_PERMISSIONS_CHANGED.toInt()),
    RLM_SYNC_ERR_SESSION_INITIAL_SYNC_NOT_COMPLETED("InitialSyncNotCompleted", realm_wrapper.RLM_SYNC_ERR_SESSION_INITIAL_SYNC_NOT_COMPLETED.toInt()),
    RLM_SYNC_ERR_SESSION_WRITE_NOT_ALLOWED("WriteNotAllowed", realm_wrapper.RLM_SYNC_ERR_SESSION_WRITE_NOT_ALLOWED.toInt());

    actual companion object {
        internal actual fun of(nativeValue: Int): ProtocolSessionErrorCode? =
            values().firstOrNull { value ->
                value.nativeValue == nativeValue
            }
    }
}
