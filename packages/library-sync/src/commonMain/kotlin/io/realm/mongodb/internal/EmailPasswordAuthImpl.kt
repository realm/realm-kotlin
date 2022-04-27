package io.realm.mongodb.internal

import io.realm.internal.interop.RealmAppPointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.platform.freeze
import io.realm.internal.util.Validation
import io.realm.internal.util.use
import io.realm.mongodb.auth.EmailPasswordAuth
import kotlinx.coroutines.channels.Channel

internal class EmailPasswordAuthImpl(private val app: RealmAppPointer) : EmailPasswordAuth {

    override suspend fun registerUser(email: String, password: String) {
        Channel<Result<Unit>>(1).use { channel ->
            RealmInterop.realm_app_email_password_provider_client_register_email(
                app,
                Validation.checkEmpty(email, "email"),
                Validation.checkEmpty(password, "password"),
                channelResultCallback<Unit, Unit>(channel) {
                    // No-op
                }.freeze()
            )
            return channel.receive()
                .getOrThrow()
        }
    }

    override suspend fun confirmUser(token: String, tokenId: String) {
        Channel<Result<Unit>>(1).use { channel ->
            RealmInterop.realm_app_email_password_provider_client_confirm_user(
                app,
                Validation.checkEmpty(token, "token"),
                Validation.checkEmpty(tokenId, "tokenId"),
                channelResultCallback<Unit, Unit>(channel) {
                    // No-op
                }.freeze()
            )
            return channel.receive()
                .getOrThrow()
        }
    }

    override suspend fun resendConfirmationEmail(email: String) {
        Channel<Result<Unit>>(1).use { channel ->
            RealmInterop.realm_app_email_password_provider_client_resend_confirmation_email(
                app,
                Validation.checkEmpty(email, "email"),
                channelResultCallback<Unit, Unit>(channel) {
                    // No-op
                }.freeze()
            )
            return channel.receive()
                .getOrThrow()
        }
    }

    override suspend fun retryCustomConfirmation(email: String) {
        Channel<Result<Unit>>(1).use { channel ->
            RealmInterop.realm_app_email_password_provider_client_retry_custom_confirmation(
                app,
                Validation.checkEmpty(email, "email"),
                channelResultCallback<Unit, Unit>(channel) {
                    // No-op
                }.freeze()
            )
            return channel.receive()
                .getOrThrow()
        }
    }

    override suspend fun sendResetPasswordEmail(email: String) {
        Channel<Result<Unit>>(1).use { channel ->
            RealmInterop.realm_app_email_password_provider_client_send_reset_password_email(
                app,
                Validation.checkEmpty(email, "email"),
                channelResultCallback<Unit, Unit>(channel) {
                    // No-op
                }.freeze()
            )
            return channel.receive()
                .getOrThrow()
        }
    }

    override suspend fun resetPassword(token: String, tokenId: String, newPassword: String) {
        Channel<Result<Unit>>(1).use { channel ->
            RealmInterop.realm_app_email_password_provider_client_reset_password(
                app,
                Validation.checkEmpty(token, "token"),
                Validation.checkEmpty(tokenId, "tokenId"),
                Validation.checkEmpty(newPassword, "newPassword"),
                channelResultCallback<Unit, Unit>(channel) {
                    // No-op
                }.freeze()
            )
            return channel.receive()
                .getOrThrow()
        }
    }
}
