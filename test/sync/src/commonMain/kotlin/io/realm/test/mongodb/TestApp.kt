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

@file:Suppress("invisible_member", "invisible_reference")
package io.realm.test.mongodb

import io.realm.internal.platform.singleThreadDispatcher
import io.realm.mongodb.App
import io.realm.mongodb.appConfigurationOf
import io.realm.mongodb.internal.KtorNetworkTransport
import kotlinx.coroutines.CoroutineDispatcher

/**
 * This class wraps various methods making it easier to create an App that can be used
 * for testing.
 */
const val BASE_URL = "http://127.0.0.1:9090"
const val TEST_APP_1 = "testapp1"       // Id for the default test app

// TODO Find appropriate configuration options
class TestApp(
    appName: String = TEST_APP_1,
    dispatcher: CoroutineDispatcher = singleThreadDispatcher("test-app-dispatcher")
) : App by App.create(appConfigurationOf(getAppId(appName, dispatcher), BASE_URL, dispatcher)) {

    companion object {
        private fun getAppId(appName: String, dispatcher: CoroutineDispatcher): String {
            val networkTransport: io.realm.internal.interop.sync.NetworkTransport =
                KtorNetworkTransport(
                    timeoutMs = 5000,
                    dispatcher = dispatcher
                )
            return networkTransport
                .sendRequest(
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
        }
    }
}
