package io.realm.mongodb.exceptions

import io.realm.mongodb.SyncSession

public open class RealmException : RuntimeException {
    internal constructor(message: String): super(message)
    internal constructor(message: String, cause: Throwable): super(message, cause)
}

public open class RealmError : Error {
    internal constructor(message: String): super(message)
    internal constructor(message: String, cause: Throwable): super(message, cause)
}

// Top-level exception for all MongoDB/Sync API's
public open class AppException: RealmException {
    public val category: String // Is there any value in making this an enum?
    public val errorCode: Int
    public val message: String
    public constructor(category: String, errorCode: Int, message: String) : super(message) { TODO() }
}

// Top-level exception for all exceptions with a category of `realm::app::ServiceError`. These
// should all have a server log attached as well
public open class ServiceException: AppException {
    public val serverLog: String
    public constructor(category: String, errorCode: Int, message: String, serverLog: String) : super(message) { TODO() }
}

public open class AuthException: ServiceException {}

public class UserAlreadyConfirmedException: AuthException {}
public class UserAlreadyExistsException: AuthException {}
public class InvalidCredentialsExceptions: AuthException {}

// These errors will be sent to SyncSession error handlers.
public open class SyncException: AppException {
    // SyncExceptions do not currently have any extra data associated with them...perhaps `isFatal`
    // but there doesn't seem any value in exposing it
}

public class ClientResetRequiredException: SyncException {}










private fun test() {
    val ex: AppException = getEx()
    when(ex) {
        is InvalidLoginException -> TODO()
        is HttpException -> TODO()
        is SyncException -> TODO()
    }
}

private fun getEx(): AppException {
    TODO()
}
