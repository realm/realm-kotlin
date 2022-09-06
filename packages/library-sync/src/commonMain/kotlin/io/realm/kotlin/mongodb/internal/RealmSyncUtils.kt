package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.internal.interop.AppCallback
import io.realm.kotlin.internal.interop.sync.AppError
import io.realm.kotlin.internal.interop.sync.AppErrorCategory
import io.realm.kotlin.internal.interop.sync.ClientErrorCode
import io.realm.kotlin.internal.interop.sync.ProtocolConnectionErrorCode
import io.realm.kotlin.internal.interop.sync.ProtocolSessionErrorCode
import io.realm.kotlin.internal.interop.sync.ServiceErrorCode
import io.realm.kotlin.internal.interop.sync.SyncError
import io.realm.kotlin.internal.interop.sync.SyncErrorCode
import io.realm.kotlin.internal.interop.sync.SyncErrorCodeCategory
import io.realm.kotlin.mongodb.exceptions.AppException
import io.realm.kotlin.mongodb.exceptions.AuthException
import io.realm.kotlin.mongodb.exceptions.BadFlexibleSyncQueryException
import io.realm.kotlin.mongodb.exceptions.BadRequestException
import io.realm.kotlin.mongodb.exceptions.ConnectionException
import io.realm.kotlin.mongodb.exceptions.InvalidCredentialsException
import io.realm.kotlin.mongodb.exceptions.ServiceException
import io.realm.kotlin.mongodb.exceptions.SyncException
import io.realm.kotlin.mongodb.exceptions.UnrecoverableSyncException
import io.realm.kotlin.mongodb.exceptions.UserAlreadyConfirmedException
import io.realm.kotlin.mongodb.exceptions.UserAlreadyExistsException
import io.realm.kotlin.mongodb.exceptions.UserNotFoundException
import io.realm.kotlin.mongodb.exceptions.WrongSyncTypeException
import kotlinx.coroutines.channels.Channel

internal fun <T, R> channelResultCallback(
    channel: Channel<Result<R>>,
    success: (T) -> R
): AppCallback<T> {
    return object : AppCallback<T> {
        override fun onSuccess(result: T) {
            channel.trySend(Result.success(success.invoke(result)))
        }

        override fun onError(error: AppError) {
            channel.trySend(Result.failure(convertAppError(error)))
        }
    }
}

internal fun convertSyncError(error: SyncError): SyncException {
    // TODO In a normal environment we only expose the information in the SyncErrorCode.
    //  In debug mode we could consider using `detailedMessage` instead.
    return convertSyncErrorCode(error.errorCode)
}

internal fun convertSyncErrorCode(syncError: SyncErrorCode): SyncException {
    // FIXME Client Reset errors are just reported as normal Sync Errors for now.
    //  Will be fixed by https://github.com/realm/realm-kotlin/issues/417
    val message = createMessageFromSyncError(syncError)
    return when (syncError.category) {
        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CLIENT -> {
            // See https://github.com/realm/realm-core/blob/master/src/realm/sync/client_base.hpp#L73
            // For now, it is unclear how to categorize these, so for now, just report as generic
            // errors.
            SyncException(message)
        }
        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_CONNECTION -> {
            // See https://github.com/realm/realm-core/blob/master/src/realm/sync/protocol.hpp#L200
            // Use https://docs.google.com/spreadsheets/d/1SmiRxhFpD1XojqCKC-xAjjV-LKa9azeeWHg-zgr07lE/edit
            // as guide for how to categorize Connection type errors.
            when (syncError.code) {
                ProtocolConnectionErrorCode.RLM_SYNC_ERR_CONNECTION_UNKNOWN_MESSAGE, // Unknown type of input message
                ProtocolConnectionErrorCode.RLM_SYNC_ERR_CONNECTION_BAD_SYNTAX, // Bad syntax in input message head
                ProtocolConnectionErrorCode.RLM_SYNC_ERR_CONNECTION_WRONG_PROTOCOL_VERSION, // Wrong protocol version (CLIENT) (obsolete)
                ProtocolConnectionErrorCode.RLM_SYNC_ERR_CONNECTION_BAD_SESSION_IDENT, // Bad session identifier in input message
                ProtocolConnectionErrorCode.RLM_SYNC_ERR_CONNECTION_REUSE_OF_SESSION_IDENT, // Overlapping reuse of session identifier (BIND)
                ProtocolConnectionErrorCode.RLM_SYNC_ERR_CONNECTION_BOUND_IN_OTHER_SESSION, // Client file bound in other session (IDENT)
                ProtocolConnectionErrorCode.RLM_SYNC_ERR_CONNECTION_BAD_MESSAGE_ORDER, // Bad input message order
                ProtocolConnectionErrorCode.RLM_SYNC_ERR_CONNECTION_BAD_DECOMPRESSION, // Error in decompression (UPLOAD)
                ProtocolConnectionErrorCode.RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_HEADER_SYNTAX, // Bad syntax in a changeset header (UPLOAD)
                ProtocolConnectionErrorCode.RLM_SYNC_ERR_CONNECTION_BAD_CHANGESET_SIZE -> { // Bad size specified in changeset header (UPLOAD)
                    UnrecoverableSyncException(message)
                }
                ProtocolConnectionErrorCode.RLM_SYNC_ERR_CONNECTION_SWITCH_TO_FLX_SYNC, // Connected with wrong wire protocol - should switch to FLX sync
                ProtocolConnectionErrorCode.RLM_SYNC_ERR_CONNECTION_SWITCH_TO_PBS -> { // Connected with wrong wire protocol - should switch to PBS
                    WrongSyncTypeException(message)
                }
                else -> SyncException(message)
            }
        }
        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_SESSION -> {
            // See https://github.com/realm/realm-core/blob/master/src/realm/sync/protocol.hpp#L217
            // Use https://docs.google.com/spreadsheets/d/1SmiRxhFpD1XojqCKC-xAjjV-LKa9azeeWHg-zgr07lE/edit
            // as guide for how to categorize Session type errors.
            when (syncError.code) {
                ProtocolSessionErrorCode.RLM_SYNC_ERR_SESSION_BAD_QUERY -> { // Flexible Sync Query was rejected by the server
                    BadFlexibleSyncQueryException(message)
                }
                ProtocolSessionErrorCode.RLM_SYNC_ERR_SESSION_PERMISSION_DENIED ->
                    // Permission denied errors should be unrecoverable according to Core, i.e. the
                    // client will disconnect sync and transition to the "inactive" state
                    UnrecoverableSyncException(message)
                else -> SyncException(message)
            }
        }
        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_SYSTEM,
        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_UNKNOWN -> {
            // It is unclear how to handle system level errors, so even though some of them
            // are probably benign, report as top-level errors for now.
            SyncException(message)
        }
        else -> {
            SyncException(message)
        }
    }
}

@Suppress("ComplexMethod", "MagicNumber", "LongMethod")
internal fun convertAppError(appError: AppError): Throwable {
    val msg = createMessageFromAppError(appError)
    return when (appError.category) {
        AppErrorCategory.RLM_APP_ERROR_CATEGORY_CUSTOM -> {
            // Custom errors are only being thrown when executing the network request on the
            // platform side and it failed in a way that didn't produce a HTTP status code.
            ConnectionException(msg)
        }
        AppErrorCategory.RLM_APP_ERROR_CATEGORY_HTTP -> {
            // HTTP errors from network requests towards Atlas. Generally we should see
            // errors in these ranges:
            // 300-399: Redirect Codes. Indicate either a misconfiguration in a users network
            // environement or on Atlas itself. Retrying should be acceptable.
            // 400-499: Client error codes. These point to different error scenarios on the
            // client and each should be considered individually.
            // 500-599: Server error codes. We assume all of these are intermiddent and retrying
            // should be safe.
            val statusCode: Int = appError.code.nativeValue
            when (statusCode) {
                in 300..399 -> ConnectionException(msg)
                401 -> InvalidCredentialsException(msg) // Unauthorized
                408, // Request Timeout
                429, // Too Many Requests
                in 500..599 -> ConnectionException(msg)
                else -> ServiceException(msg)
            }
        }
        AppErrorCategory.RLM_APP_ERROR_CATEGORY_JSON -> {
            // The JSON response from Atlas could not be parsed as valid JSON. Errors of this kind
            // would indicate a problem on Atlas that should be fixed with no action needed by the
            // client. So retrying the action should generally be safe. Although it might take a
            // while for the server to correct the behavior.
            ConnectionException(msg)
        }
        AppErrorCategory.RLM_APP_ERROR_CATEGORY_CLIENT -> {
            // See https://github.com/realm/realm-core/blob/master/src/realm/object-store/sync/generic_network_transport.hpp#L34
            //
            // `ClientErrorCode::user_not_logged in` is used when the client decides that a login
            // is no longer valid, this normally happens if the refresh_token has expired. The
            // user needs to log in again in that case.
            //
            // `ClientErrorCode::user_not_found` is mostly used as a proxy for an illegal argument,
            // but since most of our API methods that throws this is on the `User` object itself,
            // it is being converted to an `IllegalStateException` here. It is also used internally
            // when refreshing the access token, but since this error never reaches the end user,
            // we just ignore this case.
            //
            // `ClientErrorCode::app_deallocated` should never happen, so is just returned as an
            // AppException.
            when (appError.code) {
                ClientErrorCode.RLM_APP_ERR_CLIENT_USER_NOT_FOUND -> {
                    IllegalStateException(msg)
                }
                ClientErrorCode.RLM_APP_ERR_CLIENT_USER_NOT_LOGGED_IN -> {
                    InvalidCredentialsException(msg)
                }
                ClientErrorCode.RLM_APP_ERR_CLIENT_APP_DEALLOCATED -> {
                    AppException(msg)
                }
                else -> {
                    AppException(msg)
                }
            }
        }
        AppErrorCategory.RLM_APP_ERROR_CATEGORY_SERVICE -> {
            // This category is response codes from the server, that for some reason didn't
            // accept a request from the client. Most of the error codes in this category
            // can (most likely) be fixed by the client and should have a more granular
            // exception type, but until we understand the details, they will be reported as
            // generic `ServiceException`'s.
            when (appError.code) {
                ServiceErrorCode.RLM_APP_ERR_SERVICE_USER_DISABLED,
                ServiceErrorCode.RLM_APP_ERR_SERVICE_AUTH_ERROR -> {
                    // Some auth providers return a generic AuthError when
                    // invalid credentials are presented. We make a best effort
                    // to map these to a more sensible `InvalidCredentialsExceptions`
                    //
                    if (msg.contains("invalid API key")) {
                        // API Key
                        // See https://github.com/10gen/baas/blob/master/authprovider/providers/apikey/provider.go
                        InvalidCredentialsException(msg)
                    } else if (msg.contains("invalid custom auth token:")) {
                        // Custom JWT
                        // See https://github.com/10gen/baas/blob/master/authprovider/providers/custom/provider.go
                        InvalidCredentialsException(msg)
                    } else {
                        // It does not look possible to reliably detect Facebook, Google and Apple
                        // invalid tokens: https://github.com/10gen/baas/blob/master/authprovider/providers/oauth2/oauth.go#L139
                        AuthException(msg)
                    }
                }
                ServiceErrorCode.RLM_APP_ERR_SERVICE_USER_NOT_FOUND -> {
                    UserNotFoundException(msg)
                }
                ServiceErrorCode.RLM_APP_ERR_SERVICE_ACCOUNT_NAME_IN_USE -> {
                    UserAlreadyExistsException(msg)
                }
                ServiceErrorCode.RLM_APP_ERR_SERVICE_USER_ALREADY_CONFIRMED -> {
                    UserAlreadyConfirmedException(msg)
                }
                ServiceErrorCode.RLM_APP_ERR_SERVICE_INVALID_EMAIL_PASSWORD -> {
                    InvalidCredentialsException(msg)
                }
                ServiceErrorCode.RLM_APP_ERR_SERVICE_BAD_REQUEST -> {
                    BadRequestException(msg)
                }
                else -> ServiceException(msg)
            }
        }
        else -> AppException(msg)
    }
}

internal fun createMessageFromSyncError(error: SyncErrorCode): String {
    val categoryDesc = error.category.description ?: error.category.nativeValue.toString()
    val errorCodeDesc: String? = error.code.description ?: when (error.category) {
        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_SYSTEM,
        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_UNKNOWN,
        -> {
            // We lack information about these kinds of errors,
            // so rather than returning a potentially misleading
            // name, just return nothing.
            null
        }
        else -> "Unknown"
    }

    // Combine all the parts to form an error format that is human-readable.
    // An example could be this: `[Connection][WrongProtocolVersion(104)] Wrong protocol version was used: 25`
    val errorDesc: String =
        if (errorCodeDesc == null) error.code.nativeValue.toString() else "$errorCodeDesc(${error.code.nativeValue})"

    // Make sure that messages are uniformly formatted, so it looks nice if we append the
    // server log.
    val msg = error.message.let { message: String ->
        " $message${if (!message.endsWith(".")) "." else ""}"
    }

    return "[$categoryDesc][$errorDesc]$msg"
}

@Suppress("ComplexMethod", "MagicNumber", "LongMethod")
private fun createMessageFromAppError(error: AppError): String {
    // If the category is "Http", errorCode and httpStatusCode is the same.
    // if the category is "Custom", httpStatusCode is optional (i.e != 0), but
    // the Kotlin SDK always sets it to 0 in this case.
    // For all other categories, httpStatusCode is 0 (i.e not used).
    // linkToServerLog is only present if the category is "Service".

    val categoryDesc = error.category.description ?: error.category.nativeValue.toString()
    val errorCodeDesc = error.code.description ?: when (error.category) {
        AppErrorCategory.RLM_APP_ERROR_CATEGORY_HTTP -> {
            // Source https://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml
            // Only codes in the 300-599 range is mapped to errors
            when (error.code.nativeValue) {
                300 -> "MultipleChoices"
                301 -> "MovedPermanently"
                302 -> "Found"
                303 -> "SeeOther"
                304 -> "NotModified"
                305 -> "UseProxy"
                307 -> "TemporaryRedirect"
                308 -> "PermanentRedirect"
                400 -> "BadRequest"
                401 -> "Unauthorized"
                402 -> "PaymentRequired"
                403 -> "Forbidden"
                404 -> "NotFound"
                405 -> "MethodNotAllowed"
                406 -> "NotAcceptable"
                407 -> "ProxyAuthenticationRequired"
                408 -> "RequestTimeout"
                409 -> "Conflict"
                410 -> "Gone"
                411 -> "LengthRequired"
                412 -> "PreconditionFailed"
                413 -> "ContentTooLarge"
                414 -> "UriTooLong"
                415 -> "UnsupportedMediaType"
                416 -> "RangeNotSatisfiable"
                417 -> "ExpectationFailed"
                421 -> "MisdirectedRequest"
                422 -> "UnprocessableContent"
                423 -> "Locked"
                424 -> "FailedDependency"
                425 -> "TooEarly"
                426 -> "UpgradeRequired"
                428 -> "PreconditionRequired"
                429 -> "TooManyRequests"
                431 -> "RequestHeaderFieldsTooLarge"
                451 -> "UnavailableForLegalReasons"
                500 -> "InternalServerError"
                501 -> "NotImplemented"
                502 -> "BadGateway"
                503 -> "ServiceUnavailable"
                504 -> "GatewayTimeout"
                505 -> "HttpVersionNotSupported"
                506 -> "VariantAlsoNegotiates"
                507 -> "InsufficientStorage"
                508 -> "LoopDetected"
                510 -> "NotExtended"
                511 -> "NetworkAuthenticationRequired"
                else -> "Unknown"
            }
        }
        AppErrorCategory.RLM_APP_ERROR_CATEGORY_CUSTOM -> {
            when (error.code.nativeValue) {
                KtorNetworkTransport.ERROR_IO -> "IO"
                KtorNetworkTransport.ERROR_INTERRUPTED -> "Interrupted"
                else -> "Unknown"
            }
        }
        else -> "Unknown"
    }

    // Make sure that messages are uniformly formatted, so it looks nice if we append the
    // server log.
    val msg = error.message?.let { message: String ->
        if (message.endsWith(".")) {
            message
        } else {
            " $message."
        }
    } ?: ""

    // Combine all the parts to form an error format that is human-readable.
    // An example could be this: `[Service][UserNotFound(44)] No matching user was found. Server logs: http://link.to.logs`
    val serverLogsLink = error.linkToServerLog?.let { link: String ->
        " Server log entry: $link"
    } ?: ""

    val errorDesc = "$errorCodeDesc(${error.code.nativeValue})"
    return "[$categoryDesc][$errorDesc]$msg$serverLogsLink"
}
