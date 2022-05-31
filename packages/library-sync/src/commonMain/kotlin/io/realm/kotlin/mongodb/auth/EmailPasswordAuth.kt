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

package io.realm.kotlin.mongodb.auth

import io.realm.kotlin.mongodb.exceptions.AppException

/**
 * Class encapsulating functionality for managing [User]s through the
 * [AuthenticationProvider.EMAIL_PASSWORD] provider.
 */
public interface EmailPasswordAuth {
    /**
     * Registers a new user with the given email and password.
     *
     * @param email the email used to register a user. This will be the username used during log in.
     * @param password the password associated with the email. The password must be between
     * 6 and 128 characters long.
     * @throws io.realm.kotlin.mongodb.exceptions.UserAlreadyExistsException if this email was already
     * registered.
     * @throws io.realm.kotlin.mongodb.exceptions.ServiceException for other failures that can happen when
     * communicating with App Services. See [AppException] for details.
     */
    public suspend fun registerUser(email: String, password: String)

    /**
     * Confirms a user with the given token and token id.
     *
     * @param token the confirmation token.
     * @param tokenId the id of the confirmation token.
     * @throws io.realm.kotlin.mongodb.exceptions.UserAlreadyConfirmedException if this email was already
     * confirmed.
     * @throws io.realm.kotlin.mongodb.exceptions.ServiceException for other failures that can happen when
     * communicating with App Services. See [AppException] for details.
     */
    public suspend fun confirmUser(token: String, tokenId: String)

    /**
     * Resend the confirmation for a user to the given email.
     *
     * @param email the email of the user.
     * @throws io.realm.kotlin.mongodb.exceptions.UserNotFoundException if no user was registered with
     * this email.
     * @throws io.realm.kotlin.mongodb.exceptions.UserAlreadyConfirmedException if the user was already
     * confirmed.
     * @throws io.realm.kotlin.mongodb.exceptions.ServiceException for other failures that can happen when
     * communicating with App Services. See [AppException] for details.
     */
    public suspend fun resendConfirmationEmail(email: String)

    /**
     * Retries the custom confirmation on a user for a given email.
     *
     * @param email the email of the user.
     * @throws io.realm.kotlin.mongodb.exceptions.UserNotFoundException if no user was registered with
     * this email.
     * @throws io.realm.kotlin.mongodb.exceptions.UserAlreadyConfirmedException if the user was already
     * confirmed.
     * @throws io.realm.kotlin.mongodb.exceptions.BadRequestException if the custom function failed to
     * confirm the user.
     * @throws io.realm.kotlin.mongodb.exceptions.ServiceException for other failures that can happen when
     * communicating with App Services. See [AppException] for details.
     */
    public suspend fun retryCustomConfirmation(email: String)

    /**
     * Sends a user a password reset email for the given email.
     *
     * @param email the email of the user.
     * @throws io.realm.kotlin.mongodb.exceptions.UserNotFoundException if no user was registered with
     * this email.
     * @throws io.realm.kotlin.mongodb.exceptions.ServiceException for other failures that can happen when
     * communicating with App Services. See [AppException] for details.
     */
    public suspend fun sendResetPasswordEmail(email: String)

    // TODO https://github.com/realm/realm-kotlin/issues/744
    // /**
    //  * Call the reset password function configured to the
    //  * [Credentials.Provider.EMAIL_PASSWORD] provider.
    //  *
    //  * @param email the email of the user.
    //  * @param newPassword the new password of the user.
    //  * @param args any additional arguments provided to the reset function. All arguments must
    //  * be able to be converted to JSON compatible values using `toString()`.
    //  * @throws AppException if the server failed to confirm the user.
    //  */
    // public suspend fun callResetPasswordFunction(email: String, newPassword: String, vararg args: Any?)

    /**
     * Resets the password of a user with the given token, token id, and new password.
     *
     * @param token the reset password token.
     * @param tokenId the id of the reset password token.
     * @param newPassword the new password for the user identified by the `token`. The password
     * must be between 6 and 128 characters long.
     * @throws io.realm.kotlin.mongodb.exceptions.UserNotFoundException if the tokens do not map to an
     * existing user.
     * @throws io.realm.kotlin.mongodb.exceptions.BadRequestException if the input tokens where
     * rejected by the server for being malformed.
     * @throws io.realm.kotlin.mongodb.exceptions.ServiceException for other failures that can happen when
     * communicating with App Services. See [AppException] for details.
     */
    public suspend fun resetPassword(token: String, tokenId: String, newPassword: String)
}
