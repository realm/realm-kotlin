@file:Suppress("invisible_member", "invisible_reference") // Needed to call session.simulateError()
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

package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.internal.platform.appFilesDirectory
import io.realm.kotlin.internal.platform.pathOf
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLog
import io.realm.kotlin.log.RealmLogger
import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.AppConfiguration
import io.realm.kotlin.mongodb.exceptions.ServiceException
import io.realm.kotlin.mongodb.internal.AppConfigurationImpl
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.common.utils.assertFailsWithMessage
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.mongodb.use
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.receiveOrFail
import kotlinx.coroutines.channels.Channel
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val CUSTOM_HEADER_NAME = "Foo"
private const val CUSTOM_HEADER_VALUE = "bar"
private const val AUTH_HEADER_NAME = "RealmAuth"

private const val APP_ID = "app-id"

class AppConfigurationTests {

//    val looperThread = BlockingLooperThread()
//
//    @get:Rule
//    val tempFolder = TemporaryFolder()

    @Test
    fun authorizationHeaderName() {
        val config1 = AppConfiguration.Builder(APP_ID).build()
        assertEquals("Authorization", config1.authorizationHeaderName)

        val config2 = AppConfiguration.Builder(APP_ID)
            .authorizationHeaderName("CustomAuth")
            .build()
        assertEquals("CustomAuth", config2.authorizationHeaderName)

        val builder = AppConfiguration.Builder(APP_ID)

        assertFailsWithMessage<IllegalArgumentException>("Non-empty 'name' required.") {
            builder.authorizationHeaderName("")
        }
    }

    @Test
    fun addCustomRequestHeader() {
        val config = AppConfiguration.Builder(APP_ID)
            .customRequestHeaders {
                putAll(
                    mapOf(
                        "h0" to "v0",
                        "h1" to "v1",
                    )
                )
                put("h2", "v2")
            }
            .build()

        config.customRequestHeaders.let { headers ->
            assertEquals(3, headers.size)
            repeat(3) { index ->
                assertTrue(headers.any { it.key == "h$index" && it.value == "v$index" })
            }
        }

        // Accept empty values
        AppConfiguration.Builder(APP_ID).apply {
            customRequestHeaders {
                put("header1", "")
                putAll(mapOf("header1" to ""))
            }
        }

        // Fail if empty header name
        AppConfiguration.Builder(APP_ID).apply {
            assertFailsWithMessage<IllegalArgumentException>("Non-empty custom header name required.") {
                customRequestHeaders {
                    put("", "value")
                }
            }
        }

        AppConfiguration.Builder(APP_ID).apply {
            assertFailsWithMessage<IllegalArgumentException>("Non-empty custom header name required.") {
                customRequestHeaders {
                    putAll(mapOf("" to "value"))
                }
            }
        }
    }

    @Test
    fun create() {
        val config = AppConfiguration.create(APP_ID)
        assertIs<AppConfiguration>(config)
        assertEquals(APP_ID, config.appId)
    }

    @Test
    fun syncRootDirectory_default() {
        val config = AppConfiguration.Builder(APP_ID).build()
        val expectedDefaultRoot = appFilesDirectory()
        assertEquals(expectedDefaultRoot, config.syncRootDirectory)
    }

    @Test
    fun syncRootDirectory() {
        val builder: AppConfiguration.Builder = AppConfiguration.Builder(APP_ID)
        val expectedRoot = pathOf(appFilesDirectory(), "myCustomDir")
        val config = builder
            .syncRootDirectory(expectedRoot)
            .build()
        assertEquals(expectedRoot, config.syncRootDirectory)
    }

    @Test
    fun syncRootDirectory_writeProtectedDir() {
        val builder: AppConfiguration.Builder = AppConfiguration.Builder(APP_ID)
        val dir = PlatformUtils.createTempDir(readOnly = true)
        assertFailsWith<IllegalArgumentException> { builder.syncRootDirectory(dir) }
    }

    // When creating the full path for a synced Realm, we will always append `/mongodb-realm` to
    // the configured `AppConfiguration.syncRootDir`
    @Test
    fun syncRootDirectory_appendDirectoryToPath() = runBlocking {
        val expectedRoot = pathOf(appFilesDirectory(), "myCustomDir")
        TestApp("syncRootDirectory_appendDirectoryToPath", builder = {
            it.syncRootDirectory(expectedRoot)
        }).use { app ->
            val (email, password) = TestHelper.randomEmail() to "password1234"
            val user = app.createUserAndLogIn(email, password)
            assertEquals(expectedRoot, app.configuration.syncRootDirectory)
            // When creating the full path for a synced Realm, we will always append `/mongodb-realm` to
            // the configured `AppConfiguration.syncRootDir`
            val partitionValue = TestHelper.randomPartitionValue()
            val suffix =
                pathOf("", "myCustomDir", "mongodb-realm", user.app.configuration.appId, user.id, "s_$partitionValue.realm")
            val config = SyncConfiguration.Builder(user, partitionValue, schema = setOf()).build()
            assertTrue(config.path.endsWith(suffix), "Failed: ${config.path} vs. $suffix")
        }
    }

    //    @Test // TODO we need an IO framework to test this properly, see https://github.com/realm/realm-kotlin/issues/699
//    fun syncRootDirectory_dirIsAFile() {
//        val builder: AppConfiguration.Builder = AppConfiguration.Builder(APP_ID)
//        val file = File(tempFolder.newFolder(), "dummyfile")
//        assertTrue(file.createNewFile())
//        assertFailsWith<IllegalArgumentException> { builder.syncRootDirectory(file) }
//    }
//
    @Test
    fun appName() {
        val config = AppConfiguration.Builder(APP_ID)
            .appName("app-name")
            .build()
        assertEquals("app-name", config.appName)
    }

    @Test
    fun appName_defaultValue() {
        val config = AppConfiguration.Builder(APP_ID).build()
        assertEquals(null, config.appName)
    }

    @Test
    fun appName_invalidValuesThrows() {
        val builder = AppConfiguration.Builder(APP_ID)
        assertFailsWith<IllegalArgumentException> { builder.appName("") }
    }

    @Test
    fun appVersion() {
        val config = AppConfiguration.Builder(APP_ID)
            .appVersion("app-version")
            .build()
        assertEquals("app-version", config.appVersion)
    }

    @Test
    fun appVersion_defaultValue() {
        val config = AppConfiguration.Builder(APP_ID).build()
        assertEquals(null, config.appVersion)
    }

    @Test
    fun appVersion_invalidValuesThrows() {
        val builder = AppConfiguration.Builder(APP_ID)
        assertFailsWith<IllegalArgumentException> { builder.appVersion("") }
    }

    @Test
    fun baseUrl() {
        val url = "http://myurl.com"
        val config = AppConfiguration.Builder("foo").baseUrl(url).build()
        assertEquals(url, config.baseUrl)
    }

    @Test
    fun baseUrl_defaultValue() {
        val url = "https://realm.mongodb.com"
        val config = AppConfiguration.Builder("foo").build()
        assertEquals(url, config.baseUrl)
    }

//    @Test
//    fun baseUrl_invalidValuesThrows() {
//        val configBuilder = AppConfiguration.Builder("foo")
//        assertFailsWith<IllegalArgumentException> { configBuilder.baseUrl("") }
//        assertFailsWith<IllegalArgumentException> { configBuilder.baseUrl(TestHelper.getNull()) }
//        assertFailsWith<IllegalArgumentException> { configBuilder.baseUrl("invalid-url") }
//    }
//
//    @Test
//    fun defaultSyncErrorHandler() {
//        val errorHandler = SyncSession.ErrorHandler { _, _ -> }
//
//        val config = AppConfiguration.Builder(APP_ID)
//            .defaultSyncErrorHandler(errorHandler)
//            .build()
//        assertEquals(config.defaultErrorHandler, errorHandler)
//    }
//
//    @Test
//    fun defaultSyncErrorHandler_invalidValuesThrows() {
//        assertFailsWith<IllegalArgumentException> {
//            AppConfiguration.Builder(APP_ID)
//                .defaultSyncErrorHandler(TestHelper.getNull())
//        }
//
//    }
//
//    @Test
//    fun defaultClientResetHandler() {
//        val handler = SyncSession.ClientResetHandler { _, _ -> }
//
//        val config = AppConfiguration.Builder(APP_ID)
//            .defaultClientResetHandler(handler)
//            .build()
//        assertEquals(config.defaultClientResetHandler, handler)
//    }
//
//    @Test
//    fun defaultClientResetHandler_invalidValuesThrows() {
//        val builder = AppConfiguration.Builder(APP_ID)
//        assertFailsWith<IllegalArgumentException> {
//            builder.defaultClientResetHandler(TestHelper.getNull())
//        }
//    }
//

    @Test
    fun encryptionKey() {
        val key = TestHelper.getRandomKey()
        val config = AppConfiguration.Builder(APP_ID)
            .encryptionKey(key)
            .build()

        assertContentEquals(key, config.encryptionKey)
    }

    @Test
    fun encryptionKey_isCopy() {
        val key = TestHelper.getRandomKey()
        val config = AppConfiguration.Builder(APP_ID)
            .encryptionKey(key)
            .build()

        assertNotSame(key, config.encryptionKey)
    }

    @Test
    fun encryptionKey_illegalValueThrows() {
        val builder = AppConfiguration.Builder(APP_ID)

        val tooShortKey = ByteArray(1)
        assertFailsWithMessage<IllegalArgumentException>("The provided key must be") {
            builder.encryptionKey(tooShortKey)
        }

        val tooLongKey = ByteArray(65)
        assertFailsWithMessage<IllegalArgumentException>("The provided key must be") {
            builder.encryptionKey(tooLongKey)
        }
    }

    @Test
    fun httpLogObfuscator_null() {
        val config = AppConfiguration.Builder(APP_ID)
            .httpLogObfuscator(null)
            .build()
        assertNull(config.httpLogObfuscator)
    }

    @Suppress("invisible_reference", "invisible_member")
    @Test
    fun defaultLoginInfoObfuscator() {
        val config = AppConfiguration.Builder(APP_ID).build()
        assertNotNull(config.httpLogObfuscator)
        assertTrue(config.httpLogObfuscator is io.realm.kotlin.mongodb.internal.LogObfuscatorImpl)
    }

    //
//    @Test
//    fun requestTimeout() {
//        val config = AppConfiguration.Builder(APP_ID)
//            .requestTimeout(1, TimeUnit.SECONDS)
//            .build()
//        assertEquals(1000L, config.requestTimeoutMs)
//    }
//
//    @Test
//    fun requestTimeout_invalidValuesThrows() {
//        val builder = AppConfiguration.Builder(APP_ID)
//
//        assertFailsWith<IllegalArgumentException> { builder.requestTimeout(-1, TimeUnit.MILLISECONDS) }
//        assertFailsWith<IllegalArgumentException> { builder.requestTimeout(1, TestHelper.getNull()) }
//    }
//
//    @Test
//    fun codecRegistry_null() {
//        val builder: AppConfiguration.Builder = AppConfiguration.Builder(APP_ID)
//        assertFailsWith<IllegalArgumentException> {
//            builder.codecRegistry(TestHelper.getNull())
//        }
//    }
//
//    @Test
//    fun defaultFunctionsCodecRegistry() {
//        val config: AppConfiguration = AppConfiguration.Builder(APP_ID).build()
//        assertEquals(AppConfiguration.DEFAULT_BSON_CODEC_REGISTRY, config.defaultCodecRegistry)
//    }
//
//    @Test
//    fun customCodecRegistry() {
//        val configCodecRegistry = CodecRegistries.fromCodecs(StringCodec())
//        val config: AppConfiguration = AppConfiguration.Builder(APP_ID)
//            .codecRegistry(configCodecRegistry)
//            .build()
//        assertEquals(configCodecRegistry, config.defaultCodecRegistry)
//    }
//
    // Check that custom headers and auth header renames are correctly used for HTTP requests.
    @Test
    fun customHeadersTest() = runBlocking {
        TestApp(
            "customHeadersTest",
            builder = { builder ->
                builder.customRequestHeaders {
                    put(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE)
                }.authorizationHeaderName(AUTH_HEADER_NAME)
            }
        ).use { app ->
            doCustomHeaderTest(app)
        }
    }

    private suspend fun doCustomHeaderTest(app: App) {
        val originalLevel = RealmLog.level
        RealmLog.level = LogLevel.ALL
        val channel = Channel<Boolean>(1)

        val logger = object : RealmLogger {
            override val level: LogLevel = LogLevel.DEBUG
            override val tag: String = "LOGGER"

            override fun log(
                level: LogLevel,
                throwable: Throwable?,
                message: String?,
                vararg args: Any?,
            ) {
                if (level == LogLevel.DEBUG && message!!.contains("-> $CUSTOM_HEADER_NAME: $CUSTOM_HEADER_VALUE") && message.contains(
                        "$AUTH_HEADER_NAME: "
                    )
                ) {
                    channel.trySend(true)
                }
            }
        }

        try {
            // Setup custom logger
            RealmLog.add(logger)

            // Perform a network related operation
            // It will fail because the server does not expect the modified auth header name
            assertFailsWith<ServiceException> {
                app.createUserAndLogIn()
            }

            // Receive the results.
            assertTrue(channel.receiveOrFail())
        } finally {
            // Restore log status
            RealmLog.remove(logger)
            RealmLog.level = originalLevel
        }
    }

    @Test
    fun logLevelDoesNotGetOverwrittenByConfig() {
        val expectedLogLevel = LogLevel.ALL
        RealmLog.level = expectedLogLevel

        AppConfiguration.create("")

        assertEquals(expectedLogLevel, RealmLog.level)

        RealmLog.reset()
    }

    @Test
    fun injectedBundleId() {
        val app = App.create(APP_ID)
        val config1 = app.configuration
        assertIs<AppConfigurationImpl>(config1)
        @Suppress("invisible_member")
        assertEquals("TEST_BUNDLE_ID", config1.bundleId)

        val config2 = AppConfiguration.create(APP_ID)
        assertIs<AppConfigurationImpl>(config2)
        @Suppress("invisible_member")
        assertEquals("TEST_BUNDLE_ID", config2.bundleId)

        val config3 = AppConfiguration.Builder(APP_ID).build()
        assertIs<AppConfigurationImpl>(config3)
        @Suppress("invisible_member")
        assertEquals("TEST_BUNDLE_ID", config3.bundleId)
    }

    fun equals_same() {
        val appId = "foo"
        val url = "http://myurl.com"
        val config = AppConfiguration.Builder(appId)
            .baseUrl(url)
            .build()
        val otherConfig = AppConfiguration.Builder(appId)
            .baseUrl(url)
            .build()
        assertEquals(config, otherConfig)
    }

    fun equals_different() {
        val config = AppConfiguration.Builder("foo")
            .baseUrl("http://myurl.com")
            .build()
        val otherConfig = AppConfiguration.Builder("fooooo")
            .baseUrl("http://www.mongodb.com")
            .build()
        assertNotEquals(config, otherConfig)
    }

    @Ignore // TODO
    fun dispatcher() {
    }

    @Test
    fun multiplexing_default() {
        val config = AppConfiguration.Builder("foo").build()
        assertTrue(config.enableSessionMultiplexing)
    }

    @Test
    fun multiplexing() {
        val config = AppConfiguration.Builder("foo")
            .enableSessionMultiplexing(false)
            .build()
        assertFalse(config.enableSessionMultiplexing)
    }

    @Test
    fun syncTimeOutOptions_default() {
        val config = AppConfiguration.Builder("foo").build()
        with(config.syncTimeoutOptions) {
            assertEquals(2.minutes, connectTimeout)
            assertEquals(30.seconds, connectionLingerTime)
            assertEquals(1.minutes, pingKeepalivePeriod)
            assertEquals(2.minutes, pongKeepalivePeriod)
            assertEquals(1.minutes, fastReconnectLimit)
        }
    }

    @Test
    fun syncTimeOutOptions() {
        val config = AppConfiguration.Builder("foo")
            .syncTimeouts {
                connectTimeout = 1.seconds
                connectionLingerTime = 1.seconds
                pingKeepalivePeriod = 1.seconds
                pongKeepalivePeriod = 1.seconds
                fastReconnectLimit = 1.seconds
            }
            .build()
        with(config.syncTimeoutOptions) {
            assertEquals(1.seconds, connectTimeout)
            assertEquals(1.seconds, connectionLingerTime)
            assertEquals(1.seconds, pingKeepalivePeriod)
            assertEquals(1.seconds, pongKeepalivePeriod)
            assertEquals(1.seconds, fastReconnectLimit)
        }
    }

}
