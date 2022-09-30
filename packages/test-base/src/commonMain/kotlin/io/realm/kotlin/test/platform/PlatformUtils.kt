package io.realm.kotlin.test.platform

import io.realm.kotlin.test.util.Utils
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

// Platform dependant helper methods
expect object PlatformUtils {
    fun createTempDir(prefix: String = Utils.createRandomString(16)): String
    fun deleteTempDir(path: String)
    @OptIn(ExperimentalTime::class)
    fun sleep(duration: Duration)
    fun threadId(): ULong
    fun triggerGC()
}
