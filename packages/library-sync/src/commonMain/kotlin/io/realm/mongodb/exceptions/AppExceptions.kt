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

package io.realm.mongodb.exceptions

import io.realm.exceptions.RealmException

/**
 * Top level class for all exceptions that are specific to using Atlas App Services and Device Sync.
 *
 * Generally, reacting to this exception will be hard at runtime, except to log the error for
 * further analysis. But in most cases a more specific subtype will be thrown, which will be easier
 * to react to.
 *
 * Subclasses of this class fall into two broad categories: [ServiceException] and [SyncException].
 *
 * 1. [ServiceException]'s are thrown by all API's that talk directly to Atlas App Services
 *    through HTTP requests. The [io.realm.mongodb.App],
 *    [io.realm.mongodb.User] and [[io.realm.mongodb.EmailPasswordAuth] classes
 *    handle HTTP requests for the SDK.
 *
 * 2. [SyncException]'s are thrown by errors caused when using Device Sync APIs, i.e
 *    realms opened using a [io.realm.mongodb.SyncConfiguration]. These errors propagate
 *    through the [io.realm.mongodb.SyncConfiguration.Builder.errorHandler].
 *
 * Each of these categories are divided further:
 *
 * - [AppException]
 *   - [ServiceException]
 *     - [BadRequestException]
 *     - [ConnectionException]
 *     - [AuthException]
 *       - [UserNotFoundException]
 *       - [UserAlreadyConfirmedException]
 *       - [UserAlreadyExistsException]
 *       - [InvalidCredentialsException]
 *   - [SyncException]
 *     - [UnrecoverableSyncException]
 *     - [WrongSyncTypeException]
 *
 * Exceptions at the bottom of the hierarchy
 * are _actionable_, i.e. it should be clear from the exception which action can be taken to
 * resolve it.
 *
 * Exceptions further up the hierarchy categorize the errors. They can be
 * harder to react to in a single way that will make sense to end users of an app, but should
 * be logged for later inspection.
 *
 * In most cases, only exceptions at the bottom of the hierarchy will be documented in the API
 * documentation, with one notable special case: [ConnectionException]. This exception is
 * assumed to be thrown by all methods that mentions a [ServiceException] and covers all transport
 * errors. These are often intermittent, so catching this exception and retrying should generally
 * be safe, but more information can be found in the documentation for [ConnectionException].
 *
 * With the above exception hierarchy in mind, a sensible way to handle errors could look like
 * this:
 *
 * ```
 * val app = App.with("my-app-id")
 * runCatching {
 *     app.login(Credentials.emailPassword("myemail@mail.com", "mypassword"))
 * }.onSuccess {
 *     gotoMainScreen()
 * }.onFailure { ex: Throwable ->
 *     when(ex) {
 *         is InvalidCredentialsException -> {
 *             showWrongPasswordDialog()
 *         }
 *         is ConnectionException -> {
 *             CrashLogger.log(ex.toString())
 *             showRetryLoginDialog()
 *         }
 *         else -> {
 *             CrashLogger.log(ex.toString())
 *             showContactAdministratorDialog()
 *         }
 *     }
 * }
 * ```
 *
 * For more information about specific exceptions, see the documentation for that exception.
 *
 * @see ServiceException
 * @see SyncException
 */
public open class AppException : RealmException {
    internal constructor(message: String) : super(message)
}

/**
 * This exception is considered the top-level or "catch-all" for problems related to HTTP requests
 * made towards App Services. This covers both HTTP transport problems, problems passing JSON
 * or the server considering the request invalid, for whatever reason.
 *
 * Generally, reacting to this exception will be hard, except to log the error for further
 * analysis. But in many cases a more specific subtype will be thrown, which will be easier to
 * react to. See the subclasses of this exception for more information about these.
 *
 * @see ConnectionException
 * @see BadRequestException
 * @see AuthException
 */
public open class ServiceException : AppException {
    internal constructor(message: String) : super(message)
}

/**
 * Exception indicating that something went wrong with the underlying HTTP request to * Atlas App Services. The exact cause is in the exception message.
 *
 * Errors resulting in this exception are outside the apps control and can be considered
 * temporary. Retrying the action some time in the future should generally be safe, but since
 * corrective measures potentially need to taken in either the apps network environment
 * or on the server, you should log this error for further analysis.
 *
 * Note, HTTP responses that indicate problems that can be fixed in the app will throw a more
 * specific exception instead, e.g. `401 - AuthenticationRequired` will throw
 * [InvalidCredentialsException].
 */
public open class ConnectionException : ServiceException {
    internal constructor(message: String) : super(message)
}

/**
 * This exception is thrown when parameters sent to Atlas App Services are considered malformed.
 * This can happen if e.g. tokens do not have the required length or contain garbage data. The
 * exact reason will be in the error message.
 *
 * @see io.realm.mongodb.EmailPasswordAuth.resetPassword
 */
public class BadRequestException : ServiceException {
    internal constructor(message: String) : super(message)
}

/**
 * This exception is considered the top-level or "catch-all" for problems related to user account
 * actions. The exact reason for the error can be found in [Throwable.message].
 *
 * Generally, this exception does not need to be caught as more specific subtypes are available.
 * These will be documented for the relevant API methods.
 *
 * @see UserAlreadyConfirmedException
 * @see UserNotFoundException
 * @see UserAlreadyExistsException
 * @see InvalidCredentialsException
 */
public open class AuthException : ServiceException {
    internal constructor(message: String) : super(message)
}

/**
 * Thrown when trying to confirm a user that was already confirmed on the server.
 *
 * @see io.realm.mongodb.EmailPasswordAuth.confirmUser
 * @see io.realm.mongodb.EmailPasswordAuth.resendConfirmationEmail
 * @see io.realm.mongodb.EmailPasswordAuth.retryCustomConfirmation
 */
public class UserAlreadyConfirmedException : AuthException {
    internal constructor(message: String) : super(message)
}

/**
 * Thrown when using an API that also require some kind of user identifier, but the server is
 * not able to find the user.
 *
 * @see io.realm.mongodb.EmailPasswordAuth.resendConfirmationEmail
 * @see io.realm.mongodb.EmailPasswordAuth.resetPassword
 * @see io.realm.mongodb.EmailPasswordAuth.retryCustomConfirmation
 * @see io.realm.mongodb.EmailPasswordAuth.sendResetPasswordEmail
 */
public class UserNotFoundException : AuthException {
    internal constructor(message: String) : super(message)
}

/**
 * Thrown when trying to register a new user with email and password, and the user already
 * exists.
 *
 * @see io.realm.mongodb.EmailPasswordAuth.registerUser
 */
public class UserAlreadyExistsException : AuthException {
    internal constructor(message: String) : super(message)
}

/**
 * Thrown when credentials were rejected by the server when trying to log in. Only some
 * authentication providers will return this error:
 *
 * - [io.realm.mongodb.AuthenticationProvider.EMAIL_PASSWORD]
 * - [io.realm.mongodb.AuthenticationProvider.API_KEY]
 * - [io.realm.mongodb.AuthenticationProvider.JWT]
 *
 * The remaining authentication providers will throw a more general [AuthException] instead.
 *
 * @see io.realm.mongodb.App.login
 */
public class InvalidCredentialsException : AuthException {
    internal constructor(message: String) : super(message)
}

/**
 * This exception is considered the top-level exception or general "catch-all" for problems related
 * to using Device Sync.
 *
 * This exception and subclasses of it will be passed to users through
 * [io.realm.mongodb.SyncConfiguration.Builder.errorHandler] and the the exact reason must be found
 * in [Throwable.message].
 *
 * @see io.realm.mongodb.SyncConfiguration.Builder.errorHandler which is responsible for handling
 * this type of exceptions.
 */
public open class SyncException : AppException {
    internal constructor(message: String) : super(message)
}

/**
 * Thrown when something has gone wrong with Device Sync in a way that is not recoverable.
 *
 * Generally, errors of this kind are due to incompatible versions of Realm and Atlas App Services
 * being used or bugs in the library or on the server, and the only fix would be installing a new
 * version of the app with a new version of Realm.
 *
 * It is still possible to use the Realm locally after this error occurs. However, this must be
 * done with caution as data written to the realm after this point risk getting lost as
 * many errors of this category will result in a Client Reset once the client
 * re-connects to the server.
 *
 * @see io.realm.mongodb.SyncConfiguration.Builder.errorHandler which is responsible for handling
 * this type of exceptions.
 */
public class UnrecoverableSyncException : SyncException {
    internal constructor(message: String) : super(message)
}

/**
 * Thrown when the type of sync used by the server does not match the one used by the client, i.e.
 * the server and client disagrees whether to use Partition-based or Flexible Sync.
 */
public class WrongSyncTypeException : SyncException {
    internal constructor(message: String) : super(message)
}
