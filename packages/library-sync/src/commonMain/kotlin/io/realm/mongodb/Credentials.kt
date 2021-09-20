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

package io.realm.mongodb

import io.realm.mongodb.internal.CredentialImpl
import io.realm.mongodb.internal.ProviderImpl

/**
 * TODO
 */
sealed interface Credentials {
    sealed interface Provider {
        val id: String
    }
    class ANONYMOUS : Provider, ProviderImpl("anonymous")
    class EMAIL_PASSWORD : Provider, ProviderImpl("local-userpass")

    val provider: Provider

    class Anonymous() : Credentials, CredentialImpl(ANONYMOUS(), anonymous())
    class EmailPassword(val email: String, val password: String) : Credentials, CredentialImpl(EMAIL_PASSWORD(), emailPassword(email, password))
}

/**
 * TODO
 */
//class EmailPassword(val email: String, val password: String) : Credentials, CredentialImpl<EmailPassword>()


//class Anonymous : Credentials("anon-user")
//    class ApiKey : Credentials("api-key")
//    class Apple : Credentials("oauth2-apple")
//    class CustomFunction : Credentials("custom-function")
//    class Facebook : Credentials("oauth2-facebook")
//    class Google : Credentials("oauth2-google")
//    class Jwt : Credentials("jwt")
//    class Unknown : Credentials("")
