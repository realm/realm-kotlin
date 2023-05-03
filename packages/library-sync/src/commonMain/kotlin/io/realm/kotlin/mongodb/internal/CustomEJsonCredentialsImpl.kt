/*
 * Copyright 2023 Realm Inc.
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

import io.realm.kotlin.internal.interop.RealmCredentialsPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.mongodb.AuthenticationProvider
import io.realm.kotlin.mongodb.Credentials

/**
 * Credentials for a EJson payload. It solved the issue where payload serialization requires access to
 * the app instance. This credentials implementation is late evaluated when the credentials are used by
 * the app, then the app instance is passed to the lambda block that serializes the payload.
 */
@PublishedApi
internal class CustomEJsonCredentialsImpl constructor(
    val serializeAsEJson: (appImpl: AppImpl) -> String
) : Credentials {
    override val authenticationProvider: AuthenticationProvider =
        AuthenticationProvider.CUSTOM_FUNCTION

    fun nativePointer(appImpl: AppImpl): RealmCredentialsPointer =
        serializeAsEJson(appImpl).let { serializedEjsonPayload ->
            RealmInterop.realm_app_credentials_new_custom_function(serializedEjsonPayload)
        }

    internal fun asJson(appImpl: AppImpl): String {
        return RealmInterop.realm_app_credentials_serialize_as_json(nativePointer(appImpl))
    }
}
