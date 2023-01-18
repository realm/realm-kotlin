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

package io.realm.kotlin.mongodb.ext

import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.internal.UserImpl
import org.mongodb.kbson.BsonDocument

/**
 * Returns the profile for this user as [BsonDocument].
 *
 * @return The profile for this user.
 */
public inline fun User.profileAsBsonDocument(): BsonDocument =
    (this as UserImpl).profileAsBsonDocumentInternal()

/**
 * Return the custom user data associated with the user in the Realm App as [BsonDocument].
 *
 * The data is only refreshed when the user's access token is refreshed or when explicitly
 * calling [User.refreshCustomData].
 *
 * @return The custom user data associated with the user.
 */
public inline fun User.customDataAsBsonDocument(): BsonDocument? =
    (this as UserImpl).customDataAsBsonDocumentInternal()
