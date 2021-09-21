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
import io.realm.mongodb.internal.Provider

/**
 * TODO
 */
interface Credentials {

    companion object {
        /**
         * TODO
         */
        fun anynomous(): Credentials {
            return CredentialImpl(Provider.ANONYMOUS, CredentialImpl.anonymous())
        }

        /**
         * TODO
         */
        fun emailPassword(email: String, password: String): Credentials {
            return CredentialImpl(Provider.EMAIL_PASSWORD, CredentialImpl.emailPassword(email, password))
        }
    }
}
