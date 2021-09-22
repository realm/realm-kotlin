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

import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.mongodb.Credentials
import io.realm.mongodb.AuthenticationProvider

internal open class CredentialImpl(
    authenticationProvider: AuthenticationProvider,
    internal val nativePointer: NativePointer
) : Credentials {

    override val authenticationProvider: AuthenticationProvider = authenticationProvider
    get() {
        return if (field == AuthenticationProvider.fromId(RealmInterop.realm_auth_credentials_get_provider(nativePointer))) {
            field
        } else {
            error("Underlying authentication provider differs from local one")
        }
    }

    companion object {
        internal fun anonymous(): NativePointer {
            return RealmInterop.realm_app_credentials_new_anonymous()
        }

        internal fun emailPassword(email: String, password: String): NativePointer {
            return RealmInterop.realm_app_credentials_new_username_password(
                io.realm.internal.util.Validation.checkEmpty(email, "email"),
                io.realm.internal.util.Validation.checkEmpty(password, "password")
            )
        }
    }
}
