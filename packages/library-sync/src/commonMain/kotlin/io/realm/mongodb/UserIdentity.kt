/*
 * Copyright 2020 Realm Inc.
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

/**
 * Each [io.realm.mongodb.User] is represented by one or more identities, each defined by an
 * [Credentials.Provider].
 *
 * This class represents one identity, defined by a specific provider.
 */
interface UserIdentity {
    /**
     * The unique identifier for this identity.
     */
    val id: String

    /**
     * The provider defining this identity.
     */
    val provider: Credentials.Provider
}
