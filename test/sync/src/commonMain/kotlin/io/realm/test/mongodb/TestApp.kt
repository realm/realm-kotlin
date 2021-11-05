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
import io.realm.internal.platform.appFilesDirectory
import io.realm.internal.platform.runBlocking
import io.realm.internal.platform.singleThreadDispatcher
import io.realm.log.LogLevel
import io.realm.mongodb.App
import io.realm.mongodb.AppConfiguration
import io.realm.test.mongodb.util.AdminApi
import io.realm.test.mongodb.util.AdminApiImpl
import io.realm.test.mongodb.util.defaultClient
import kotlinx.coroutines.CoroutineDispatcher
import okio.FileSystem
import okio.Path.Companion.toPath

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
 * @param fileSystem platform-dependent entry point to allow file cleanup after the test.
 * @param debug enable trace of command server and rest api calls in the test app.
 */
@Suppress("LongParameterList")
class TestApp(
    appName: String = TEST_APP_1,
    dispatcher: CoroutineDispatcher = singleThreadDispatcher("test-app-dispatcher"),
    appId: String = runBlocking(dispatcher) { getAppId(appName, debug) },
    logLevel: LogLevel = LogLevel.WARN,
    builder: (AppConfiguration.Builder) -> AppConfiguration.Builder = { it },
    private val fileSystem: FileSystem,
    debug: Boolean = false
) : App by App.create(builder(testAppConfigurationBuilder(appId, logLevel)).dispatcher(dispatcher).build()),
    AdminApi by (runBlocking(dispatcher) { AdminApiImpl(TEST_SERVER_BASE_URL, appId, debug, dispatcher) }) {

    fun close() {
        deleteAllUsers()

        // Delete metadata Realm files
        fileSystem.deleteRecursively((appFilesDirectory() + "/mongodb-realm").toPath())
    }

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
