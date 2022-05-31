package io.realm.kotlin.test.util

import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger

/**
 * Logger implementation that track latest log event, so we are able to inspect it.
 */
class TestLogger : RealmLogger {
    override val tag: String = "IGNORE"
    override val level: LogLevel = LogLevel.NONE
    var logLevel: LogLevel = LogLevel.NONE
    var throwable: Throwable? = null
    var message: String? = null
    var args: Array<out Any?> = arrayOf()

    override fun log(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?) {
        this.logLevel = level
        this.throwable = throwable
        this.message = message
        this.args = args
    }
}
