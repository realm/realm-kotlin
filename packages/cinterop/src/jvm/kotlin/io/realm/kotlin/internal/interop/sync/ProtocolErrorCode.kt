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
import io.realm.kotlin.internal.interop.realm_sync_errno_connection_e
import io.realm.kotlin.internal.interop.realm_sync_errno_session_e
import io.realm.kotlin.internal.interop.realm_sync_socket_callback_result_e
import io.realm.kotlin.internal.interop.realm_web_socket_errno_e

actual enum class SyncConnectionErrorCode(
    actual override val description: String?,
    actual override val nativeValue: Int
) : CodeDescription {
    RLM_SYNC_ERR_CONNECTION_CONNECTION_CLOSED("ConnectionClosed", realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_CONNECTION_CLOSED),
    RLM_SYNC_ERR_CONNECTION_OTHER_ERROR("OtherError", realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_OTHER_ERROR),
    RLM_SYNC_ERR_CONNECTION_UNKNOWN_MESSAGE("UnknownMessage", realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_UNKNOWN_MESSAGE),
    RLM_SYNC_ERR_CONNECTION_BAD_SYNTAX("BadSyntax", realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_BAD_SYNTAX),
    RLM_SYNC_ERR_CONNECTION_LIMITS_EXCEEDED("LimitsExceeded", realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_LIMITS_EXCEEDED),
    RLM_SYNC_ERR_CONNECTION_WRONG_PROTOCOL_VERSION("WrongBadSyncPartitionValueProtocolVersion", realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_WRONG_PROTOCOL_VERSION),
    RLM_SYNC_ERR_CONNECTION_BAD_SESSION_IDENT("BadSessionIdent", realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_BAD_SESSION_IDENT),
    RLM_SYNC_ERR_CONNECTION_REUSE_OF_SESSION_IDENT("ReuseOfSessionIdent", realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_REUSE_OF_SESSION_IDENT),
    RLM_SYNC_ERR_CONNECTION_BOUND_IN_OTHER_SESSION("BoundInOtherSession", realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_BOUND_IN_OTHER_SESSION),
    RLM_SYNC_ERR_CONNECTION_BAD_MESSAGE_ORDER("BadMessageOrder", realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_BAD_MESSAGE_ORDER),
    RLM_SYNC_ERR_CONNECTION_BAD_DECOMPRESSION("BadDecompression", realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_BAD_DECOMPRESSION),
    RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_HEADER_SYNTAX("BadChangesetHeaderSyntax", realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_HEADER_SYNTAX),
    RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_SIZE("BadChangesetSize", realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_SIZE),
    RLM_SYNC_ERR_CONNECTION_SWITCH_TO_FLX_SYNC("SwitchToFlxSync", realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_SWITCH_TO_FLX_SYNC),
    RLM_SYNC_ERR_CONNECTION_SWITCH_TO_PBS("SwitchToPbs", realm_sync_errno_connection_e.RLM_SYNC_ERR_CONNECTION_SWITCH_TO_PBS);

    companion object {
        internal fun of(nativeValue: Int): SyncConnectionErrorCode? =
            entries.firstOrNull { value ->
                value.nativeValue == nativeValue
            }
    }
}

actual enum class SyncSessionErrorCode(
    actual override val description: String?,
    actual override val nativeValue: Int
) : CodeDescription {
    RLM_SYNC_ERR_SESSION_SESSION_CLOSED("SessionClosed", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_SESSION_CLOSED),
    RLM_SYNC_ERR_SESSION_OTHER_SESSION_ERROR("OtherSessioError", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_OTHER_SESSION_ERROR),
    RLM_SYNC_ERR_SESSION_TOKEN_EXPIRED("TokenExpired", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_TOKEN_EXPIRED),
    RLM_SYNC_ERR_SESSION_BAD_AUTHENTICATION("BadAuthentication", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_AUTHENTICATION),
    RLM_SYNC_ERR_SESSION_ILLEGAL_REALM_PATH("IllegalRealmPath", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_ILLEGAL_REALM_PATH),
    RLM_SYNC_ERR_SESSION_NO_SUCH_REALM("NoSuchRealm", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_NO_SUCH_REALM),
    RLM_SYNC_ERR_SESSION_PERMISSION_DENIED("PermissionDenied", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_PERMISSION_DENIED),
    RLM_SYNC_ERR_SESSION_BAD_SERVER_FILE_IDENT("BadServerFileIdent", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_SERVER_FILE_IDENT),
    RLM_SYNC_ERR_SESSION_BAD_CLIENT_FILE_IDENT("BadClientFileIdent", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_CLIENT_FILE_IDENT),
    RLM_SYNC_ERR_SESSION_BAD_SERVER_VERSION("BadServerVersion", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_SERVER_VERSION),
    RLM_SYNC_ERR_SESSION_BAD_CLIENT_VERSION("BadClientVersion", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_CLIENT_VERSION),
    RLM_SYNC_ERR_SESSION_DIVERGING_HISTORIES("DivergingHistories", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_DIVERGING_HISTORIES),
    RLM_SYNC_ERR_SESSION_BAD_CHANGESET("BadChangeset", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_CHANGESET),
    RLM_SYNC_ERR_SESSION_PARTIAL_SYNC_DISABLED("PartialSyncDisabled", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_PARTIAL_SYNC_DISABLED),
    RLM_SYNC_ERR_SESSION_UNSUPPORTED_SESSION_FEATURE("UnsupportedSessionFeature", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_UNSUPPORTED_SESSION_FEATURE),
    RLM_SYNC_ERR_SESSION_BAD_ORIGIN_FILE_IDENT("BadOriginFileIdent", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_ORIGIN_FILE_IDENT),
    RLM_SYNC_ERR_SESSION_BAD_CLIENT_FILE("BadClientFile", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_CLIENT_FILE),
    RLM_SYNC_ERR_SESSION_SERVER_FILE_DELETED("ServerFileDeleted", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_SERVER_FILE_DELETED),
    RLM_SYNC_ERR_SESSION_CLIENT_FILE_BLACKLISTED("ClientFileBlacklisted", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_CLIENT_FILE_BLACKLISTED),
    RLM_SYNC_ERR_SESSION_USER_BLACKLISTED("UserBlacklisted", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_USER_BLACKLISTED),
    RLM_SYNC_ERR_SESSION_TRANSACT_BEFORE_UPLOAD("TransactBeforeUpload", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_TRANSACT_BEFORE_UPLOAD),
    RLM_SYNC_ERR_SESSION_CLIENT_FILE_EXPIRED("ClientFileExpired", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_CLIENT_FILE_EXPIRED),
    RLM_SYNC_ERR_SESSION_USER_MISMATCH("UserMismatch", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_USER_MISMATCH),
    RLM_SYNC_ERR_SESSION_TOO_MANY_SESSIONS("TooManySession", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_TOO_MANY_SESSIONS),
    RLM_SYNC_ERR_SESSION_INVALID_SCHEMA_CHANGE("InvalidSchemaChange", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_INVALID_SCHEMA_CHANGE),
    RLM_SYNC_ERR_SESSION_BAD_QUERY("BadQuery", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_QUERY),
    RLM_SYNC_ERR_SESSION_OBJECT_ALREADY_EXISTS("ObjectAlreadyExists", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_OBJECT_ALREADY_EXISTS),
    RLM_SYNC_ERR_SESSION_SERVER_PERMISSIONS_CHANGED("ServerPermissionsChanged", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_SERVER_PERMISSIONS_CHANGED),
    RLM_SYNC_ERR_SESSION_INITIAL_SYNC_NOT_COMPLETED("InitialSyncNotCompleted", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_INITIAL_SYNC_NOT_COMPLETED),
    RLM_SYNC_ERR_SESSION_WRITE_NOT_ALLOWED("WriteNotAllowed", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_WRITE_NOT_ALLOWED),
    RLM_SYNC_ERR_SESSION_COMPENSATING_WRITE("CompensatingWrite", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_COMPENSATING_WRITE),
    RLM_SYNC_ERR_SESSION_MIGRATE_TO_FLX("MigrateToFlexibleSync", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_MIGRATE_TO_FLX),
    RLM_SYNC_ERR_SESSION_BAD_PROGRESS("BadProgress", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_PROGRESS),
    RLM_SYNC_ERR_SESSION_REVERT_TO_PBS("RevertToPartitionBasedSync", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_REVERT_TO_PBS),
    RLM_SYNC_ERR_SESSION_BAD_SCHEMA_VERSION("BadSchemaVersion", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_BAD_SCHEMA_VERSION),
    RLM_SYNC_ERR_SESSION_SCHEMA_VERSION_CHANGED("SchemaVersionChanged", realm_sync_errno_session_e.RLM_SYNC_ERR_SESSION_SCHEMA_VERSION_CHANGED);

    companion object {
        internal fun of(nativeValue: Int): SyncSessionErrorCode? =
            entries.firstOrNull { value ->
                value.nativeValue == nativeValue
            }
    }
}

actual enum class WebsocketErrorCode(
    actual override val description: String?,
    actual override val nativeValue: Int
) : CodeDescription {
    RLM_ERR_WEBSOCKET_OK("Ok", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_OK),
    RLM_ERR_WEBSOCKET_GOINGAWAY("GoingAway", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_GOINGAWAY),
    RLM_ERR_WEBSOCKET_PROTOCOLERROR("ProtocolError", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_PROTOCOLERROR),
    RLM_ERR_WEBSOCKET_UNSUPPORTEDDATA("UnsupportedData", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_UNSUPPORTEDDATA),
    RLM_ERR_WEBSOCKET_RESERVED("Reserved", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_RESERVED),
    RLM_ERR_WEBSOCKET_NOSTATUSRECEIVED("NoStatusReceived", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_NOSTATUSRECEIVED),
    RLM_ERR_WEBSOCKET_ABNORMALCLOSURE("AbnormalClosure", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_ABNORMALCLOSURE),
    RLM_ERR_WEBSOCKET_INVALIDPAYLOADDATA("InvalidPayloadData", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_INVALIDPAYLOADDATA),
    RLM_ERR_WEBSOCKET_POLICYVIOLATION("PolicyViolation", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_POLICYVIOLATION),
    RLM_ERR_WEBSOCKET_MESSAGETOOBIG("MessageToBig", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_MESSAGETOOBIG),
    RLM_ERR_WEBSOCKET_INAVALIDEXTENSION("InvalidExtension", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_INAVALIDEXTENSION),
    RLM_ERR_WEBSOCKET_INTERNALSERVERERROR("InternalServerError", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_INTERNALSERVERERROR),
    RLM_ERR_WEBSOCKET_TLSHANDSHAKEFAILED("TlsHandshakeFailed", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_TLSHANDSHAKEFAILED),

    RLM_ERR_WEBSOCKET_UNAUTHORIZED("Unauthorized", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_UNAUTHORIZED),
    RLM_ERR_WEBSOCKET_FORBIDDEN("Forbidden", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_FORBIDDEN),
    RLM_ERR_WEBSOCKET_MOVEDPERMANENTLY("MovedPermanently", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_MOVEDPERMANENTLY),

    RLM_ERR_WEBSOCKET_RESOLVE_FAILED("ResolveFailed", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_RESOLVE_FAILED),
    RLM_ERR_WEBSOCKET_CONNECTION_FAILED("ConnectionFailed", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_CONNECTION_FAILED),
    RLM_ERR_WEBSOCKET_READ_ERROR("ReadError", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_READ_ERROR),
    RLM_ERR_WEBSOCKET_WRITE_ERROR("WriteError", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_WRITE_ERROR),
    RLM_ERR_WEBSOCKET_RETRY_ERROR("RetryError", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_RETRY_ERROR),
    RLM_ERR_WEBSOCKET_FATAL_ERROR("FatalError", realm_web_socket_errno_e.RLM_ERR_WEBSOCKET_FATAL_ERROR);

    companion object {
        fun of(nativeValue: Int): WebsocketErrorCode? =
            entries.firstOrNull { value ->
                value.nativeValue == nativeValue
            }
    }
}

actual enum class WebsocketCallbackResult(actual override val description: String?, actual override val nativeValue: Int) : CodeDescription {

    RLM_ERR_SYNC_SOCKET_SUCCESS(
        "Websocket callback success",
        realm_sync_socket_callback_result_e.RLM_ERR_SYNC_SOCKET_SUCCESS
    ),
    RLM_ERR_SYNC_SOCKET_OPERATION_ABORTED(
        "Websocket callback aborted",
        realm_sync_socket_callback_result_e.RLM_ERR_SYNC_SOCKET_OPERATION_ABORTED
    ),
    RLM_ERR_SYNC_SOCKET_RUNTIME(
        "Websocket Runtime error",
        realm_sync_socket_callback_result_e.RLM_ERR_SYNC_SOCKET_RUNTIME
    ),
    RLM_ERR_SYNC_SOCKET_OUT_OF_MEMORY(
        "Websocket out of memory ",
        realm_sync_socket_callback_result_e.RLM_ERR_SYNC_SOCKET_OUT_OF_MEMORY
    ),
    RLM_ERR_SYNC_SOCKET_ADDRESS_SPACE_EXHAUSTED(
        "Websocket address space exhausted",
        realm_sync_socket_callback_result_e.RLM_ERR_SYNC_SOCKET_ADDRESS_SPACE_EXHAUSTED
    ),
    RLM_ERR_SYNC_SOCKET_CONNECTION_CLOSED(
        "Websocket connection closed",
        realm_sync_socket_callback_result_e.RLM_ERR_SYNC_SOCKET_CONNECTION_CLOSED
    ),
    RLM_ERR_SYNC_SOCKET_NOT_SUPPORTED(
        "Websocket not supported",
        realm_sync_socket_callback_result_e.RLM_ERR_SYNC_SOCKET_NOT_SUPPORTED
    ),
    RLM_ERR_SYNC_SOCKET_INVALID_ARGUMENT(
        "Websocket invalid argument",
        realm_sync_socket_callback_result_e.RLM_ERR_SYNC_SOCKET_INVALID_ARGUMENT
    );

    companion object {
        fun of(nativeValue: Int): WebsocketCallbackResult? =
            entries.firstOrNull { value ->
                value.nativeValue == nativeValue
            }
    }
}
