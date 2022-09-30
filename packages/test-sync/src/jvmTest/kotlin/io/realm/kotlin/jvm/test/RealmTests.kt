package io.realm.kotlin.jvm.test

import io.realm.kotlin.Realm
import io.realm.kotlin.entities.sync.ChildPk
import io.realm.kotlin.entities.sync.ParentPk
import io.realm.kotlin.mongodb.Credentials
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.test.mongodb.TestApp
import io.realm.kotlin.test.util.TestHelper
import io.realm.kotlin.test.util.use
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RealmTests {

    // Ensure that we cleanup all threads owned by Realm when closing the Realm as it will
    // otherwise prevent things like the JVM main thread from exiting.
    @Ignore // FIXME The SyncClient thread is currently not being closed.
    @Test
    fun cleanupAllRealmThreadsOnClose() = runBlocking {
        val startingThreadCount = Thread.activeCount()
        // Finalizer might be running if a another Realm has been opened first.
        val finalizerRunning = Thread.getAllStackTraces().filter { it.key.name == "RealmFinalizerThread" }.isNotEmpty()
        // Not 100% sure where the `kotlinx.coroutines.DefaultExecutor` thread is coming from, it
        // isn't created when opening normal Realms. I assume it is related to our use of Ktor.
        // For now, just track if it is already running or not and account for it.
        val defaultCoroutineExecutorRunning = Thread.getAllStackTraces().filter { it.key.name == "kotlinx.coroutines.DefaultExecutor" }.isNotEmpty()

        // Create the app and login
        val app = TestApp()
        val user = app.login(Credentials.anonymous())
        // Starting an app will create a Realm Network Dispatcher and a Ktor Http Engine.
        // The Ktor Http engine will itself configure a number of worker threads, with the default
        // being 4: https://api.ktor.io/ktor-client/ktor-client-core/io.ktor.client.engine/-http-client-engine-config/threads-count.html
        // Also, if the RealmFinalizingDaemon or default coroutine executor isn't running, they
        // will also be started.
        val systemThreads = if (finalizerRunning) 0 else 1 + if (defaultCoroutineExecutorRunning) 0 else 1
        val syncClientThreads = 1
        val appThreads = 5
        assertEquals(startingThreadCount + appThreads + systemThreads, Thread.activeCount(), "Number of app threads mismatched.")

        // Opening a Realm will also start a Notifier and Writer dispatcher.
        // The +1 is the Sync Client thread, which for some reason appear in Java.
        val realmThreads = 2
        val configuration = SyncConfiguration.create(user, TestHelper.randomPartitionValue(), setOf(ParentPk::class, ChildPk::class))
        Realm.open(configuration).use { realm ->
            assertEquals(startingThreadCount + realmThreads + appThreads + systemThreads + syncClientThreads, Thread.activeCount(), "Failed to start notifier dispatchers.")
        }
        printThreadTrace()

        // Verify that we can close apps and Realms and expect resources to be cleaned up as well.
        val maxThreads = startingThreadCount + appThreads + realmThreads + systemThreads + syncClientThreads

        // Closing a Realm should also cleanup our default dispatchers.
        // The finalizer thread will never be closed.
        var expectedThreadCount = maxThreads - realmThreads
        var counter = 10 // Wait 10 seconds for threads to settle
        while(Thread.activeCount() != expectedThreadCount && counter > 0) {
            delay(1000)
            counter--
        }
        printThreadTrace()
        assertEquals(expectedThreadCount, Thread.activeCount(), "Failed to close Realm threads.")

        // Closing the app should cleanup the app dispatcher and http engine threads.
        expectedThreadCount -= appThreads
        app.close()
        counter = 10 // Wait 10 seconds for threads to settle
        while(Thread.activeCount() != expectedThreadCount && counter > 0) {
            delay(1000)
            counter--
        }
        // Remaining: Finalizer, SyncClient, DefaultExecutor
        assertEquals(expectedThreadCount, Thread.activeCount(), "Failed to close App threads.")
    }

    private fun printThreadTrace() {
        println("--------------------------------")
        Thread.getAllStackTraces().keys.forEach { t: Thread ->
            println("${t.name} - Is Daemon ${t.isDaemon} - Is Alive ${t.isAlive}")
        }
        println("Total threads: ${Thread.getAllStackTraces().keys}")
        println("Active threads: ${Thread.activeCount()}")

    }
}