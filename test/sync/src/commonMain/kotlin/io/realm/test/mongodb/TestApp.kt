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

import io.ktor.client.request.get
import io.realm.internal.platform.runBlocking
import io.realm.internal.platform.singleThreadDispatcher
import io.realm.log.LogLevel
import io.realm.mongodb.App
import io.realm.mongodb.AppConfiguration
import io.realm.mongodb.Credentials
import io.realm.mongodb.User
import io.realm.test.mongodb.util.AdminApi
import io.realm.test.mongodb.util.AdminApiImpl
import io.realm.test.mongodb.util.defaultClient
import kotlinx.coroutines.CoroutineDispatcher

const val COMMAND_SERVER_BASE_URL = "http://127.0.0.1:8888"
const val TEST_SERVER_BASE_URL = "http://127.0.0.1:9090"
const val TEST_APP_1 = "testapp1" // Id for the default test app

/**
 * This class merges the classes `App` and `AdminApi` making it easier to create an App that can be
 * used for testing.
 *
 * @param logLevel log level used to prime the AppConfiguration.Builder.
 * @param builder the builder used to build the final app. The builder is already primed with the
 * default test app configuration, but can be used to override the defaults and add additional
 * options.
 * @param debug enable trace of command server and rest api calls in the test app.
 */
class TestApp(
    appName: String = TEST_APP_1,
    dispatcher: CoroutineDispatcher = singleThreadDispatcher("test-app-dispatcher"),
    appId: String = runBlocking(dispatcher) { getAppId(appName, debug) },
    logLevel: LogLevel = LogLevel.WARN,
    builder: (AppConfiguration.Builder) -> AppConfiguration.Builder = { it },
    debug: Boolean = false
) : App by App.create(builder(testAppConfigurationBuilder(appId, logLevel)).dispatcher(dispatcher).build()),
    AdminApi by (runBlocking(dispatcher) { AdminApiImpl(TEST_SERVER_BASE_URL, appId, debug, dispatcher) }) {

    fun close() {
        deleteAllUsers()
    }

    suspend fun createUserAndLogIn(email: String, password: String): User =
        createUser(email, password).run { logIn(email, password) }

    suspend fun createUser(email: String, password: String) =
        this.emailPasswordAuth.registerUser(email, password)

    suspend fun logIn(email: String, password: String): User =
        this.login(Credentials.emailPassword(email, password))

    companion object {
        suspend fun getAppId(appName: String, debug: Boolean): String {
            return defaultClient("$appName-initializer", debug).get("$COMMAND_SERVER_BASE_URL/$appName")
        }

        fun testAppConfigurationBuilder(appName: String, logLevel: LogLevel): AppConfiguration.Builder {
            return AppConfiguration.Builder(appName)
                .baseUrl(TEST_SERVER_BASE_URL)
                .log(logLevel)
        }
    }
}

val App.asTestApp: TestApp
    get() = this as TestApp
