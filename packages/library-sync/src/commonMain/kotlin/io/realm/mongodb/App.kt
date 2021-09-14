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

import io.realm.mongodb.internal.AppConfigurationImpl
import io.realm.mongodb.internal.AppImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * TODO
 */
interface App {

    val configuration: AppConfiguration

    /**
     * TODO
     */
    // FIXME Reevaluate Result api to surface App errors more explicitly
    //  https://github.com/realm/realm-kotlin/pull/447#discussion_r707344044
    suspend fun login(credentials: Credentials): Result<User>

    companion object {
        /**
         * TODO
         */
        fun create(
            configuration: AppConfiguration,
        ): App = AppImpl(configuration as AppConfigurationImpl)
    }
}

