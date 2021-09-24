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

import io.realm.internal.platform.singleThreadDispatcher
import io.realm.mongodb.internal.AppConfigurationImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * An **AppConfiguration** is used to setup linkage to a MongoDB Realm application.
 *
 * Instances of a AppConfiguration can only created by using the
 * {@link AppConfiguration.Builder} and calling its
 * {@link AppConfiguration.Builder#build()} method.
 * <p>
 * Configuring a App is only required if the default settings are not enough. Otherwise calling
 * {@code App("app-id")} is sufficient.
 */
interface AppConfiguration {

    val appId: String
    val baseUrl: String
    val networkTransportDispatcher: CoroutineDispatcher

    companion object {
        /**
         * The default url for MongoDB Realm applications.
         *
         * @see Builder#baseUrl(String)
         */
        const val DEFAULT_BASE_URL = "https://realm.mongodb.com"

        /**
         * The default header name used to carry authorization data when making network requests
         * towards MongoDB Realm.
         */
        const val DEFAULT_AUTHORIZATION_HEADER_NAME = "Authorization"

    }

    /**
     * Builder used to construct instances of a {@link AppConfiguration} in a fluent manner.
     */
    class Builder(
        val appId: String
    ) {
        private var baseUrl: String = DEFAULT_BASE_URL
        private var dispatcher: CoroutineDispatcher = singleThreadDispatcher("dispatcher-$appId") // TODO

        /**
         * Sets the base url for the MongoDB Realm Application. The default value is
         * {@link #DEFAULT_BASE_URL}.
         *
         * @param baseUrl the base url for the MongoDB Realm application.
         */
        fun baseUrl(url: String) = apply { this.baseUrl = url }

        /**
         * TODO
         */
        fun dispatcher(dispatcher: CoroutineDispatcher) = apply { this.dispatcher = dispatcher }

        /**
         * Creates the AppConfiguration from the properties of the builder.
         *
         * @return the AppConfiguration that can be used to create a {@link App}.
         */
        fun build(): AppConfiguration = AppConfigurationImpl(
            appId = appId,
            baseUrl = baseUrl,
            networkTransportDispatcher = dispatcher
        )
    }
}
