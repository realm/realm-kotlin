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
package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.internal.platform.createDefaultSystemLogger
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLog
import io.realm.kotlin.test.util.TestLogger
import io.realm.kotlin.test.util.Utils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Class that exercises the logger implementation.
 *
 * It isn't possible, in any sane manner, to validate the output actually being correct, so this class mostly just
 * exercises the relevant code paths in order to check for crashes.
 */
class LogTests {

    private lateinit var existingLogLevel: LogLevel
    private lateinit var log: RealmLog

    @BeforeTest
    fun setUp() {
        existingLogLevel = RealmLog.level
        RealmLog.level = LogLevel.ALL
        RealmLog.add(createDefaultSystemLogger(Realm.DEFAULT_LOG_TAG))
        log = RealmLog
    }

    @AfterTest
    fun tearDown() {
        RealmLog.level = existingLogLevel
        RealmLog.removeAllLoggers(removeDefaultSystemLogger = false)
    }

    @Test
    fun ignoreEventsLowerThanLogLevel() {
        val customLogger = TestLogger()
        RealmLog.apply {
            level = LogLevel.WARN
            add(customLogger)
            warn("Testing 1")
            assertEquals("Testing 1", customLogger.message)
            error("Testing 2")
            assertEquals("Testing 2", customLogger.message)
            info("Testing 3") // This should be swallowed
            assertEquals("Testing 2", customLogger.message)
        }
    }

    @Test
    fun customLogger() {
        val customLogger = TestLogger()
        RealmLog.level = LogLevel.ALL
        RealmLog.add(customLogger)

        var message = "Testing"

        // Simple message
        RealmLog.warn(message)
        assertEquals(LogLevel.WARN, customLogger.logLevel)
        assertNull(customLogger.throwable)
        assertEquals(message, customLogger.message)
        assertTrue(customLogger.args.isEmpty())

        // Advanced message
        val throwable = RuntimeException("BOOM")
        message = "Message: %s"
        val args: Array<out Any?> = arrayOf("foo")
        RealmLog.error(throwable, message, *args)
        assertEquals(LogLevel.ERROR, customLogger.logLevel)
        assertEquals(throwable, customLogger.throwable)
        assertEquals(message, customLogger.message)
        assertTrue(customLogger.args.contentEquals(args))
    }

    @Test
    fun smallLogEntry() {
        val message = "Testing the RealmLog implementation"
        LogLevel.values().forEach {
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
        LogLevel.values().forEach {
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
        LogLevel.values().forEach {
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
        LogLevel.values().forEach {
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
        LogLevel.values().forEach {
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
        LogLevel.values().forEach {
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
}
