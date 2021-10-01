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
 * TODO
 */
interface App {

    val configuration: AppConfiguration

    /**
     * TODO
     */
    // FIXME Reevaluate Result api to surface App errors more explicitly
    //  https://github.com/realm/realm-kotlin/pull/447#discussion_r707344044
    //  https://github.com/realm/realm-kotlin/issues/241
    suspend fun login(credentials: Credentials): User

    companion object {
        /**
         * Create an [App] with default settings.
         * @return
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
