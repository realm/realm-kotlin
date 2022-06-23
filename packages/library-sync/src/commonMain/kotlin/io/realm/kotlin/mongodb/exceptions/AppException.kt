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

package io.realm.kotlin.mongodb.exceptions

import io.realm.kotlin.exceptions.RealmException

/**
 * Top level class for all exceptions that are specific to using App Services and Device Sync.
 *
 * Subclasses of this class fall into two broad categories: [ServiceException] and [SyncException].
 *
 * 1. [ServiceException]'s are thrown by all API's that talk directly to App Services
 *    through HTTP requests. The [io.realm.kotlin.mongodb.App],
 *    [io.realm.kotlin.mongodb.User] and [io.realm.kotlin.mongodb.auth.EmailPasswordAuth] classes
 *    handle HTTP requests for the SDK.
 *
 * 2. [SyncException]'s are thrown by errors caused when using Device Sync APIs, i.e
 *    realms opened using a [io.realm.kotlin.mongodb.SyncConfiguration]. These errors propagate
 *    through the [io.realm.kotlin.mongodb.SyncConfiguration.Builder.errorHandler].
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
 * Exceptions at the bottom of the hierarchy are _actionable_, i.e. it should be clear from the
 * exception which action can be taken to resolve it.
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
