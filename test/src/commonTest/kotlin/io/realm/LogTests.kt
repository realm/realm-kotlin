@file:Suppress("invisible_reference", "invisible_member")
import io.realm.LogConfiguration
import io.realm.Realm
import io.realm.internal.PlatformHelper
import io.realm.internal.RealmLog
import io.realm.log.LogLevel
import io.realm.util.TestLogger
import io.realm.util.Utils
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

    private lateinit var log: RealmLog

    @BeforeTest
    fun setUp() {
        val loggers = listOf(PlatformHelper.createDefaultSystemLogger(Realm.DEFAULT_LOG_TAG))
        log = RealmLog(configuration = LogConfiguration(LogLevel.ALL, loggers))
    }

    @Test
    fun ignoreEventsLowerThanLogLevel() {
        val customLogger = TestLogger()
        log = RealmLog(configuration = LogConfiguration(LogLevel.WARN, listOf(customLogger)))
        log.warn("Testing 1")
        assertEquals("Testing 1", customLogger.message)
        log.error("Testing 2")
        assertEquals("Testing 2", customLogger.message)
        log.info("Testing 3") // This should be swallowed
        assertEquals("Testing 2", customLogger.message)
    }

    @Test
    fun customLogger() {
        val customLogger = TestLogger()
        log = RealmLog(configuration = LogConfiguration(LogLevel.ALL, listOf(customLogger)))
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
