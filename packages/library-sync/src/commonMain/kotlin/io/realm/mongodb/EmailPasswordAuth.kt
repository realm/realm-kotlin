package io.realm.mongodb

import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.channelResultCallback
import io.realm.internal.platform.freeze
import io.realm.internal.util.Validation
import io.realm.internal.util.use
import kotlinx.coroutines.channels.Channel

/**
 * Class encapsulating functionality for managing [User]s through the
 * [AuthenticationProvider.EMAIL_PASSWORD] provider.
 */
class EmailPasswordAuth(
    private val app: NativePointer
) {

    /**
     * Registers a new user with the given email and password.
     *
     * @param email the email used to register a user. This will be the username used during log in.
     * @param password the password associated with the email. The password must be between
     * 6 and 128 characters long.
     *
     * @throws AppException if the server failed to register the user.
     */
    suspend fun registerUser(email: String, password: String) {
        Channel<Result<Unit>>(1).use { channel ->
            RealmInterop.realm_app_email_password_provider_client_register_email(
                app,
                Validation.checkEmpty(email, "email"),
                Validation.checkEmpty(password, "password"),
                channelResultCallback<Unit, Unit>(channel) {
                    // No-op
                }.freeze() // MM: Doesn't catch user references
            )
            return channel.receive()
                .getOrThrow()
        }
    }
}
