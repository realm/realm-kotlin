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
import io.realm.kotlin.internal.interop.sync.CoreUserState
import io.realm.kotlin.internal.util.use
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.Functions
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.UserIdentity
import io.realm.kotlin.mongodb.auth.ApiKeyAuth
import io.realm.kotlin.mongodb.mongo.MongoClient
import kotlinx.coroutines.channels.Channel
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson

// TODO Public due to being a transitive dependency to SyncConfigurationImpl
public class UserImpl(
    public val nativePointer: RealmUserPointer,
    override val app: AppImpl
) : User {
    override val apiKeyAuth: ApiKeyAuth by lazy {
        ApiKeyAuthImpl(app, this)
    }
    override val state: User.State
        get() = fromCoreState(RealmInterop.realm_user_get_state(nativePointer))

    override val id: String
        get() = RealmInterop.realm_user_get_identity(nativePointer)
    override val loggedIn: Boolean
        get() = RealmInterop.realm_user_is_logged_in(nativePointer)

    override val accessToken: String
        get() = RealmInterop.realm_user_get_access_token(nativePointer)
    override val refreshToken: String
        get() = RealmInterop.realm_user_get_refresh_token(nativePointer)
    override val deviceId: String
        get() = RealmInterop.realm_user_get_device_id(nativePointer)
    override val functions: Functions by lazy { FunctionsImpl(app, this) }

    @PublishedApi
    internal fun <T> profileInternal(block: (ejsonEncodedProfile: String) -> T): T =
        block(RealmInterop.realm_user_get_profile(nativePointer))

    @PublishedApi
    internal fun <T> customDataInternal(block: (ejsonEncodedCustomData: String) -> T?): T? =
        RealmInterop.realm_user_get_custom_data(nativePointer)?.let(block)

    override suspend fun refreshCustomData() {
        Channel<Result<Unit>>(1).use { channel ->
            RealmInterop.realm_user_refresh_custom_data(
                app = app.nativePointer,
                user = nativePointer,
                callback = channelResultCallback<Unit, Unit>(channel) {
                    // No-op
                }
            )
            return channel.receive()
                .getOrThrow()
        }
    }

    override val identities: List<UserIdentity>
        get() = RealmInterop.realm_user_get_all_identities(nativePointer).map {
            UserIdentity(it.id, AuthenticationProviderImpl.fromId(it.provider))
        }

    override suspend fun logOut() {
        Channel<Result<User.State?>>(1).use { channel ->
            val reportLoggedOut = loggedIn
            RealmInterop.realm_app_log_out(
                app.nativePointer,
                nativePointer,
                channelResultCallback<Unit, User.State?>(channel) {
                    if (reportLoggedOut) {
                        User.State.LOGGED_OUT
                    } else {
                        null
                    }
                }
            )
            return@use channel.receive()
                .getOrThrow().also { state: User.State? ->
                    if (state != null) {
                        app.reportAuthenticationChange(this, state)
                    }
                }
        }
    }

    override suspend fun remove(): User {
        Channel<Result<User.State?>>(1).use { channel ->
            val reportRemoved = loggedIn
            RealmInterop.realm_app_remove_user(
                app.nativePointer,
                nativePointer,
                channelResultCallback<Unit, User.State?>(channel) {
                    if (reportRemoved) {
                        User.State.REMOVED
                    } else {
                        null
                    }
                }
            )
            return@use channel.receive()
                .getOrThrow().also { state: User.State? ->
                    if (state != null) {
                        app.reportAuthenticationChange(this, state)
                    }
                }
        }
        return this
    }

    override suspend fun delete() {
        if (state != User.State.LOGGED_IN) {
            throw IllegalStateException("User must be logged in, in order to be deleted.")
        }
        Channel<Result<User.State>>(1).use { channel ->
            RealmInterop.realm_app_delete_user(
                app.nativePointer,
                nativePointer,
                channelResultCallback<Unit, User.State>(channel) {
                    User.State.REMOVED
                }
            )
            return@use channel.receive()
                .getOrThrow().also { state: User.State ->
                    app.reportAuthenticationChange(this, state)
                }
        }
    }

    override suspend fun linkCredentials(credentials: Credentials): User {
        if (state != User.State.LOGGED_IN) {
            throw IllegalStateException("User must be logged in, in order to link credentials to it.")
        }
        Channel<Result<User>>(1).use { channel ->
            RealmInterop.realm_app_link_credentials(
                app.nativePointer,
                nativePointer,
                (credentials as CredentialsImpl).nativePointer,
                channelResultCallback<RealmUserPointer, User>(channel) { userPointer ->
                    UserImpl(userPointer, app)
                }
            )
            channel.receive().getOrThrow()
            return this
        }
    }

    @ExperimentalKBsonSerializerApi
    override fun mongoClient(serviceName: String, eJson: EJson?): MongoClient {
        if (!loggedIn) throw IllegalStateException("Cannot obtain a MongoClient from a logged out user")
        return MongoClientImpl(this, serviceName, eJson ?: app.configuration.ejson)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as UserImpl
        if (id != (other.id)) return false

        return app.configuration == other.app.configuration
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + app.configuration.appId.hashCode()
        return result
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
