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
@file:Suppress("invisible_reference", "invisible_member")
package io.realm.kotlin.test.common

import io.realm.kotlin.internal.ContextLogger
import io.realm.kotlin.internal.categoriesByPath
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.log.LogCategory
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLog
import io.realm.kotlin.log.RealmLogger
import io.realm.kotlin.test.util.TestLogger
import io.realm.kotlin.test.util.Utils
import kotlinx.atomicfu.atomic
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Class that exercises the logger implementation.
 *
 * It isn't possible, in any sane manner, to validate output on the default system logger actually
 * being correct, so this class mostly just exercises the relevant code paths in order to check for
 * crashes.
 */
class RealmLogTests {

    private lateinit var existingLogLevel: LogLevel
    private val log: ContextLogger = ContextLogger()

    @BeforeTest
    fun setUp() {
        existingLogLevel = RealmLog.getLevel()
    }

    @AfterTest
    fun tearDown() {
        RealmLog.reset()
    }

    @Test
    fun defaultLevel() {
        assertEquals(LogLevel.WARN, existingLogLevel)
    }

    @Test
    fun ignoreEventsLowerThanLogLevel() {
        val customLogger = TestLogger()
        RealmLog.apply {
            setLevel(LogLevel.WARN)
            add(customLogger)
            log.warn("Testing 1")
            assertEquals("Testing 1", customLogger.message)
            log.error("Testing 2")
            assertEquals("Testing 2", customLogger.message)
            log.info("Testing 3") // This should be swallowed
            assertEquals("Testing 2", customLogger.message)
        }
    }

    @Test
    fun customLogger() {
        val customLogger = TestLogger()
        RealmLog.setLevel(LogLevel.ALL)
        RealmLog.add(customLogger)

        var message = "Testing"

        // Simple message
        log.warn(message)
        assertEquals(LogLevel.WARN, customLogger.logLevel)
        assertNull(customLogger.throwable)
        assertEquals(message, customLogger.message)
        assertTrue(customLogger.args.isEmpty())

        // Advanced message
        val throwable = RuntimeException("BOOM")
        message = "Message: %s"
        val args: Array<out Any?> = arrayOf("foo")
        log.error(throwable, message, *args)
        assertEquals(LogLevel.ERROR, customLogger.logLevel)
        assertEquals(throwable, customLogger.throwable)
        assertEquals(message, customLogger.message)
        assertTrue(customLogger.args.contentEquals(args))
    }

    @Test
    fun smallLogEntry() {
        val message = "Testing the RealmLog implementation"
        LogLevel.entries.forEach {
            when (it) {
                LogLevel.ALL -> { /* Ignore */ }
                LogLevel.TRACE -> log.trace(message)
                LogLevel.DEBUG -> log.debug(message)
                LogLevel.INFO -> log.info(message)
                LogLevel.WARN -> log.warn(message)
                LogLevel.ERROR -> log.error(message)
                LogLevel.WTF -> log.wtf(message)
                LogLevel.NONE -> { /* Ignore */ }
                else -> throw IllegalArgumentException("Unknown level: $it")
            }
        }
    }

    @Test
    fun smallLogEntryWithArgs() {
        val message = "Testing the RealmLog implementation: (%s, %d, %f)"
        val args: Array<out Any?> = arrayOf("foo", Long.MAX_VALUE, Float.MAX_VALUE)
        LogLevel.entries.forEach {
            when (it) {
                LogLevel.ALL -> { /* Ignore */ }
                LogLevel.TRACE -> log.trace(message, *args)
                LogLevel.DEBUG -> log.debug(message, *args)
                LogLevel.INFO -> log.info(message, *args)
                LogLevel.WARN -> log.warn(message, *args)
                LogLevel.ERROR -> log.error(message, *args)
                LogLevel.WTF -> log.wtf(message, *args)
                LogLevel.NONE -> { /* Ignore */ }
                else -> throw IllegalArgumentException("Unknown level: $it")
            }
        }
    }

    @Test
    fun longLogEntry() {
        val message = Utils.createRandomString(8000)
        LogLevel.entries.forEach {
            when (it) {
                LogLevel.ALL -> { /* Ignore */ }
                LogLevel.TRACE -> log.trace(message)
                LogLevel.DEBUG -> log.debug(message)
                LogLevel.INFO -> log.info(message)
                LogLevel.WARN -> log.warn(message)
                LogLevel.ERROR -> log.error(message)
                LogLevel.WTF -> log.wtf(message)
                LogLevel.NONE -> { /* Ignore */ }
                else -> throw IllegalArgumentException("Unknown level: $it")
            }
        }
    }

    @Test
    fun longLogEntryWithArgs() {
        val message = "${Utils.createRandomString(8000)}: (%s, %d, %f)"
        val args: Array<out Any?> = arrayOf("foo", Long.MAX_VALUE, Float.MAX_VALUE)
        LogLevel.entries.forEach {
            when (it) {
                LogLevel.ALL -> { /* Ignore */ }
                LogLevel.TRACE -> log.trace(message, *args)
                LogLevel.DEBUG -> log.debug(message, *args)
                LogLevel.INFO -> log.info(message, *args)
                LogLevel.WARN -> log.warn(message, *args)
                LogLevel.ERROR -> log.error(message, *args)
                LogLevel.WTF -> log.wtf(message, *args)
                LogLevel.NONE -> { /* Ignore */ }
                else -> throw IllegalArgumentException("Unknown level: $it")
            }
        }
    }

    @Test
    fun logException() {
        val error = IllegalArgumentException("BOOM")
        LogLevel.entries.forEach {
            when (it) {
                LogLevel.ALL -> { /* Ignore */ }
                LogLevel.TRACE -> log.trace(error)
                LogLevel.DEBUG -> log.debug(error)
                LogLevel.INFO -> log.info(error)
                LogLevel.WARN -> log.warn(error)
                LogLevel.ERROR -> log.error(error)
                LogLevel.WTF -> log.wtf(error)
                LogLevel.NONE -> { /* Ignore */ }
                else -> throw IllegalArgumentException("Unknown level: $it")
            }
        }
    }

    @Test
    fun logExceptionWithMessage() {
        val error = IllegalArgumentException("BOOM")
        val message = "Details: (%s, %d, %f)"
        val args: Array<out Any?> = arrayOf("foo", Long.MAX_VALUE, Float.MAX_VALUE)
        LogLevel.entries.forEach {
            when (it) {
                LogLevel.ALL -> { /* Ignore */ }
                LogLevel.TRACE -> log.trace(error, message, *args)
                LogLevel.DEBUG -> log.debug(error, message, *args)
                LogLevel.INFO -> log.info(error, message, *args)
                LogLevel.WARN -> log.warn(error, message, *args)
                LogLevel.ERROR -> log.error(error, message, *args)
                LogLevel.WTF -> log.wtf(error, message, *args)
                LogLevel.NONE -> { /* Ignore */ }
                else -> throw IllegalArgumentException("Unknown level: $it")
            }
        }
    }

    @Test
    fun addLogger() {
        val called = atomic<Boolean>(false)
        val customLogger = object : RealmLogger {
            override fun log(
                category: LogCategory,
                level: LogLevel,
                throwable: Throwable?,
                message: String?,
                vararg args: Any?
            ) {
                assertEquals(LogCategory.Realm.Sdk, category)
                assertEquals("Hello", message)
                called.value = true
            }
        }
        RealmLog.add(customLogger)
        log.trace("Hello")
        assertTrue(called.value)
    }

    @Test
    fun addLogger_twice() {
        val called = atomic(0)
        val customLogger = object : RealmLogger {
            override fun log(
                category: LogCategory,
                level: LogLevel,
                throwable: Throwable?,
                message: String?,
                vararg args: Any?
            ) {
                assertEquals(LogCategory.Realm.Sdk, category)
                assertEquals("Hello", message)
                called.incrementAndGet()
            }
        }
        RealmLog.add(customLogger)
        RealmLog.add(customLogger)
        log.trace("Hello")
        assertEquals(2, called.value)
    }

    @Test
    fun removeLogger_success() {
        val called = atomic(0)
        val customLogger = object : RealmLogger {
            override fun log(
                category: LogCategory,
                level: LogLevel,
                throwable: Throwable?,
                message: String?,
                vararg args: Any?
            ) {
                assertEquals(LogCategory.Realm.Sdk, category)
                assertEquals("Hello", message)
                called.incrementAndGet()
            }
        }
        RealmLog.add(customLogger)
        assertTrue(RealmLog.remove(customLogger))
        log.error("Hello")
        assertEquals(0, called.value)
    }

    @Test
    fun removeLogger_falseForNonExistingLogger() {
        val customLogger = object : RealmLogger {
            override fun log(
                category: LogCategory,
                level: LogLevel,
                throwable: Throwable?,
                message: String?,
                vararg args: Any?
            ) {
                fail()
            }
        }
        assertFalse(RealmLog.remove(customLogger))
    }

    @Test
    fun removeAll_success() {
        val customLogger = object : RealmLogger {
            override fun log(
                category: LogCategory,
                level: LogLevel,
                throwable: Throwable?,
                message: String?,
                vararg args: Any?
            ) {
                fail()
            }
        }
        RealmLog.add(customLogger)
        assertTrue(RealmLog.removeAll())
        log.trace("Hello") // Should not hit `fail()`
    }

    @Test
    fun removeAll_falseIfNoLoggerWereRemoved() {
        assertTrue(RealmLog.removeAll())
        assertFalse(RealmLog.removeAll())
    }

    @Test
    fun addDefaultSystemLogger_success() {
        RealmLog.removeAll()
        assertTrue(RealmLog.addDefaultSystemLogger())
    }

    @Test
    fun addDefaultSystemLogger_failure() {
        assertFalse(RealmLog.addDefaultSystemLogger())
    }

    @Test
    fun setCategoryLevels() {
        categoriesByPath
            .values
            .forEach { logCategory ->
                val previousLevel: LogLevel = RealmLog.getLevel(logCategory)
                RealmLog.setLevel(LogLevel.TRACE, logCategory)
                assertEquals(LogLevel.TRACE, RealmLog.getLevel(logCategory))

                // Restore the level to whatever it was set before
                RealmLog.setLevel(previousLevel, logCategory)
            }
    }

    @Test
    fun categoryContains() {
        assertFalse(LogCategory.Realm in LogCategory.Realm.Storage)

        assertTrue(LogCategory.Realm.Storage in LogCategory.Realm)
        assertTrue(LogCategory.Realm.Storage.Transaction in LogCategory.Realm)
        assertTrue(LogCategory.Realm.Storage.Transaction in LogCategory.Realm.Storage)
    }

    /**
     * Core defines the different categories in runtime, forcing the SDK to define the categories again.
     * This test validates that we have defined the same categories as in Core.
     */
    @Test
    fun categoriesWatchdog() {
        val coreLogCategoryNames = RealmInterop.realm_get_category_names()

        val logCategoriesPaths = categoriesByPath
            .keys

        logCategoriesPaths.forEach { path ->
            assertContains(coreLogCategoryNames, path)
        }

        coreLogCategoryNames.forEach { path ->
            assertContains(logCategoriesPaths, path)
        }
    }

    @Test
    fun filterSdkLogs() {
        val called = atomic<Boolean>(false)
        val customLogger = object : RealmLogger {
            override fun log(
                category: LogCategory,
                level: LogLevel,
                throwable: Throwable?,
                message: String?,
                vararg args: Any?,
            ) {
                called.value = true
            }
        }
        RealmLog.add(customLogger)

        ContextLogger().run {
            RealmLog.setLevel(LogLevel.NONE, LogCategory.Realm.Sdk)
            warn("should be filtered")
            assertFalse(called.value, "Unexpected message logged")
        }
    }
}
