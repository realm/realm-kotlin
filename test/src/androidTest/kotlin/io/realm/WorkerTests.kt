package io.realm

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import org.junit.Test
import test.Sample
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.*

@ExperimentalPathApi
class WorkerTests {

    @RealmModule(Sample::class)
    class MySchema

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = Utils.createTempDir()
        configuration = RealmConfiguration.Builder(schema = MySchema(), path = "$tmpDir/default.realm").build()
        realm = Realm(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (!realm.isClosed()) {
            realm.close()
        }
        Utils.deleteTempDir(tmpDir)
    }

    @Test
    fun write() = runBlocking {
        assertEquals(0, realm.objects(Sample::class).size)
        val result: Sample = realm.write {
            val result = copyToRealm(Sample().apply { stringField = "TestObject" })
            assertEquals(1, this.objects(Sample::class).size)
            result // FIXME: Don't attempt to accces this afterwards. It is still not frozen correctly
        }
        // Realm is updated as soon as the write completes
        assertEquals(1, realm.objects(Sample::class).size)
    }

    // Show that notifications are getting triggered from writes from the writer
    // thread and that we can access the results afterwards
    @Test
    fun resultNotifications() {
        val frozenResults: RealmResults<Sample> = realm.objects(Sample::class)
        val observableResults = realm.objects(Sample::class)
        val counter =  AtomicInteger(0)

        val flowJob = GlobalScope.launch {
            observableResults.observe()
                .collect {
                    println("Frozen size: ${frozenResults.size}")
                    println("New size: ${it.size}")
                    counter.set(it.size)

                    // Just check that we can access result from another thread
                    withContext(Dispatchers.IO) {
                        it.first().intField
                    }
                }
        }

        GlobalScope.launch {
            realm.write {
                copyToRealm(Sample().apply { intField = 1 })
                println("Wrote Sample 1")
            }
        }

        GlobalScope.launch {
            realm.write {
                copyToRealm(Sample().apply { intField = 2 })
                println("Wrote Sample 2")
            }
        }

        runBlocking {
            while(counter.get() != 2) {
                println("Delaying...")
                delay(100)
            }
            flowJob.cancelAndJoin()
        }
    }
}