package io.realm.mongodb.internal

import io.realm.internal.interop.AppCallback
import io.realm.internal.interop.sync.AppError
import io.realm.internal.interop.sync.AppErrorCategory
import io.realm.internal.interop.sync.ClientErrorCode
import io.realm.internal.interop.sync.JsonErrorCode
import io.realm.internal.interop.sync.ProtocolConnectionErrorCode
import io.realm.internal.interop.sync.ServiceErrorCode
import io.realm.internal.interop.sync.SyncError
import io.realm.internal.interop.sync.SyncErrorCode
import io.realm.internal.interop.sync.SyncErrorCodeCategory
import io.realm.mongodb.exceptions.AppException
import io.realm.mongodb.exceptions.AuthException
import io.realm.mongodb.exceptions.BadRequestException
import io.realm.mongodb.exceptions.ConnectionException
import io.realm.mongodb.exceptions.InvalidCredentialsException
import io.realm.mongodb.exceptions.ServiceException
import io.realm.mongodb.exceptions.SyncException
import io.realm.mongodb.exceptions.UnrecoverableSyncException
import io.realm.mongodb.exceptions.UserAlreadyConfirmedException
import io.realm.mongodb.exceptions.UserAlreadyExistsException
import io.realm.mongodb.exceptions.UserNotFoundException
import io.realm.mongodb.exceptions.WrongSyncTypeException
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
    //  In debug mode we could consider to use the `detailedMessage` instead.
    return convertSyncErrorCode(error.errorCode, error.detailedMessage)
}

internal fun convertSyncErrorCode(error: SyncErrorCode, overrideMessage: String? = null): SyncException {
    // TODO All errors resulting in Client Resets have already been routed through a different
    //  error handler by Core. Is this true?
    val category = error.category.name.removePrefix("RLM_SYNC_ERROR_CATEGORY_")
    val code = error.value
    val msg = overrideMessage ?: error.message
    val message = "[$category][$code] $msg"

    return when (error.category) {
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
            // For now report them
            val err = ProtocolConnectionErrorCode.fromInt(code)
            when (err) {
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
            // All Client Reset errors should never pass through this function anyway, and it is
            // unclear how to categorize the rest, so for now, just report as generic exceptions.
            // TODO Is this true?
            SyncException(message)
        }
        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_SYSTEM,
        SyncErrorCodeCategory.RLM_SYNC_ERROR_CATEGORY_UNKNOWN -> {
            // It is unclear how to handle system level errors, so even though some of them
            // are probably benign, report as top-level errors
            SyncException(message)
        }
        else -> {
            SyncException(message)
        }
    }
}

@Suppress("ComplexMethod", "MagicNumber", "LongMethod")
internal fun convertAppError(error: AppError): Throwable {
    val msg = createMessageFromAppError(error)
    return when (error.category) {
        AppErrorCategory.RLM_APP_ERROR_CATEGORY_CUSTOM -> {
            // Custom errors are only being thrown when executing the network request on the
            // platform side and it failed in a way that doesn't produce an HTTP status code.
            ConnectionException(msg)
        }
        AppErrorCategory.RLM_APP_ERROR_CATEGORY_HTTP -> {
            // Http errors from App network requests towards Atlas. Generally we should see
            // errors in these ranges:
            // 300-399: Redirect Codes. Indicate either a misconfiguration in a users network
            // environement or on Atlas itself. Retrying should be acceptable.
            // 400-499: Client error codes. These point to different error scenarios on the
            // client and each should be considered individually.
            // 500-599: Server error codes. We assume all of these are intermiddent and retrying
            // should be safe.
            val statusCode: Int = error.errorCode
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
            // `ClientErrorCode::user_not_found` is mostly used as a proxy for IllegalArgument,
            // but is also used internally when refreshing the access token, but since this error
            // never reaches the end user, we just map to IllegalArgumentException directly.
            //
            // `ClientErrorCode::app_deallocated` should never happen, so is just returned as an
            // AppException.
            val err = ClientErrorCode.fromInt(error.errorCode)
            when (err) {
                ClientErrorCode.RLM_APP_ERR_CLIENT_USER_NOT_FOUND -> {
                    IllegalArgumentException(msg)
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
            // accept a request from the client. Most of the error codes found in this category
            // can (most likely) be fixed by the client and should have a more granular
            // exception type, but until we understand the details, they will be reported as
            // generic `ServiceException`'s.
            val err = ServiceErrorCode.fromInt(error.errorCode)
            when (err) {
                ServiceErrorCode.RLM_APP_ERR_SERVICE_USER_DISABLED,
                ServiceErrorCode.RLM_APP_ERR_SERVICE_AUTH_ERROR -> {
                    // Some auth providers just return a generic Auth Error when
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
                        io.realm.mongodb.exceptions.InvalidCredentialsException(msg)
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
        else -> throw IllegalStateException("Unknown category: ${error.category}")
    }
}

@Suppress("ComplexMethod", "MagicNumber", "LongMethod")
private fun createMessageFromAppError(error: AppError): String {
    // If the category is HTTP, errorCode and httpStatusCode is the same.
    // if the category is CUSTOM, httpStatusCode is optional (i.e != 0), but
    // the Kotlin SDK always sets it to 0 in this case.
    // For all other categories, httpStatusCode is 0 (i.e not used).
    // linkToServerLog is only present if the category is SERVICE.
    val categoryDesc = error.category.description

    // Attempt to find a user friendly name for the error code.
    val errorCodeDesc: String = when (error.category) {
        AppErrorCategory.RLM_APP_ERROR_CATEGORY_CUSTOM -> {
            when (error.errorCode) {
                KtorNetworkTransport.ERROR_IO -> "IO"
                KtorNetworkTransport.ERROR_INTERRUPTED -> "Interrupted"
                else -> "Unknown"
            }
        }
        AppErrorCategory.RLM_APP_ERROR_CATEGORY_HTTP -> {
            // Source https://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml
            // Only codes in the 300-599 range is mapped to errors
            when (error.errorCode) {
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
        AppErrorCategory.RLM_APP_ERROR_CATEGORY_JSON -> {
            JsonErrorCode.fromInt(error.errorCode).description
        }
        AppErrorCategory.RLM_APP_ERROR_CATEGORY_CLIENT -> {
            ClientErrorCode.fromInt(error.errorCode).description
        }
        AppErrorCategory.RLM_APP_ERROR_CATEGORY_SERVICE -> {
            ServiceErrorCode.fromInt(error.errorCode).description
        }
        else -> "Unknown"
    }

    // Make sure that messages are uniformly formatted, so it looks nice if we append the
    // server log.
    val msg = if (error.message == null) {
        ""
    } else {
        error.message?.let {
            if (it.endsWith(".")) {
                it
            } else {
                "$it."
            }
        }
    }

    // Combine all the parts to form an error format that is human-readable.
    // An example could be this: `[Service][UserNotFound(44)] No matching user was found. Server logs: http://link.to.logs`
    val serverLogsLink = if (error.linkToServerLog == null) "" else " Server log entry: ${error.linkToServerLog}"
    val errorDesc = "$errorCodeDesc(${error.errorCode})"
    return "[$categoryDesc][$errorDesc] $msg$serverLogsLink"
}
