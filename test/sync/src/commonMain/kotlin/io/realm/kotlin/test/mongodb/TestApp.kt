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

package io.realm.kotlin.test.mongodb

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.internal.platform.singleThreadDispatcher
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.AppConfiguration
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.test.mongodb.util.AdminApi
import io.realm.kotlin.test.mongodb.util.AdminApiImpl
import io.realm.kotlin.test.mongodb.util.defaultClient
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TestHelper
import kotlinx.coroutines.CoroutineDispatcher

const val COMMAND_SERVER_BASE_URL = "http://127.0.0.1:8888"
const val TEST_SERVER_BASE_URL = "http://127.0.0.1:9090"
const val TEST_APP_1 = "testapp1" // With Partion-based Sync
const val TEST_APP_2 = "testapp2" // Copy of Test App 1
const val TEST_APP_FLEX = "testapp3" // With Flexible Sync

/**
 * This class merges the classes `App` and `AdminApi` making it easier to create an App that can be
 * used for testing.
 *
 * @param app gives access to the [App] class delegate for testing purposes
 * @param debug enable trace of command server and rest api calls in the test app.
 */
class TestApp private constructor(
    val app: App,
    dispatcher: CoroutineDispatcher = singleThreadDispatcher("test-app-dispatcher"),
    debug: Boolean = false
) : App by app,
    AdminApi by (runBlocking(dispatcher) { AdminApiImpl(TEST_SERVER_BASE_URL, app.configuration.appId, debug, dispatcher) }) {

    /**
     * Creates an [App] with the given configuration parameters.
     *
     * @param logLevel log level used to prime the AppConfiguration.Builder.
     * @param builder the builder used to build the final app. The builder is already primed with the
     * default test app configuration, but can be used to override the defaults and add additional
     * options.
     * @param debug enable trace of command server and rest api calls in the test app.
     **/
    @Suppress("LongParameterList")
    constructor(
        appName: String = TEST_APP_1,
        dispatcher: CoroutineDispatcher = singleThreadDispatcher("test-app-dispatcher"),
        appId: String = runBlocking(dispatcher) { getAppId(appName, debug) },
        logLevel: LogLevel = LogLevel.WARN,
        builder: (AppConfiguration.Builder) -> AppConfiguration.Builder = { it },
        debug: Boolean = false,
        customLogger: RealmLogger? = null
    ) : this(
        App.create(
            builder(testAppConfigurationBuilder(appId, logLevel, customLogger))
                .dispatcher(dispatcher)
                .build()
        ),
        dispatcher,
        debug
    )

    public fun createUserAndLogin(): User = runBlocking {
        val (email, password) = TestHelper.randomEmail() to "password1234"
        app.emailPasswordAuth.registerUser(email, password).run {
            logIn(email, password)
        }
    }

    fun close() {
        // This is needed to "properly reset" all sessions across tests since deleting users
        // directly using the REST API doesn't do the trick
        runBlocking {
            while (currentUser != null) {
                currentUser.logOut()
            }
            deleteAllUsers()
        }

        // Close network client resources
        closeClient()

        // Make sure to clear cached apps before deleting files
        RealmInterop.realm_clear_cached_apps()

        // Delete metadata Realm files
        PlatformUtils.deleteTempDir("${this.app.configuration.syncRootDirectory}/mongodb-realm")
    }

    companion object {
        suspend fun getAppId(appName: String, debug: Boolean): String {
            val client = defaultClient("$appName-initializer", debug)
            return client.get("$COMMAND_SERVER_BASE_URL/$appName").also {
                client.close()
            }.bodyAsText()
        }

        fun testAppConfigurationBuilder(
            appName: String,
            logLevel: LogLevel,
            customLogger: RealmLogger?
        ): AppConfiguration.Builder {
            return AppConfiguration.Builder(appName)
                .baseUrl(TEST_SERVER_BASE_URL)
                .log(
                    logLevel,
                    if (customLogger == null) emptyList<RealmLogger>()
                    else listOf<RealmLogger>(customLogger)
                )
        }
    }
}

val App.asTestApp: TestApp
    get() = this as TestApp

suspend fun App.createUserAndLogIn(email: String, password: String): User {
    return this.emailPasswordAuth.registerUser(email, password).run {
        logIn(email, password)
    }
}

suspend fun App.logIn(email: String, password: String): User =
    this.login(Credentials.emailPassword(email, password))
