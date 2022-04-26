package io.realm.mongodb.auth

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
     * @throws AppException if the server failed to register the user.
     */
    public suspend fun registerUser(email: String, password: String)

    /**
     * Confirms a user with the given token and token id.
     *
     * @param token the confirmation token.
     * @param tokenId the id of the confirmation token.
     * @throws AppException if the server failed to confirm the user.
     * @see https://www.mongodb.com/docs/realm/web/manage-email-password-users/#complete-a-user-confirmation
     */
    public suspend fun confirmUser(token: String, tokenId: String)

    /**
     * Resend the confirmation for a user to the given email.
     *
     * @param email the email of the user.
     * @throws AppException if the server failed to confirm the user.
     */
    public suspend fun resendConfirmationEmail(email: String)

    /**
     * Retries the custom confirmation on a user for a given email.
     *
     * @param email the email of the user.
     * @throws AppException if the server failed to confirm the user.
     */
    public suspend fun retryCustomConfirmation(email: String)

    /**
     * Sends a user a password reset email for the given email.
     *
     * @param email the email of the user.
     * @throws AppException if the server failed to confirm the user.
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
     * @throws AppException if the server failed to confirm the user.
     */
    public suspend fun resetPassword(token: String, tokenId: String, newPassword: String)
}
