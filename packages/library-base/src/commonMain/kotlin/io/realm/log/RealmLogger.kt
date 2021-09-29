package io.realm.log

import io.realm.RealmConfiguration
/**
 * Interface describing a logger implementation.
 *
 * @see RealmConfiguration.Builder.log
 */
interface RealmLogger {

    /**
     * Tag that can be used to describe the output.
     */
    val tag: String

    /**
     * Log an event.
     */
    fun log(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?)

    fun log(message: String) {
        log(LogLevel.ALL, null, message, null)
    }
}
