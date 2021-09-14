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

import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop

/**
 * TODO
 */
// FIXME Revisit API
//  https://github.com/realm/realm-kotlin/pull/447#discussion_r707345579
sealed class Credentials(val id: String) {

    // FIXME Internalize. Hmm, implementation of sealed classes must be in same package
    val nativePointer: NativePointer by lazy {
        when (this) {
            is EmailPassword ->
                RealmInterop.realm_app_credentials_new_username_password(email, password)
        }
    }
}

/**
 * TODO
 */
class EmailPassword(val email: String, val password: String) : Credentials("local-userpass")

//    class Anonymous : Credentials("anon-user")
//    class ApiKey : Credentials("api-key")
//    class Apple : Credentials("oauth2-apple")
//    class CustomFunction : Credentials("custom-function")
//    class Facebook : Credentials("oauth2-facebook")
//    class Google : Credentials("oauth2-google")
//    class Jwt : Credentials("jwt")
//    class Unknown : Credentials("")
