/*
 * Copyright 2022 Realm Inc.
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
package io.realm.kotlin.mongodb

/**
 * Each [User] on Atlas App Services is uniquely identified by their [User.id], but this id cannot
 * be used across multiple authentication providers, as they all have their own notation on what
 * defines a user. This class thus represents a users identity towards one single authentication
 * provider.
 *
 * A single [User] on App Services can have multiple user identities, one towards each
 * authentication provider, e.g. an example would an app user that can log in using either
 * a custom email account or a Google account.
 *
 * The list of all user identities associated with an App Services user can be found through
 * [User.identities]. It is possible to add more user identities through [User.linkCredentials].
 */
public data class UserIdentity(
    /**
     * A unique identifier for this identity. The identifier is only unique for the given
     * [provider].
     */
    val id: String,
    /**
     * The provider responsible for defining and managing this identity.
     */
    val provider: AuthenticationProvider
)
