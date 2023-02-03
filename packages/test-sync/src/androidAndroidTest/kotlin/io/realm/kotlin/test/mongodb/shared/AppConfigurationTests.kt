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

package io.realm.kotlin.test.mongodb.shared

import io.realm.kotlin.internal.platform.appFilesDirectory
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.mongodb.AppConfiguration
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.mongodb.asTestApp
import io.realm.kotlin.test.mongodb.createUserAndLogIn
import io.realm.kotlin.test.util.TestHelper
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

// private const val CUSTOM_HEADER_NAME = "Foo"
// private const val CUSTOM_HEADER_VALUE = "bar"
// private const val AUTH_HEADER_NAME = "RealmAuth"
private const val APP_ID = "app-id"

class AppConfigurationTests {

//    val looperThread = BlockingLooperThread()
//
//    @get:Rule
//    val tempFolder = TemporaryFolder()

//    @Test
//    fun authorizationHeaderName_illegalArgumentsThrows() {
//        val builder: AppConfiguration.Builder = AppConfiguration.Builder(APP_ID)
//        assertFailsWith<IllegalArgumentException> { builder.authorizationHeaderName(TestHelper.getNull()) }
//        assertFailsWith<IllegalArgumentException> { builder.authorizationHeaderName("") }
//    }
//
//    @Test
//    fun authorizationHeaderName() {
//        val config1 = AppConfiguration.Builder(APP_ID).build()
//        assertEquals("Authorization", config1.authorizationHeaderName)
//
//        val config2 = AppConfiguration.Builder(APP_ID)
//            .authorizationHeaderName("CustomAuth")
//            .build()
//        assertEquals("CustomAuth", config2.authorizationHeaderName)
//    }
//
//    @Test
//    fun addCustomRequestHeader_illegalArgumentThrows() {
//        val builder: AppConfiguration.Builder = AppConfiguration.Builder(APP_ID)
//        assertFailsWith<IllegalArgumentException> { builder.addCustomRequestHeader("", "val") }
//        assertFailsWith<IllegalArgumentException> { builder.addCustomRequestHeader(TestHelper.getNull(), "val") }
//        assertFailsWith<IllegalArgumentException> { builder.addCustomRequestHeader("header", TestHelper.getNull()) }
//    }
//
//    @Test
//    fun addCustomRequestHeader() {
//        val config = AppConfiguration.Builder(APP_ID)
//            .addCustomRequestHeader("header1", "val1")
//            .addCustomRequestHeader("header2", "val2")
//            .build()
//        val headers: Map<String, String> = config.customRequestHeaders
//        assertEquals(2, headers.size.toLong())
//        assertTrue(headers.any { it.key == "header1" && it.value == "val1" })
//        assertTrue(headers.any { it.key == "header2" && it.value == "val2" })
//    }
//
//    @Test
//    fun addCustomRequestHeaders() {
//        val inputHeaders: MutableMap<String, String> = LinkedHashMap()
//        inputHeaders["header1"] = "value1"
//        inputHeaders["header2"] = "value2"
//        val config = AppConfiguration.Builder(APP_ID)
//            .addCustomRequestHeaders(TestHelper.getNull())
//            .addCustomRequestHeaders(inputHeaders)
//            .build()
//        val outputHeaders: Map<String, String> = config.customRequestHeaders
//        assertEquals(2, outputHeaders.size.toLong())
//        assertTrue(outputHeaders.any { it.key == "header1" && it.value == "value1" })
//        assertTrue(outputHeaders.any { it.key == "header2" && it.value == "value2" })
//    }
//
//    @Test
//    fun addCustomHeader_combinesSingleAndMultiple() {
//        val config = AppConfiguration.Builder(APP_ID)
//            .addCustomRequestHeader("header3", "val3")
//            .addCustomRequestHeaders(mapOf(Pair("header1", "val1")))
//            .build()
//        val headers: Map<String, String> = config.customRequestHeaders
//        assertEquals(2, headers.size)
//        assertTrue(headers.any { it.key == "header3" && it.value == "val3" })
//        assertTrue(headers.any { it.key == "header1" && it.value == "val1" })
//    }

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
        val expectedRoot = "${appFilesDirectory()}/myCustomDir"
        val config = builder
            .syncRootDirectory(expectedRoot)
            .build()
        assertEquals(expectedRoot, config.syncRootDirectory)
    }

    @Test
    fun syncRootDirectory_writeProtectedDir() {
        val builder: AppConfiguration.Builder = AppConfiguration.Builder(APP_ID)
        val dir = "/"
        assertFailsWith<IllegalArgumentException> { builder.syncRootDirectory(dir) }
    }

    // When creating the full path for a synced Realm, we will always append `/mongodb-realm` to
    // the configured `AppConfiguration.syncRootDir`
    @Test
    fun syncRootDirectory_appendDirectoryToPath() = runBlocking {
        val expectedRoot = "${appFilesDirectory()}/myCustomDir"
        val app = TestApp(builder = {
            it.syncRootDirectory(expectedRoot)
        })
        val (email, password) = TestHelper.randomEmail() to "password1234"
        val user = app.createUserAndLogIn(email, password)
        try {
            assertEquals(expectedRoot, app.configuration.syncRootDirectory)
            // When creating the full path for a synced Realm, we will always append `/mongodb-realm` to
            // the configured `AppConfiguration.syncRootDir`
            val partitionValue = TestHelper.randomPartitionValue()
            val suffix = "/myCustomDir/mongodb-realm/${user.app.configuration.appId}/${user.identity}/s_$partitionValue.realm"
            val config = SyncConfiguration.Builder(user, partitionValue, schema = setOf()).build()
            assertTrue(config.path.endsWith(suffix), "Failed: ${config.path} vs. $suffix")
        } finally {
            app.asTestApp.close()
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
//    @Test
//    fun httpLogObfuscator_null() {
//        val config = AppConfiguration.Builder(APP_ID)
//            .httpLogObfuscator(TestHelper.getNull())
//            .build()
//        assertNull(config.httpLogObfuscator)
//    }
//
//    @Test
//    fun defaultLoginInfoObfuscator() {
//        val config = AppConfiguration.Builder(APP_ID).build()
//
//        val defaultHttpLogObfuscator = HttpLogObfuscator(LOGIN_FEATURE, AppConfiguration.loginObfuscators)
//        assertEquals(defaultHttpLogObfuscator, config.httpLogObfuscator)
//    }
//    // Check that custom headers and auth header renames are correctly used for HTTP requests
//    // performed from Java.
//    @Test
//    fun javaRequestCustomHeaders() {
//        var app: App? = null
//        try {
//            looperThread.runBlocking {
//                app = TestApp(builder = { builder ->
//                    builder.addCustomRequestHeader(CUSTOM_HEADER_NAME, CUSTOM_HEADER_VALUE)
//                    builder.authorizationHeaderName(AUTH_HEADER_NAME)
//                })
//                runJavaRequestCustomHeadersTest(app!!)
//            }
//        } finally {
//            app?.close()
//        }
//    }
//
//    private fun runJavaRequestCustomHeadersTest(app: App) {
//        val username = UUID.randomUUID().toString()
//        val password = "password"
//        val headerSet = AtomicBoolean(false)
//
//        // Setup logger to inspect that we get a log message with the custom headers
//        val level = RealmLog.getLevel()
//        RealmLog.setLevel(LogLevel.ALL)
//        val logger = RealmLogger { level: Int, tag: String?, throwable: Throwable?, message: String? ->
//            if (level > LogLevel.TRACE && message!!.contains(CUSTOM_HEADER_NAME) && message.contains(CUSTOM_HEADER_VALUE)
//                && message.contains("RealmAuth: ")) {
//                headerSet.set(true)
//            }
//        }
//        RealmLog.add(logger)
//        assertFailsWithErrorCode(ErrorCode.SERVICE_UNKNOWN) {
//            app.registerUserAndLogin(username, password)
//        }
//        RealmLog.remove(logger)
//        RealmLog.setLevel(level)
//
//        assertTrue(headerSet.get())
//        looperThread.testComplete()
//    }

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
    fun dispatcher() { }
}
