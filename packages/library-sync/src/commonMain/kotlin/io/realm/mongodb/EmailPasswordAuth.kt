package io.realm.mongodb

import io.realm.internal.interop.CinteropVoidCallback
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.util.Validation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Class encapsulating functionality provided when [User]s are logged in through the
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
        suspendCoroutine<Unit> { continuation ->
            RealmInterop.realm_app_email_password_provider_client_register_email(
                app,
                Validation.checkEmpty(email, "email"),
                Validation.checkEmpty(password, "password"),
                object : CinteropVoidCallback {
                    override fun onSuccess() {
                        continuation.resume(Unit)
                    }

                    override fun onError(throwable: Throwable) {
                        continuation.resumeWithException(throwable)
                    }
                }
            )
        }
    }
}
