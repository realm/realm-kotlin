package io.realm.kotlin.jvm

import io.realm.kotlin.Realm
import io.realm.kotlin.entities.sync.ChildPk
import io.realm.kotlin.entities.sync.ParentPk
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.util.TestHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RealmTests {

    // Ensure that we cleanup all threads owned by Realm when closing the Realm as it will
    // otherwise prevent things like the JVM main thread from exiting.
    //
    // Since both coroutines and OkHttp spin up a fair amount of workers with unpredictable
    // lifecycle, it is very difficult to track all threads. Instead this test just makes a best
    // effort in detecting the cases we do know about.
    @Test
    fun cleanupAllRealmThreadsOnClose() = runBlocking {
        val app = TestApp()
        val user = app.login(Credentials.anonymous())
        val configuration = SyncConfiguration.create(user, TestHelper.randomPartitionValue(), setOf(ParentPk::class, ChildPk::class))
        Realm.open(configuration).close()
        app.close()
        var counter = 5 // Wait 5 seconds for threads to settle
        while (counter > 0) {
            delay(1000)
            counter--
        }

        // Ensure we only have daemon threads after closing Realms and Apps
        val activeThreads = Thread.getAllStackTraces().keys
            .filter { !it.isDaemon }
            .filterNot {
                // Ignore the Sync thread for now, until https://github.com/realm/realm-core/issues/5966
                // is fixed.
                it.name.startsWith("Thread-")
            }
            .filterNot {
                // Android Studio or Gradle worker threads
                it.name.startsWith("/127.0.0.1")
            }
            .filterNot {
                // Test thread
                it.name == "Test worker @coroutine#1"
            }
            .size

        assertEquals(0, activeThreads, "Active threads where found: ${threadTrace()}")
    }

    private fun threadTrace(): String {
        val sb = StringBuilder()
        sb.appendLine("--------------------------------")
        val stack = Thread.getAllStackTraces()
        stack.keys
            .sortedBy { it.name }
            .forEach { t: Thread ->
                sb.appendLine("${t.name} - Is Daemon ${t.isDaemon} - Is Alive ${t.isAlive}")
            }
        sb.appendLine("All threads: ${stack.keys.size}")
        sb.appendLine("Active threads: ${Thread.activeCount()}")
        return sb.toString()
    }
}
