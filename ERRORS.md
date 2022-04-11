# Better App Exceptions

This document describe how we can improve error reporting for Sync enabled Apps. We do not attempt to fix error handling in general, but the scheme we choose should ideally be compatible with how error are being reported from e.g. Realm Core.


## History

In Realm Java, all App and Sync errors flow through the same two classes: [AppException](https://github.com/realm/realm-java/blob/master/realm/realm-library/src/objectServer/java/io/realm/mongodb/AppException.java) and [ErrorCode](https://github.com/realm/realm-java/blob/master/realm/realm-library/src/objectServer/java/io/realm/mongodb/ErrorCode.java). 

While this gives the advantage that it is easy to single out a specific error scenario to handle, it comes with some other problems:

- Adding a new enum is, in theory, a breaking change. In practice, no-one will probably have enumerated all 200+ errors and will have a default fallthrough somewhere.

- The error codes are not really documented anywhere, so users will have to fingure out which is relevant by going through them manually.


We have had several team discussions about this approach, and the entire team feels that the advantages does not outweigh the disadvantages and thus we need to find another alternative.


## Choosing a new approach

If we want to move away from a global enum, we basically are left with two choices:

1. A sealed class exception hiearchy.
2. A normal open class exception hierachy.


In both of these cases we want the following goals accomplished:

1.  All exceptions should contain the underlying exception category, error number, message and optional URL so we can effectively help users if they report exceptions to us.
2. Errors that have a clear end-user (not developer) action, should have an explicit Exception class that can be documented, caught and handled by app developers.
3. Errors that will be retried automatically should never be reported, but instead only logged. Currently Core does not do this.


Some arguments for and against sealed classes:

- Sealed classes make for greater discoverability BUT breaks backwards compatibility when adding new ones.
- Autocomplete `when` only display the bottom nodes of the inheritence hiarchy. This seems problematic in our case.
- Normal class hierarchy is simple to extend, also in a non-breaking way, but makes discoverability worse.
- For both cases we can easily document the relevant exception, like InvalidLoginException at entry points where it matter.

Example code

```
sealed class AppException: RuntimeException() {}
sealed class ServiceException: AppException() {}
sealed class AuthException: ServiceException() {}
class HttpException: ServiceException() {}
class InvalidLoginException: AuthException() {}
class SyncException: AppException() {}


private fun test() {
    val ex: AppException = getEx()
    when(ex) {
        is InvalidLoginException -> TODO()
        is HttpException -> TODO()
        is SyncException -> TODO()
    }
}
```

## API Proposal

Given that the sealed class approach doesn't seem well-suited to a dynamically growing list of exceptions, I will propose we implement a standard exception hiearchy. An attempt of doing that is outlined below. All the error codes have been posted in and attempted to be put into buckets.


The general idea is to break our current `ErrorCode.Category` into different exception classes, and then further create invidual exceptions for the cases we truely care about. Doing this already surfaced a few problems: Some errors are used across multiple components and putting them in the same "category" might be problematic, i.e. `USER_NOT_FOUND` can be thrown both when confirming a new user and when logging in.
It doesn't seem clear that both of these are Authentication-related. Some ambiguity will probably need to be accepted.

An attempt was made at the offsite to categorize Sync errors into actionable buckets: https://docs.google.com/spreadsheets/d/1SmiRxhFpD1XojqCKC-xAjjV-LKa9azeeWHg-zgr07lE/edit#gid=1272551158



High level classes: `RealmError` and `RealmException`. Root exceptions for all Realm related exceptions that does not fit the standard exception types like `IllegalArgumentException` og `IllegalStateException`.


```kotlin
public open class RealmException : RuntimeException {
    public constructor(message: String): super(message)
    public constructor(message: String, cause: Throwable): super(message, cause)
}

public open class RealmError : Error {
    public constructor(message: String): super(message)
    public constructor(message: String, cause: Throwable): super(message, cause)
}
``` 

For MongoDB API's we have a number of categories:

In the C-API:

```
typedef enum realm_app_error_category {
    RLM_APP_ERROR_CATEGORY_HTTP,
    RLM_APP_ERROR_CATEGORY_JSON,
    RLM_APP_ERROR_CATEGORY_CLIENT, // Does not seem covered in Java currently, local user not found, user not logged in
    RLM_APP_ERROR_CATEGORY_SERVICE, 
    RLM_APP_ERROR_CATEGORY_CUSTOM,
} realm_app_error_category_e;


typedef enum realm_sync_error_category {
    RLM_SYNC_ERROR_CATEGORY_CLIENT,
    RLM_SYNC_ERROR_CATEGORY_CONNECTION,
    RLM_SYNC_ERROR_CATEGORY_SESSION,
    RLM_SYNC_ERROR_CATEGORY_SYSTEM,
    RLM_SYNC_ERROR_CATEGORY_UNKNOWN,
} realm_sync_error_category_e;


// is_fatal flag

/// In general, the connection will be automatically reestablished
/// later. Whether this happens quickly, generally depends on \ref
/// is_fatal. If \ref is_fatal is true, it means that the error is deemed to
/// be of a kind that is likely to persist, and cause all future reconnect
/// attempts to fail. In that case, if another attempt is made at
/// reconnecting, the delay will be substantial (at least an hour).
///
/// \ref error_code specifies the error that caused the connection to be
/// closed. For the list of errors reported by the server, see \ref
/// ProtocolError (or `protocol.md`). For the list of errors corresponding
/// to protocol violations that are detected by the client, see
/// Client::Error. The error may also be a system level error, or an error
/// from one of the potential intermediate protocol layers (SSL or
/// WebSocket).

// AppError in Core

struct AppError {
    std::error_code error_code;
    util::Optional<int> http_status_code;
    std::string message;
    std::string link_to_server_logs;
    AppError(std::error_code error_code, std::string message, std::string link = "",
             util::Optional<int> http_error_code = util::none)
        : error_code(error_code)
        , http_status_code(http_error_code)
        , message(message)
        , link_to_server_logs(link)
    {
    }
}

```

From Java:

```
public static class Type {
    public static final String AUTH = "auth"; // Errors from the Realm Object Server
    public static final String CONNECTION = "realm.basic_system"; // Connection/System errors from the native Sync Client
    public static final String DEPRECATED = "deprecated"; // Deprecated errors
    public static final String HTTP = "realm::app::HttpError"; // Errors from the HTTP layer
    public static final String JAVA = "realm::app::CustomError"; // Errors from the Java layer
    public static final String MISC = "realm.util.misc_ext"; // Misc errors from the native Sync Client
    public static final String SERVICE = "realm::app::ServiceError"; // MongoDB Realm Response errors
    public static final String JSON = "realm::app::JSONError"; // Errors when parsing JSON
    public static final String PROTOCOL = "realm::sync::ProtocolError"; // Protocol level errors from the native Sync Client
    public static final String SESSION = "realm::sync::ClientError"; // Session level errors from the native Sync Client
    public static final String UNKNOWN = "unknown"; // Catch-all category
}
```

These will be mapped into the following high-level classes:


```
// Top-level class for all exceptions for Sync/MongoDB
public open class AppException : RealmException ```
- AppException
	UNKNOWN = "unknown"; // Catch-all category
	DEPRECATED = "deprecated"; // Deprecated errors
	HTTP = "realm::app::HttpError"; // Errors from the HTTP layer
	JSON = "realm::app::JSONError"; // Errors when parsing JSON
	JAVA = "realm::app::CustomError"; // Errors from the Java layer

	- ServiceException
		SERVICE = "realm::app::ServiceError"; // MongoDB Realm Response errors

	    SERVICE_UNKNOWN(Type.SERVICE, -1),
	    SERVICE_NONE(Type.SERVICE, 0),
	    INVALID_SESSION(Type.SERVICE, 2),
	    USER_APP_DOMAIN_MISMATCH(Type.SERVICE, 3),
	    DOMAIN_NOT_ALLOWED(Type.SERVICE, 4),
	    READ_SIZE_LIMIT_EXCEEDED(Type.SERVICE, 5),
	    INVALID_PARAMETER(Type.SERVICE, 6),
	    MISSING_PARAMETER(Type.SERVICE, 7),
	    TWILIO_ERROR(Type.SERVICE, 8),
	    GCM_ERROR(Type.SERVICE, 9),
	    HTTP_ERROR(Type.SERVICE, 10),
	    AWS_ERROR(Type.SERVICE, 11),
	    MONGODB_ERROR(Type.SERVICE, 12),
	    ARGUMENTS_NOT_ALLOWED(Type.SERVICE, 13),
	    FUNCTION_EXECUTION_ERROR(Type.SERVICE, 14),
	    NO_MATCHING_RULE_FOUND(Type.SERVICE, 15),
	    SERVICE_INTERNAL_SERVER_ERROR(Type.SERVICE, 16),
	    AUTH_PROVIDER_NOT_FOUND(Type.SERVICE, 17),
	    AUTH_PROVIDER_ALREADY_EXISTS(Type.SERVICE, 18),
	    SERVICE_NOT_FOUND(Type.SERVICE, 19),
	    SERVICE_TYPE_NOT_FOUND(Type.SERVICE, 20),
	    SERVICE_ALREADY_EXISTS(Type.SERVICE, 21),
	    SERVICE_COMMAND_NOT_FOUND(Type.SERVICE, 22),
	    VALUE_NOT_FOUND(Type.SERVICE, 23),
	    VALUE_ALREADY_EXISTS(Type.SERVICE, 24),
	    VALUE_DUPLICATE_NAME(Type.SERVICE, 25),
	    FUNCTION_NOT_FOUND(Type.SERVICE, 26),
	    FUNCTION_ALREADY_EXISTS(Type.SERVICE, 27),
	    FUNCTION_DUPLICATE_NAME(Type.SERVICE, 28),
	    FUNCTION_SYNTAX_ERROR(Type.SERVICE, 29),
	    FUNCTION_INVALID(Type.SERVICE, 30),
	    INCOMING_WEBHOOK_NOT_FOUND(Type.SERVICE, 31),
	    INCOMING_WEBHOOK_ALREADY_EXISTS(Type.SERVICE, 32),
	    INCOMING_WEBHOOK_DUPLICATE_NAME(Type.SERVICE, 33),
	    RULE_NOT_FOUND(Type.SERVICE, 34),
	    API_KEY_NOT_FOUND(Type.SERVICE, 35),
	    RULE_ALREADY_EXISTS(Type.SERVICE, 36),
	    RULE_DUPLICATE_NAME(Type.SERVICE, 37),
	    AUTH_PROVIDER_DUPLICATE_NAME(Type.SERVICE, 38),
	    RESTRICTED_HOST(Type.SERVICE, 39),
	    API_KEY_ALREADY_EXISTS(Type.SERVICE, 40),
	    INCOMING_WEBHOOK_AUTH_FAILED(Type.SERVICE, 41),
	    EXECUTION_TIME_LIMIT_EXCEEDED(Type.SERVICE, 42),
	    NOT_CALLABLE(Type.SERVICE, 43),
	    BAD_REQUEST(Type.SERVICE, 48),


		- AuthException (Most things involving users should probably go here?)
		    USER_NOT_FOUND(Type.SERVICE, 45), // Confirm logic / Login logic. One is Auth, the other is not?
		    USER_DISABLED(Type.SERVICE, 46), 
		    AUTH_ERROR(Type.SERVICE, 47), // From other auth providers 


		    - UserAlreadyConfirmedException
			    USER_ALREADY_CONFIRMED(Type.SERVICE, 44),

		    - UserAlreadyExistsException
		    	ACCOUNT_NAME_IN_USE(Type.SERVICE, 49), // When registering existing email

		    - InvalidLogin
		    	INVALID_EMAIL_PASSWORD(Type.SERVICE, 50),

	- SyncException
		PROTOCOL = "realm::sync::ProtocolError"; // Protocol level errors from the native Sync Client
		SESSION = "realm::sync::ClientError"; // Session level errors from the native Sync Client
		CONNECTION = "realm.basic_system"; // Connection/System errors from the native Sync Client
		MISC = "realm.util.misc_ext"; // Misc errors from the native Sync Client


	    // Connection level and protocol errors from the native Sync Client
	    CONNECTION_CLOSED(Type.PROTOCOL, 100, Category.RECOVERABLE),    // Connection closed (no error)
	    OTHER_ERROR(Type.PROTOCOL, 101),                                // Other connection level error
	    UNKNOWN_MESSAGE(Type.PROTOCOL, 102),                            // Unknown type of input message
	    BAD_SYNTAX(Type.PROTOCOL, 103),                                 // Bad syntax in input message head
	    LIMITS_EXCEEDED(Type.PROTOCOL, 104),                            // Limits exceeded in input message
	    WRONG_PROTOCOL_VERSION(Type.PROTOCOL, 105),                     // Wrong protocol version (CLIENT)
	    BAD_SESSION_IDENT(Type.PROTOCOL, 106),                          // Bad session identifier in input message
	    REUSE_OF_SESSION_IDENT(Type.PROTOCOL, 107),                     // Overlapping reuse of session identifier (BIND)
	    BOUND_IN_OTHER_SESSION(Type.PROTOCOL, 108),                     // Client file bound in other session (IDENT)
	    BAD_MESSAGE_ORDER(Type.PROTOCOL, 109),                          // Bad input message order
	    BAD_DECOMPRESSION(Type.PROTOCOL, 110),                          // Error in decompression (UPLOAD)
	    BAD_CHANGESET_HEADER_SYNTAX(Type.PROTOCOL, 111),                // Bad server version in changeset header (DOWNLOAD)
	    BAD_CHANGESET_SIZE(Type.PROTOCOL, 112),                         // Bad size specified in changeset header (UPLOAD)
	    BAD_CHANGESETS(Type.PROTOCOL, 113),                             // Bad changesets (UPLOAD)

	    // Session level errors from the native Sync Client
	    SESSION_CLOSED(Type.PROTOCOL, 200, Category.RECOVERABLE),      // Session closed (no error)
	    OTHER_SESSION_ERROR(Type.PROTOCOL, 201, Category.RECOVERABLE), // Other session level error
	    TOKEN_EXPIRED(Type.PROTOCOL, 202, Category.RECOVERABLE),       // Access token expired

	    // Session fatal: Auth wrong. Cannot be fixed without a new User/SyncConfiguration.
	    BAD_AUTHENTICATION(Type.PROTOCOL, 203),                        // Bad user authentication (BIND, REFRESH)
	    ILLEGAL_REALM_PATH(Type.PROTOCOL, 204),                        // Illegal Realm path (BIND)
	    NO_SUCH_PATH(Type.PROTOCOL, 205),                              // No such Realm (BIND)
	    PERMISSION_DENIED(Type.PROTOCOL, 206),                         // Permission denied (BIND, REFRESH)

	    // Fatal: Wrong server/client versions. Trying to sync incompatible files or the file was corrupted.
	    // See https://github.com/realm/realm-core/blob/master/src/realm/sync/protocol.hpp
	    BAD_SERVER_FILE_IDENT(Type.PROTOCOL, 207),                     // Bad server file identifier (IDENT)
	    DIVERGING_HISTORIES(Type.PROTOCOL, 211),                       // Diverging histories (IDENT)
	    DISABLED_SESSION(Type.PROTOCOL, 213),                          // Disabled session
	    PARTIAL_SYNC_DISABLED(Type.PROTOCOL, 214),                     // Partial sync disabled (BIND)
	    UNSUPPORTED_SESSION_FEATURE(Type.PROTOCOL, 215),               // Unsupported session-level feature
	    BAD_ORIGIN_FILE_IDENT(Type.PROTOCOL, 216),                     // Bad origin file identifier (UPLOAD)
	    SERVER_FILE_DELETED(Type.PROTOCOL, 218),                       // Server file was deleted while session was bound to it
	    CLIENT_FILE_BLACKLISTED(Type.PROTOCOL, 219),                   // Client file has been blacklisted (IDENT)
	    USER_BLACKLISTED(Type.PROTOCOL, 220),                          // User has been blacklisted (BIND)
	    TRANSACT_BEFORE_UPLOAD(Type.PROTOCOL, 221),                    // Serialized transaction before upload completion
	    USER_MISMATCH(Type.PROTOCOL, 223),                             // User mismatch for client file identifier (IDENT)
	    TOO_MANY_SESSIONS(Type.PROTOCOL, 224),                         // Too many sessions in connection (BIND)
	    INVALID_SCHEMA_CHANGE(Type.PROTOCOL, 225),                     // Invalid schema change (UPLOAD)
	    BAD_QUERY(Type.PROTOCOL, 226),                                 // Client query is invalid/malformed (IDENT, QUERY)
	    OBJECT_ALREADY_EXISTS(Type.PROTOCOL, 227),                     // Client tried to create an object that already exists outside their view (UPLOAD)
	    INITIAL_SYNC_NOT_COMPLETE(Type.PROTOCOL, 229),                 // Client tried to open a session before initial sync is complete (BIND)
	    WRITE_NOT_ALLOWED(Type.PROTOCOL, 230),                         // Client attempted a write that is disallowed by permissions, or modifies an object outside the current query - requires client reset (UPLOAD)

	    // Sync Network Client errors.
	    // See https://github.com/realm/realm-core/blob/master/src/realm/sync/client_base.hpp#L73
	    CLIENT_CONNECTION_CLOSED(Type.SESSION, 100),            // Connection closed (no error)
	    CLIENT_UNKNOWN_MESSAGE(Type.SESSION, 101),              // Unknown type of input message
	    CLIENT_LIMITS_EXCEEDED(Type.SESSION, 103),              // Limits exceeded in input message
	    CLIENT_BAD_SESSION_IDENT(Type.SESSION, 104),            // Bad session identifier in input message
	    CLIENT_BAD_MESSAGE_ORDER(Type.SESSION, 105),            // Bad input message order
	    CLIENT_BAD_CLIENT_FILE_IDENT(Type.SESSION, 106),        // Bad client file identifier (IDENT)
	    CLIENT_BAD_PROGRESS(Type.SESSION, 107),                 // Bad progress information (DOWNLOAD)
	    CLIENT_BAD_CHANGESET_HEADER_SYNTAX(Type.SESSION, 108),  // Bad syntax in changeset header (DOWNLOAD)
	    CLIENT_BAD_CHANGESET_SIZE(Type.SESSION, 109),           // Bad changeset size in changeset header (DOWNLOAD)
	    CLIENT_BAD_ORIGIN_FILE_IDENT(Type.SESSION, 110),        // Bad origin file identifier in changeset header (DOWNLOAD)
	    CLIENT_BAD_SERVER_VERSION(Type.SESSION, 111),           // Bad server version in changeset header (DOWNLOAD)
	    CLIENT_BAD_CHANGESET(Type.SESSION, 112),                // Bad changeset (DOWNLOAD)
	    CLIENT_BAD_REQUEST_IDENT(Type.SESSION, 113),            // Bad request identifier (MARK)
	    CLIENT_BAD_ERROR_CODE(Type.SESSION, 114),               // Bad error code (ERROR)
	    CLIENT_BAD_COMPRESSION(Type.SESSION, 115),              // Bad compression (DOWNLOAD)
	    CLIENT_BAD_CLIENT_VERSION_DOWNLOAD(Type.SESSION, 116),  // Bad last integrated client version in changeset header (DOWNLOAD)
	    CLIENT_SSL_SERVER_CERT_REJECTED(Type.SESSION, 117),     // SSL server certificate rejected
	    CLIENT_PONG_TIMEOUT(Type.SESSION, 118),                 // Timeout on reception of PONG respone message
	    CLIENT_BAD_CLIENT_FILE_IDENT_SALT(Type.SESSION, 119),   // Bad client file identifier salt (IDENT)
	    CLIENT_FILE_IDENT(Type.SESSION, 120),                   // Bad file identifier (ALLOC)
	    CLIENT_CONNECT_TIMEOUT(Type.SESSION, 121),              // Sync connection was not fully established in time
	    CLIENT_BAD_TIMESTAMP(Type.SESSION, 122),                // Bad timestamp (PONG)
	    CLIENT_BAD_PROTOCOL_FROM_SERVER(Type.SESSION, 123),     // Bad or missing protocol version information from server
	    CLIENT_TOO_OLD_FOR_SERVER(Type.SESSION, 124),           // Protocol version negotiation failed: Client is too old for server
	    CLIENT_TOO_NEW_FOR_SERVER(Type.SESSION, 125),           // Protocol version negotiation failed: Client is too new for server
	    CLIENT_PROTOCOL_MISMATCH(Type.SESSION, 126),            // Protocol version negotiation failed: No version supported by both client and server
	    CLIENT_BAD_STATE_MESSAGE(Type.SESSION, 127),            // Bad values in state message (STATE)
	    CLIENT_MISSING_PROTOCOL_FEATURE(Type.SESSION, 128),     // Requested feature missing in negotiated protocol version
	    CLIENT_BAD_SERIAL_TRANSACT_STATUS(Type.SESSION, 129),   // Bad status of serialized transaction (TRANSACT)
	    CLIENT_BAD_OBJECT_ID_SUBSTITUTIONS(Type.SESSION, 130),  // Bad encoded object identifier substitutions (TRANSACT)
	    CLIENT_HTTP_TUNNEL_FAILED(Type.SESSION, 131),           // Failed to establish HTTP tunnel with configured proxy
	    AUTO_CLIENT_RESET_FAILURE(Type.SESSION, 132),           // Automatic client reset failed

	    CONNECTION_RESET_BY_PEER(Type.CONNECTION, 104, Category.RECOVERABLE), // ECONNRESET: Connection reset by peer
	    CONNECTION_SOCKET_SHUTDOWN(Type.CONNECTION, 110, Category.RECOVERABLE), // ESHUTDOWN: Can't send after socket shutdown
	    CONNECTION_REFUSED(Type.CONNECTION, 111, Category.RECOVERABLE), // ECONNREFUSED: Connection refused
	    CONNECTION_ADDRESS_IN_USE(Type.CONNECTION, 112, Category.RECOVERABLE), // EADDRINUSE: Address already i use
	    CONNECTION_CONNECTION_ABORTED(Type.CONNECTION, 113, Category.RECOVERABLE), // ECONNABORTED: Connection aborted

	    MISC_END_OF_INPUT(Type.MISC, 1), // End of input
	    MISC_PREMATURE_END_OF_INPUT(Type.MISC, 2), // Premature end of input. That is, end of input at an unexpected, or illegal place in an input stream.
	    MISC_DELIMITER_NOT_FOUND(Type.MISC, 3); // Delimiter not found


		- ClientResetRequiredException

			// Client Reset - Recovery
		    BAD_CLIENT_FILE_IDENT(Type.PROTOCOL, 208),                     // Bad client file identifier (IDENT)
	    	BAD_CHANGESET(Type.PROTOCOL, 212),                             // Bad changeset (UPLOAD)
		    BAD_CLIENT_FILE(Type.PROTOCOL, 217),                           // Synchronization no longer possible for client-side file
		    CLIENT_FILE_EXPIRED(Type.PROTOCOL, 222),                       // Client file has expired
	    	SERVER_PERMISSIONS_CHANGED(Type.PROTOCOL, 228),                // Server permissions for this file ident have changed since the last time it was used (IDENT)

			// Client Reset - Only Manual Recovery or Discard Local
    	    BAD_SERVER_VERSION(Type.PROTOCOL, 209),                        // Bad server version (IDENT, UPLOAD)
		    BAD_CLIENT_VERSION(Type.PROTOCOL, 210),                        // Bad client version (IDENT, UPLOAD)
```











