package io.realm.mongodb.exceptions

import io.realm.exceptions.RealmException

/**
 * Top level class for all exceptions that are specific to using Atlas App Services API's.
 *
 * In most cases, a subclass of this type will be thrown. Exactly which, will will be documented by
 * the individual API entry points.
 *
 * FIXME Add very detailed explanation here since all API entry points will point to this.
 */
public open class AppException : RealmException {
    internal constructor(message: String) : super(message)
}

/**
 * TODO
 */
public open class ServiceException : AppException {
    internal constructor(message: String) : super(message)
}

/**
 * Exception indicating that something went wrong with a HTTP request towards one of the
 * Atlas App Services. The exact cause is in the exception message. Generally, the error
 * resulting in this exception being thrown should be temporary and retrying the
 * action resulting in this exception should be safe.
 */
// TODO Naming?
// TODO Should we have a more generic "ConnectionIssuePleaseRetryException" that also cover ServiceErrorCodes?
public open class HttpConnectionException : ServiceException {
    internal constructor(message: String) : super(message)
}

/**
 *
 */
public class BadServiceRequestException : ServiceException {
    internal constructor(message: String) : super(message)
}

/**
 * TODO
 */
public open class AuthException : ServiceException {
    internal constructor(message: String) : super(message)
}

/**
 * TODO
 */
public class UserAlreadyConfirmedException : AuthException {
    internal constructor(message: String) : super(message)
}

/**
 * TODO
 */
public class UserNotFoundException : AuthException {
    internal constructor(message: String) : super(message)
}

/**
 * TODO
 */
public class UserAlreadyExistsException : AuthException {
    internal constructor(message: String) : super(message)
}

/**
 * TODO
 */
public class InvalidCredentialsException : AuthException {
    internal constructor(message: String) : super(message)
}

/**
 * A problem occurred when using Device Sync. The exact reason will be in the error message.
 */
public open class SyncException : AppException {
    internal constructor(message: String) : super(message)
}

/**
 * Something has gone terribly wrong with Sync.
 *
 * Generally, errors of this kind are due to incompatible versions of client and server being used,
 * and the only fix would be installing a new version of the app with a new version of Realm.
 */
// TODO Naming?
public class UnrecoverableSyncException : SyncException {
    internal constructor(message: String) : super(message)
}

/**
 * The type of sync used by the server does not match the one used by the client, i.e.
 * the server and client disagrees whether to use Partition-based or Flexible Sync.
 */
public class WrongSyncTypeException : SyncException {
    internal constructor(message: String) : super(message)
}
