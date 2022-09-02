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
import io.realm.kotlin.test.mongodb.util.BaasClient
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TestHelper
import kotlinx.coroutines.CoroutineDispatcher

const val TESTAPP_PARTITION = "testapp-partition" // With Partion-based Sync
const val TESTAPP_FLEX = "testapp-flex" // With Flexible Sync

const val TEST_SERVER_BASE_URL = "http://127.0.0.1:9090"

/**
 * This class merges the classes `App` and `AdminApi` making it easier to create an App that can be
 * used for testing.
 *
 * @param app gives access to the [App] class delegate for testing purposes
 * @param debug enable trace of command server and rest api calls in the test app.
 */
open class TestApp private constructor(
    private val appBuilder: TestAppBuilder,
    dispatcher: CoroutineDispatcher = singleThreadDispatcher("test-app-dispatcher"),
    debug: Boolean = false
) : App by appBuilder.app, AdminApi by appBuilder.adminApi {

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
        appName: String,
        dispatcher: CoroutineDispatcher = singleThreadDispatcher("test-app-dispatcher"),
        logLevel: LogLevel = LogLevel.WARN,
        builder: (AppConfiguration.Builder) -> AppConfiguration.Builder = { it },
        debug: Boolean = false,
        customLogger: RealmLogger? = null,
    ) : this(
        TestAppBuilder(
            baasClient = BaasClient(
                baseUrl = TEST_SERVER_BASE_URL,
                debug = debug,
                dispatcher = dispatcher
            ),
            appName = appName,
            logLevel = logLevel,
            customLogger = customLogger,
            dispatcher = dispatcher,
            builder = builder
        ),
        dispatcher,
        debug
    )

    val app: App = appBuilder.app

    public fun createUserAndLogin(): User = runBlocking {
        val (email, password) = TestHelper.randomEmail() to "password1234"
        appBuilder.app.emailPasswordAuth.registerUser(email, password).run {
            logIn(email, password)
        }
    }

    fun close() {
        // This is needed to "properly reset" all sessions across tests since deleting users
        // directly using the REST API doesn't do the trick
        runBlocking {
            while (app.currentUser != null) {
                (app.currentUser as User).logOut()
            }
            deleteAllUsers()
        }

        // Close network client resources
        closeClient()

        // Make sure to clear cached apps before deleting files
        RealmInterop.realm_clear_cached_apps()

        // Delete metadata Realm files
        PlatformUtils.deleteTempDir("${appBuilder.app.configuration.syncRootDirectory}/mongodb-realm")
    }
}

class TestAppBuilder(
    val baasClient: BaasClient,
    val appName: String,
    val logLevel: LogLevel,
    val customLogger: RealmLogger?,
    val dispatcher: CoroutineDispatcher,
    val builder: (AppConfiguration.Builder) -> AppConfiguration.Builder,
) {
    val adminApi: AdminApi
    val app: App

    init {
        val baasApp = baasClient.getApp(appName)
        adminApi = AdminApiImpl(baasClient, baasApp)

        val config = AppConfiguration.Builder(baasApp.clientAppId)
            .baseUrl(TEST_SERVER_BASE_URL)
            .log(
                logLevel,
                if (customLogger == null) emptyList<RealmLogger>()
                else listOf<RealmLogger>(customLogger)
            )

        app = App.create(
            builder(config)
                .dispatcher(dispatcher)
                .build()
        )
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
