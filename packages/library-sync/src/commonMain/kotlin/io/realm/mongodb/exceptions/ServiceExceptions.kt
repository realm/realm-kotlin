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
 * Exception indicating that something went wrong with the underlying HTTP request to
 * App Services. The exact cause is in the exception message.
 *
 * Errors resulting in this exception are outside the apps control and can be considered
 * temporary. Retrying the action some time in the future should generally be safe, but since
 * this potentially requires corrective measures in either the apps network environment
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
