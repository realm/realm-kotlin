package io.realm.test.platform

import kotlin.time.Duration
import kotlin.time.ExperimentalTime

// Platform dependant helper methods
expect object PlatformUtils {
    fun createTempDir(): String
    fun deleteTempDir(path: String)
    @OptIn(ExperimentalTime::class)
    fun sleep(duration: Duration)
    fun threadId(): ULong
    fun triggerGC()
}
