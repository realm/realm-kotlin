/*
 * Copyright 2021 Realm Inc.
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

package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmUserPointer
import io.realm.kotlin.internal.interop.sync.AuthProvider
import io.realm.kotlin.internal.interop.sync.CoreUserState
import io.realm.kotlin.internal.platform.freeze
import io.realm.kotlin.internal.util.use
import io.realm.kotlin.mongodb.AuthenticationProvider
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.UserIdentity
import io.realm.kotlin.mongodb.exceptions.CredentialsCannotBeLinkedException
import io.realm.kotlin.mongodb.exceptions.ServiceException
import kotlinx.coroutines.channels.Channel

// TODO Public due to being a transitive dependency to SyncConfigurationImpl
public class UserImpl(
    public val nativePointer: RealmUserPointer,
    override val app: AppImpl
) : User {

    override val state: User.State
        get() = fromCoreState(RealmInterop.realm_user_get_state(nativePointer))

    // TODO Can maybe fail, but we could also cache the return value?
    override val identity: String
        get() = id
    override val id: String
        get() = RealmInterop.realm_user_get_identity(nativePointer)
    override val loggedIn: Boolean
        get() = RealmInterop.realm_user_is_logged_in(nativePointer)
    override val provider: AuthenticationProvider
        get() = getProviderFromCore(RealmInterop.realm_user_get_auth_provider(nativePointer))
    override val accessToken: String
        get() = RealmInterop.realm_user_get_access_token(nativePointer)
    override val identities: List<UserIdentity>
        get() = RealmInterop.realm_user_get_all_identities(nativePointer).map {
            UserIdentity(it.id, getProviderFromCore(it.provider))
        }

    override suspend fun logOut() {
        Channel<Result<Unit>>(1).use { channel ->
            RealmInterop.realm_app_log_out(
                app.nativePointer,
                nativePointer,
                channelResultCallback<Unit, Unit>(channel) {
                    // No-op
                }.freeze()
            )
            return@use channel.receive()
                .getOrThrow()
        }
    }

    override suspend fun remove(): User {
        Channel<Result<Unit>>(1).use { channel ->
            RealmInterop.realm_app_remove_user(
                app.nativePointer,
                nativePointer,
                channelResultCallback<Unit, Unit>(channel) {
                    // No-op
                }.freeze()
            )
            return@use channel.receive()
                .getOrThrow()
        }
        return this
    }

    override suspend fun delete() {
        if (state != User.State.LOGGED_IN) {
            throw IllegalStateException("User must be logged in, in order to be deleted.")
        }
        Channel<Result<Unit>>(1).use { channel ->
            RealmInterop.realm_app_delete_user(
                app.nativePointer,
                nativePointer,
                channelResultCallback<Unit, Unit>(channel) {
                    // No-op
                }.freeze()
            )
            return@use channel.receive()
                .getOrThrow()
        }
    }

    override suspend fun linkCredentials(credentials: Credentials): User {
        if (state != User.State.LOGGED_IN) {
            throw IllegalStateException("User must be logged in, in order to link credentials to it.")
        }
        try {
            Channel<Result<User>>(1).use { channel ->
                RealmInterop.realm_app_link_credentials(
                    app.nativePointer,
                    nativePointer,
                    (credentials as CredentialsImpl).nativePointer,
                    channelResultCallback<RealmUserPointer, User>(channel) { userPointer ->
                        UserImpl(userPointer, app)
                    }.freeze()
                )
                channel.receive().getOrThrow()
                return this
            }
        } catch (ex: ServiceException) {
            // Linking an account with itself throws a different error code than other linking errors:
            // It is unclear if this error is shared between other error scenarios, so for now,
            // we remap the exception type here instead of in the generic handler in
            // `RealmSyncUtils.kt`.
            if (ex.message?.contains("[Service][InvalidSession(2)] a user already exists with the specified provider.") == true) {
                throw CredentialsCannotBeLinkedException(ex.message!!)
            } else {
                throw ex
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as UserImpl

        if (identity != (other.identity)) return false
        return app.configuration == other.app.configuration
    }

    override fun hashCode(): Int {
        var result = identity.hashCode()
        result = 31 * result + app.configuration.appId.hashCode()
        return result
    }

    private fun getProviderFromCore(authProvider: AuthProvider): AuthenticationProvider{
        return when (authProvider) {
            AuthProvider.RLM_AUTH_PROVIDER_ANONYMOUS -> AuthenticationProvider.ANONYMOUS
            AuthProvider.RLM_AUTH_PROVIDER_ANONYMOUS_NO_REUSE -> AuthenticationProvider.ANONYMOUS
            AuthProvider.RLM_AUTH_PROVIDER_FACEBOOK -> AuthenticationProvider.FACEBOOK
            AuthProvider.RLM_AUTH_PROVIDER_GOOGLE -> AuthenticationProvider.GOOGLE
            AuthProvider.RLM_AUTH_PROVIDER_APPLE -> AuthenticationProvider.APPLE
            AuthProvider.RLM_AUTH_PROVIDER_CUSTOM -> TODO()
            AuthProvider.RLM_AUTH_PROVIDER_EMAIL_PASSWORD -> AuthenticationProvider.EMAIL_PASSWORD
            AuthProvider.RLM_AUTH_PROVIDER_FUNCTION -> TODO()
            AuthProvider.RLM_AUTH_PROVIDER_USER_API_KEY -> AuthenticationProvider.API_KEY
            AuthProvider.RLM_AUTH_PROVIDER_SERVER_API_KEY -> TODO()
            else -> throw IllegalStateException("Unknown auth provider: $authProvider")
        }
    }

    public companion object {
        /**
         * Converts a Core state value to a library state value.
         *
         * For internal use only.
         */
        public fun fromCoreState(coreState: CoreUserState): User.State = when (coreState) {
            CoreUserState.RLM_USER_STATE_LOGGED_OUT -> User.State.LOGGED_OUT
            CoreUserState.RLM_USER_STATE_LOGGED_IN -> User.State.LOGGED_IN
            CoreUserState.RLM_USER_STATE_REMOVED -> User.State.REMOVED
            else -> throw IllegalArgumentException("Invalid user state: ${coreState.name}")
        }
    }
}
