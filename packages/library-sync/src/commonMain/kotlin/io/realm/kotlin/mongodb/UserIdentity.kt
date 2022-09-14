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
 * Each User is represented by 1 or more identities each defined by an
 * [AuthenticationProvider].
 *
 * This class represents the identity defined by a specific provider.
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
