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

package io.realm.mongodb.internal

import io.realm.internal.interop.AppCallback
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.mongodb.User
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal class UserImpl(
    val nativePointer: NativePointer,
    override val app: AppImpl
) : User {

    override val state: User.State
        get() = User.State.fromCoreState(RealmInterop.realm_user_get_state(nativePointer))

    // TODO Can maybe fail, but we could also cache the return value?
    override val identity: String
        get() = RealmInterop.realm_user_get_identity(nativePointer)

    override val loggedIn: Boolean
        get() = RealmInterop.realm_user_is_logged_in(nativePointer)

    override suspend fun logOut() {
        return suspendCoroutine { continuation ->
            RealmInterop.realm_app_log_out(
                app.nativePointer,
                nativePointer,
                object : AppCallback<Unit> {
                    override fun onSuccess(result: Unit) {
                        continuation.resume(Unit)
                    }

                    override fun onError(throwable: Throwable) {
                        continuation.resumeWithException(throwable)
                    }
                }
            )
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
}
