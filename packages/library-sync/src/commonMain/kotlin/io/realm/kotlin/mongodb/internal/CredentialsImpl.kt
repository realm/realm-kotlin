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

import io.realm.kotlin.internal.interop.RealmCredentialsPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.util.Validation
import io.realm.kotlin.mongodb.AuthenticationProvider
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.GoogleAuthType
import org.mongodb.kbson.BsonType
import org.mongodb.kbson.BsonValue
import org.mongodb.kbson.serialization.Bson

internal class CredentialsImpl constructor(
    internal val nativePointer: RealmCredentialsPointer
) : Credentials {

    override val authenticationProvider: AuthenticationProvider =
        AuthenticationProviderImpl.fromId(
            RealmInterop.realm_auth_credentials_get_provider(nativePointer)
        )

    internal fun asJson(): String {
        return RealmInterop.realm_app_credentials_serialize_as_json(nativePointer)
    }

    companion object {
        internal fun anonymous(reuseExisting: Boolean): RealmCredentialsPointer =
            RealmInterop.realm_app_credentials_new_anonymous(reuseExisting)

        internal fun emailPassword(email: String, password: String): RealmCredentialsPointer =
            RealmInterop.realm_app_credentials_new_email_password(
                Validation.checkEmpty(email, "email"),
                Validation.checkEmpty(password, "password")
            )

        internal fun apiKey(key: String): RealmCredentialsPointer =
            RealmInterop.realm_app_credentials_new_api_key(Validation.checkEmpty(key, "key"))

        internal fun apple(idToken: String): RealmCredentialsPointer =
            RealmInterop.realm_app_credentials_new_apple(Validation.checkEmpty(idToken, "idToken"))

        internal fun facebook(accessToken: String): RealmCredentialsPointer =
            RealmInterop.realm_app_credentials_new_facebook(Validation.checkEmpty(accessToken, "accessToken"))

        internal fun google(token: String, type: GoogleAuthType): RealmCredentialsPointer {
            Validation.checkEmpty(token, "token")
            return when (type) {
                GoogleAuthType.AUTH_CODE -> RealmInterop.realm_app_credentials_new_google_auth_code(token)
                GoogleAuthType.ID_TOKEN -> RealmInterop.realm_app_credentials_new_google_id_token(token)
            }
        }

        internal fun jwt(jwtToken: String): RealmCredentialsPointer =
            RealmInterop.realm_app_credentials_new_jwt(Validation.checkEmpty(jwtToken, "jwtToken"))

        internal fun customFunction(payload: Any): RealmCredentialsPointer =
            BsonEncoder.encodeToBsonValue(payload).let { bsonValue: BsonValue ->
                require(bsonValue.bsonType == BsonType.DOCUMENT) {
                    "Invalid payload type '${payload::class.simpleName}', only BsonDocument and maps are supported."
                }
                RealmInterop.realm_app_credentials_new_custom_function(Bson.toJson(bsonValue))
            }
    }
}
