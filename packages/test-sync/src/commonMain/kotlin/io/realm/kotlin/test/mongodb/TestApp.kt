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
@file:OptIn(ExperimentalKBsonSerializerApi::class, ExperimentalRealmSerializerApi::class)

package io.realm.kotlin.test.mongodb

import io.realm.kotlin.annotations.ExperimentalRealmSerializerApi
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.SynchronizableObject
import io.realm.kotlin.internal.interop.sync.NetworkTransport
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.internal.platform.singleThreadDispatcher
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.AppConfiguration
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.internal.AppConfigurationImpl
import io.realm.kotlin.test.mongodb.util.AppAdmin
import io.realm.kotlin.test.mongodb.util.AppAdminImpl
import io.realm.kotlin.test.mongodb.util.AppInitializer
import io.realm.kotlin.test.mongodb.util.AppServicesClient
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TestHelper
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson

val TEST_APP_PARTITION = syncServerAppName("pbs") // With Partition-based Sync
val TEST_APP_FLEX = syncServerAppName("flx") // With Flexible Sync
val TEST_APP_CLUSTER_NAME = SyncServerConfig.clusterName

val TEST_SERVER_BASE_URL = baasTestUrl()
const val DEFAULT_PASSWORD = "password1234"

expect fun baasTestUrl(): String

// Expose a try-with-resource pattern for Test Apps
inline fun App.use(action: (App) -> Unit) {
    try {
        action(this)
    } finally {
        this.close()
    }
}

/**
 * This class merges the classes [App] and [AppAdmin] making it easier to create an App that can be
 * used for testing.
 *
 * @param app gives access to the [App] class delegate for testing purposes
 * @param debug enable trace of command server and rest api calls in the test app.
 */
open class TestApp private constructor(
    private val dispatcher: CoroutineDispatcher,
    pairAdminApp: Pair<App, AppAdmin>
) : App by pairAdminApp.first, AppAdmin by pairAdminApp.second {

    var mutex = SynchronizableObject()
    var isClosed: Boolean = false
    val app: App = pairAdminApp.first

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
    @OptIn(ExperimentalKBsonSerializerApi::class, ExperimentalCoroutinesApi::class)
    constructor(
        testId: String?,
        appInitializer: AppInitializer,
        dispatcher: CoroutineDispatcher = singleThreadDispatcher("$testId-dispatcher"),
        builder: (AppConfiguration.Builder) -> AppConfiguration.Builder = {
            it.syncRootDirectory(PlatformUtils.createTempDir("${appInitializer.name}-$testId"))
        },
        debug: Boolean = false,
        networkTransport: NetworkTransport? = null,
        ejson: EJson = EJson,
    ) : this(
        dispatcher,
        build(
            debug = debug,
            appInitializer = appInitializer,
            dispatcher = dispatcher,
            builder = builder,
            networkTransport = networkTransport,
            ejson = ejson,
        )
    )

    fun createUserAndLogin(): User = runBlocking {
        val (email, password) = TestHelper.randomEmail() to "password1234"
        emailPasswordAuth.registerUser(email, password).run {
            logIn(email, password)
        }
    }

    override fun close() {
        mutex.withLock {
            if (isClosed) {
                return
            }

            app.sync.waitForSessionsToTerminate()

            // This is needed to "properly reset" all sessions across tests since deleting users
            // directly using the REST API doesn't do the trick
            runBlocking {
                try {
                    while (currentUser != null) {
                        (currentUser as User).logOut()
                    }
                    deleteAllUsers()
                } catch (ex: Exception) {
                    // Some tests might render the server inaccessible, preventing us from
                    // deleting users. Assume those tests know what they are doing and
                    // ignore errors here.
                    (configuration as AppConfigurationImpl).logger.warn("Server side users could not be deleted: $ex")
                }
            }

            app.close()

            // Tearing down the SyncSession still relies on the the event loop (powered by the coroutines) of the platform networking
            //  to post Function Handler, so we need to close it after we close the App
            @OptIn(ExperimentalCoroutinesApi::class)
            if (dispatcher is CloseableCoroutineDispatcher) {
                dispatcher.close()
            }

            // Close network client resources
            closeClient()

            // Make sure to clear cached apps before deleting files
            RealmInterop.realm_clear_cached_apps()

            // Delete metadata Realm files
            PlatformUtils.deleteTempDir("${configuration.syncRootDirectory}/mongodb-realm")
            isClosed = true
        }
    }

    companion object {

        @Suppress("LongParameterList")
        fun build(
            debug: Boolean,
            appInitializer: AppInitializer,
            dispatcher: CoroutineDispatcher,
            builder: (AppConfiguration.Builder) -> AppConfiguration.Builder,
            networkTransport: NetworkTransport?,
            ejson: EJson,
        ): Pair<App, AppAdmin> {
            val appAdmin: AppAdmin = runBlocking(dispatcher) {
                AppServicesClient.build(
                    baseUrl = TEST_SERVER_BASE_URL,
                    debug = debug,
                    dispatcher = dispatcher
                ).run {
                    val baasApp = getOrCreateApp(appInitializer)

                    AppAdminImpl(this, baasApp)
                }
            }

            @Suppress("invisible_member", "invisible_reference")
            val config = AppConfiguration.Builder(appAdmin.clientAppId)
                .baseUrl(TEST_SERVER_BASE_URL)
                .networkTransport(networkTransport)
                .ejson(ejson)
                .apply {
                    if (SyncServerConfig.usePlatformNetworking) {
                        usePlatformNetworking()
                    }
                }

            val app = App.create(
                builder(config)
                    .dispatcher(dispatcher)
                    .build()
            )

            return Pair<App, AppAdmin>(app, appAdmin)
        }
    }
}

val App.asTestApp: TestApp
    get() = this as TestApp

suspend fun App.createUserAndLogIn(
    email: String = TestHelper.randomEmail(),
    password: String = DEFAULT_PASSWORD
): User {
    return this.emailPasswordAuth.registerUser(email, password).run {
        logIn(email, password)
    }
}

suspend fun App.logIn(email: String, password: String): User =
    this.login(Credentials.emailPassword(email, password))

fun syncServerAppName(appNameSuffix: String): String {
    return "${SyncServerConfig.appPrefix}-$appNameSuffix"
}
