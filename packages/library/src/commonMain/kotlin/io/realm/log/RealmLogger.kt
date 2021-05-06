package io.realm.log

/**
 * Interface describing a logger implementation.
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
}
