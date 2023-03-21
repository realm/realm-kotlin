package io.realm.kotlin.test

import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Logged collecting all logs it has seen.
 */
class CustomLogCollector(
    override val tag: String,
    override val level: LogLevel
) : RealmLogger {

    private val mutex = Mutex()
    private val _logs = mutableListOf<String>()
    /**
     * Returns a snapshot of the current state of the logs.
     */
    val logs: List<String>
        get() = runBlocking {
            mutex.withLock {
                _logs.toList()
            }
        }

    override fun log(level: LogLevel, throwable: Throwable?, message: String?, vararg args: Any?) {
        val logMessage: String = message!!
        runBlocking {
            mutex.withLock {
                _logs.add(logMessage)
            }
        }
    }
}
