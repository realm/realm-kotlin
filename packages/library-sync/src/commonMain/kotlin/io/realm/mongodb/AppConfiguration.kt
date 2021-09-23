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

import io.realm.internal.platform.multiThreadDispatcher
import io.realm.mongodb.internal.AppConfigurationImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * TODO
 */
interface AppConfiguration {

    val appId: String
    val baseUrl: String
    val networkTransportDispatcher: CoroutineDispatcher

    companion object {
        const val DEFAULT_BASE_URL = "https://realm.mongodb.com"
        const val DEFAULT_AUTHORIZATION_HEADER_NAME = "Authorization"
    }

    /**
     * TODO
     */
    class Builder(
        private val appId: String
    ) {

        private var baseUrl: String = DEFAULT_BASE_URL
        private var dispatcher: CoroutineDispatcher = multiThreadDispatcher() // TODO

        /**
         * TODO
         */
        fun baseUrl(url: String) = apply { this.baseUrl = url }

        /**
         * TODO
         */
        fun dispatcher(dispatcher: CoroutineDispatcher) = apply { this.dispatcher = dispatcher }

        /**
         * TODO
         */
        fun build(): AppConfiguration = AppConfigurationImpl(
            appId = appId,
            baseUrl = baseUrl,
            networkTransportDispatcher = dispatcher
        )
    }
}
