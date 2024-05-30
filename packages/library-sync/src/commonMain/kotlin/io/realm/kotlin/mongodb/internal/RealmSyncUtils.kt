package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.internal.interop.AppCallback
import io.realm.kotlin.internal.interop.CoreError
import io.realm.kotlin.internal.interop.ErrorCategory
import io.realm.kotlin.internal.interop.ErrorCode
import io.realm.kotlin.internal.interop.sync.AppError
import io.realm.kotlin.internal.interop.sync.SyncError
import io.realm.kotlin.mongodb.exceptions.AppException
import io.realm.kotlin.mongodb.exceptions.AuthException
import io.realm.kotlin.mongodb.exceptions.BadFlexibleSyncQueryException
import io.realm.kotlin.mongodb.exceptions.BadRequestException
import io.realm.kotlin.mongodb.exceptions.CompensatingWriteException
import io.realm.kotlin.mongodb.exceptions.ConnectionException
import io.realm.kotlin.mongodb.exceptions.CredentialsCannotBeLinkedException
import io.realm.kotlin.mongodb.exceptions.FunctionExecutionException
import io.realm.kotlin.mongodb.exceptions.InvalidCredentialsException
import io.realm.kotlin.mongodb.exceptions.ServiceException
import io.realm.kotlin.mongodb.exceptions.SyncException
import io.realm.kotlin.mongodb.exceptions.UnrecoverableSyncException
import io.realm.kotlin.mongodb.exceptions.UserAlreadyConfirmedException
import io.realm.kotlin.mongodb.exceptions.UserAlreadyExistsException
import io.realm.kotlin.mongodb.exceptions.UserNotFoundException
import io.realm.kotlin.mongodb.exceptions.WrongSyncTypeException
import io.realm.kotlin.serializers.MutableRealmIntKSerializer
import io.realm.kotlin.serializers.RealmAnyKSerializer
import io.realm.kotlin.serializers.RealmInstantKSerializer
import io.realm.kotlin.serializers.RealmUUIDKSerializer
import io.realm.kotlin.types.MutableRealmInt
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmUUID
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

@PublishedApi
internal fun <T, R> channelResultCallback(
    channel: Channel<Result<R>>,
    success: (T) -> R
): AppCallback<T> {
    return object : AppCallback<T> {
        override fun onSuccess(result: T) {
            try {
                val sendResult: ChannelResult<Unit> =
                    channel.trySend(Result.success(success.invoke(result)))
                if (!sendResult.isSuccess) {
                    throw sendResult.exceptionOrNull()!!
                }
            } catch (ex: Throwable) {
                channel.trySend(Result.failure(ex)).let {
                    if (!it.isSuccess) {
                        throw it.exceptionOrNull()!!
                    }
                }
            }
        }

        override fun onError(error: AppError) {
            try {
                val sendResult = channel.trySend(Result.failure(convertAppError(error)))
                if (!sendResult.isSuccess) {
                    throw sendResult.exceptionOrNull()!!
                }
            } catch (ex: Throwable) {
                channel.trySend(Result.failure(ex)).let {
                    if (!it.isSuccess) {
                        throw it.exceptionOrNull()!!
                    }
                }
            }
        }
    }
}

internal fun convertSyncError(syncError: SyncError): SyncException {
    val errorCode = syncError.errorCode
    val message = createMessageFromSyncError(errorCode)
    return if (syncError.isFatal) {
        // An unrecoverable exception happened
        UnrecoverableSyncException(message)
    } else {
        when (errorCode.errorCode) {
            ErrorCode.RLM_ERR_WRONG_SYNC_TYPE -> WrongSyncTypeException(message)

            ErrorCode.RLM_ERR_INVALID_SUBSCRIPTION_QUERY -> {
                // Flexible Sync Query was rejected by the server
                BadFlexibleSyncQueryException(message)
            }

            ErrorCode.RLM_ERR_SYNC_COMPENSATING_WRITE -> CompensatingWriteException(
                message,
                syncError.compensatingWrites
            )
            ErrorCode.RLM_ERR_SYNC_PROTOCOL_INVARIANT_FAILED,
            ErrorCode.RLM_ERR_SYNC_PROTOCOL_NEGOTIATION_FAILED,
            ErrorCode.RLM_ERR_SYNC_PERMISSION_DENIED -> {
                // Permission denied errors should be unrecoverable according to Core, i.e. the
                // client will disconnect sync and transition to the "inactive" state
                UnrecoverableSyncException(message)
            }
            else -> {
                // An error happened we are not sure how to handle. Just report as a generic
                // SyncException.
                SyncException(message)
            }
        }
    }
}

@Suppress("ComplexMethod", "MagicNumber", "LongMethod")
internal fun convertAppError(appError: AppError): Throwable {
    val msg = createMessageFromAppError(appError)
    return when {
        ErrorCategory.RLM_ERR_CAT_CUSTOM_ERROR in appError -> {
            // Custom errors are only being thrown when executing the network request on the
            // platform side and it failed in a way that didn't produce a HTTP status code.
            ConnectionException(msg)
        }
        ErrorCategory.RLM_ERR_CAT_HTTP_ERROR in appError -> {
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
        ErrorCategory.RLM_ERR_CAT_JSON_ERROR in appError -> {
            // The JSON response from Atlas could not be parsed as valid JSON. Errors of this kind
            // would indicate a problem on Atlas that should be fixed with no action needed by the
            // client. So retrying the action should generally be safe. Although it might take a
            // while for the server to correct the behavior.
            ConnectionException(msg)
        }
        ErrorCategory.RLM_ERR_CAT_CLIENT_ERROR in appError -> {
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
                ErrorCode.RLM_ERR_CLIENT_USER_NOT_FOUND -> {
                    IllegalStateException(msg)
                }
                ErrorCode.RLM_ERR_CLIENT_USER_ALREADY_NAMED -> {
                    CredentialsCannotBeLinkedException(msg)
                }
                else -> {
                    AppException(msg)
                }
            }
        }
        ErrorCategory.RLM_ERR_CAT_SERVICE_ERROR in appError -> {
            // This category is response codes from the server, that for some reason didn't
            // accept a request from the client. Most of the error codes in this category
            // can (most likely) be fixed by the client and should have a more granular
            // exception type, but until we understand the details, they will be reported as
            // generic `ServiceException`'s.
            when (appError.code) {
                ErrorCode.RLM_ERR_INTERNAL_SERVER_ERROR -> {
                    if (msg.contains("linking an anonymous identity is not allowed") || // Trying to link an anonymous account to a named one.
                        msg.contains("linking a local-userpass identity is not allowed") // Trying to link two email logins with each other
                    ) {
                        CredentialsCannotBeLinkedException(msg)
                    } else {
                        ServiceException(msg)
                    }
                }
                ErrorCode.RLM_ERR_INVALID_SESSION -> {
                    if (msg.contains("a user already exists with the specified provider")) {
                        CredentialsCannotBeLinkedException(msg)
                    } else {
                        ServiceException(msg)
                    }
                }
                ErrorCode.RLM_ERR_USER_DISABLED,
                ErrorCode.RLM_ERR_AUTH_ERROR -> {
                    // Some auth providers return a generic AuthError when
                    // invalid credentials are presented. We make a best effort
                    // to map these to a more sensible `InvalidCredentialsExceptions`
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
                ErrorCode.RLM_ERR_USER_NOT_FOUND -> {
                    UserNotFoundException(msg)
                }
                ErrorCode.RLM_ERR_ACCOUNT_NAME_IN_USE -> {
                    UserAlreadyExistsException(msg)
                }
                ErrorCode.RLM_ERR_USER_ALREADY_CONFIRMED -> {
                    UserAlreadyConfirmedException(msg)
                }
                ErrorCode.RLM_ERR_INVALID_PASSWORD -> {
                    InvalidCredentialsException(msg)
                }
                ErrorCode.RLM_ERR_BAD_REQUEST -> {
                    BadRequestException(msg)
                }
                ErrorCode.RLM_ERR_FUNCTION_NOT_FOUND,
                ErrorCode.RLM_ERR_EXECUTION_TIME_LIMIT_EXCEEDED,
                ErrorCode.RLM_ERR_FUNCTION_EXECUTION_ERROR -> {
                    FunctionExecutionException(msg)
                }
                else -> ServiceException(message = msg, errorCode = appError.code)
            }
        }
        else -> AppException(msg)
    }
}

internal fun createMessageFromSyncError(error: CoreError): String {
    val categoryDesc = error.categories.description
    val errorCodeDesc: String? = error.errorCode?.description ?: if (ErrorCategory.RLM_ERR_CAT_SYSTEM_ERROR in error.categories) {
        // We lack information about these kinds of errors,
        // so rather than returning a potentially misleading
        // name, just return nothing.
        null
    } else {
        "Unknown"
    }

    // Combine all the parts to form an error format that is human-readable.
    // An example could be this: `[Connection][WrongProtocolVersion(104)] Wrong protocol version was used: 25`
    val errorDesc: String =
        if (errorCodeDesc == null) error.errorCodeNativeValue.toString() else "$errorCodeDesc(${error.errorCodeNativeValue})"

    // Make sure that messages are uniformly formatted, so it looks nice if we append the
    // server log.
    val msg = error.message?.let { message: String ->
        " $message${if (!message.endsWith(".")) "." else ""}"
    } ?: ""

    return "[$categoryDesc][$errorDesc]$msg"
}

@Suppress("ComplexMethod", "MagicNumber", "LongMethod")
private fun createMessageFromAppError(error: AppError): String {
    // If the category is "Http", errorCode and httpStatusCode is the same.
    // if the category is "Custom", httpStatusCode is optional (i.e != 0), but
    // the Kotlin SDK always sets it to 0 in this case.
    // For all other categories, httpStatusCode is 0 (i.e not used).
    // linkToServerLog is only present if the category is "Service".
    val categoryDesc: String? = when {
        ErrorCategory.RLM_ERR_CAT_CLIENT_ERROR in error -> ErrorCategory.RLM_ERR_CAT_CLIENT_ERROR
        ErrorCategory.RLM_ERR_CAT_JSON_ERROR in error -> ErrorCategory.RLM_ERR_CAT_JSON_ERROR
        ErrorCategory.RLM_ERR_CAT_SERVICE_ERROR in error -> ErrorCategory.RLM_ERR_CAT_SERVICE_ERROR
        ErrorCategory.RLM_ERR_CAT_HTTP_ERROR in error -> ErrorCategory.RLM_ERR_CAT_HTTP_ERROR
        ErrorCategory.RLM_ERR_CAT_CUSTOM_ERROR in error -> ErrorCategory.RLM_ERR_CAT_CUSTOM_ERROR
        else -> null
    }?.description ?: error.categoryFlags.toString()

    val errorCodeDesc = error.code.description ?: when {
        ErrorCategory.RLM_ERR_CAT_HTTP_ERROR in error -> {
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
        ErrorCategory.RLM_ERR_CAT_CUSTOM_ERROR in error -> {
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

@Suppress("UNCHECKED_CAST")
@PublishedApi
internal inline fun <reified T> SerializersModule.serializerOrRealmBuiltInSerializer(): KSerializer<T> =
    when (T::class) {
        /**
         * Automatically resolves any Realm datatype serializer or defaults to the type built in.
         *
         * ReamLists, Sets and others cannot be resolved here as we don't have the type information
         * required to instantiate them. They require to be instantiated by the user.
         */
        MutableRealmInt::class -> MutableRealmIntKSerializer
        RealmUUID::class -> RealmUUIDKSerializer
        RealmInstant::class -> RealmInstantKSerializer
        RealmAny::class -> RealmAnyKSerializer
        else -> serializer<T>()
    } as KSerializer<T>
