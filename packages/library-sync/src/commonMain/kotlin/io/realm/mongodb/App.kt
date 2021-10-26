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

import io.realm.internal.util.Validation
import io.realm.mongodb.internal.AppConfigurationImpl
import io.realm.mongodb.internal.AppImpl

/**
 * An **App** is the main client-side entry point for interacting with a **MongoDB Realm App**.
 *
 * The **App** can be used to:
 * - Register and authenticate users.
 * - Synchronize data between the local device and a backend Realm App with synchronized realms.
 *
 * To create an app that is linked with a remote **Realm App**, initialize Realm and configure the
 * **App** as shown below:
 *
 * ```
 *     class MyRealmAppClass {
 *         val configuration: AppConfiguration = AppConfiguration.Builder(<APP_ID>).build()
 *         val app: App = App.create(configuration)
 *         val realm: Realm
 *
 *         init {
 *              realm = runBlocking {
 *                  val user = app.login(Credentials.anonymous())
 *                  val config = SyncConfiguration.Builder(
 *                      schema = setOf(YourRealmObject::class),
 *                      user = user,
 *                      partitionValue = "my-partition"
 *                  ).build()
 *
 *                  Realm.open(config)
 *              }
 *         }
 *     }
 * ```
 */
interface App {

    val configuration: AppConfiguration

    /**
     * Log in as a user with the given credentials associated with an authentication provider.
     *
     * @param credentials the credentials representing the type of login.
     * @return the logged in [User].
     * @throws AppException if the user could not be logged in.
     */
    suspend fun login(credentials: Credentials): User

    companion object {
        /**
         * Create an [App] with default settings.
         * @param appId The MongoDB Realm App ID.
         */
        fun create(appId: String): App {
            Validation.checkEmpty(appId, "appId")
            return create(AppConfiguration.Builder(appId).build())
        }

        /**
         * Create an [App] according to the given [AppConfiguration].
         *
         * @param configuration The configuration to use for this [App] instance.
         * @see AppConfiguration.Builder
         */
        fun create(configuration: AppConfiguration): App =
            AppImpl(configuration as AppConfigurationImpl)
    }
}
