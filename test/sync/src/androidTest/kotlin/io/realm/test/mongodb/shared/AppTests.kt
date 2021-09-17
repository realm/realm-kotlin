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

package io.realm.test.mongodb.shared

import io.realm.internal.platform.singleThreadDispatcher
import io.realm.mongodb.App
import io.realm.mongodb.AppConfiguration
import io.realm.mongodb.EmailPassword
import io.realm.mongodb.appConfigurationOf
import io.realm.mongodb.internal.KtorNetworkTransport
import kotlinx.coroutines.runBlocking
import kotlin.test.Ignore
import kotlin.test.Test

const val TEST_APP_1 = "testapp1" // Id for the default test app
const val BASE_URL = "http://127.0.0.1:9090"

// Cannot run on CI yet, as it requires sync server to be started with
// tools/sync_test_server/start_server.sh and manual creation of a user "asdf@asdf.com"/"asdfasdf"
// through the web ui
class AppTests {

    @Test
    fun login() {
        // Send request directly to the local server to get the actual app ID
        // TODO Wrap test app setup as in Realm Java
        //  https://github.com/realm/realm-kotlin/pull/447#discussion_r707350138
        val applicationId = KtorNetworkTransport(
            timeoutMs = 5000,
            dispatcher = singleThreadDispatcher("transport dispatcher")
        ).sendRequest(
            "get",
            "http://127.0.0.1:8888/$TEST_APP_1",
            mapOf(),
            "",
            true
        ).let { response ->
            when (response.httpResponseCode) {
                200 -> response.body
                else -> throw IllegalStateException(response.toString())
            }
        }
        assertTrue(application.startsWith("testapp1"))
//        val configuration: AppConfiguration = appConfigurationOf(applicationId, BASE_URL, singleThreadDispatcher("asdf"))
//        val app = App.create(configuration)
//
//        runBlocking {
//            app.login(EmailPassword("asdf@asdf.com", "asdfasdf")).getOrThrow()
//        }
    }
}
