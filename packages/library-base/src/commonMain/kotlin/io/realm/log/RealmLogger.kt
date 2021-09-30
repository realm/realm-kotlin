package io.realm.log

import io.realm.RealmConfiguration
import io.realm.internal.interop.CoreLogger
/**
 * Interface describing a logger implementation.
 *
 * @see RealmConfiguration.Builder.log
 */
interface RealmLogger: CoreLogger {

    /**
     * Tag that can be used to describe the output.
     */
    val tag: String

    /**
     * Log an event.
     */
    fun log(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?)

    fun log(message: String) {
        println("---> $message")
//        log(LogLevel.ALL, null, message, null)
    }

    // FIXME
    override fun log(level: Short, message: String) {
        log(LogLevel.ALL, null, message, null)
    }
}
