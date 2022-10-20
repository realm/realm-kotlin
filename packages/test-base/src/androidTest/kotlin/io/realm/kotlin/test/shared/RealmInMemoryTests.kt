package io.realm.kotlin.test.shared

import io.realm.kotlin.Configuration
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.ext.query
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TestHelper
import junit.framework.AssertionFailedError
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

class RealmInMemoryTests {
    private lateinit var tmpDir: String
    private lateinit var realm: Realm
    private lateinit var inMemConf: Configuration
    private lateinit var onDiskConf: Configuration
    @Before
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        inMemConf = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .inMemory()
            .build()
        onDiskConf = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .build()
        realm = Realm.open(inMemConf)
    }

    @After
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
        if (realm != null) {
            realm.close()
        }
    }

    @Test
    fun inMemoryRealm() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        assertEquals(1, realm.query(Sample::class).count().find())
        realm.close()
        realm = Realm.open(inMemConf)
        assertEquals(0, realm.query(Sample::class).count().find())
    }

    @Test
    fun inMemoryRealmWithDifferentNames() {
        realm.writeBlocking {
            copyToRealm(Sample().apply { stringField = "foo" })
        }

        // Creates the 2nd in-memory Realm with a different name. To make sure they are not affecting each other.
        val tmpDir2 = PlatformUtils.createTempDir("2")
        val configuration2 = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir2)
            .inMemory()
            .build()
        val realm2 = Realm.open(configuration2)
        realm2.writeBlocking {
            copyToRealm(Sample().apply { stringField = "bar" })
        }
        assertEquals(1, realm.query(Sample::class).count().find())
        assertEquals("foo", realm.query<Sample>("stringField == 'foo'").find().first().stringField)
        assertEquals(1, realm2.query(Sample::class).count().find())
        assertEquals("bar", realm2.query<Sample>("stringField == 'bar'").find().first().stringField)
        realm2.close()
        PlatformUtils.deleteTempDir(tmpDir2)
    }

    @Test
    fun delete() {
        try {
            Realm.deleteRealm(realm.configuration)
            fail("Realm.deleteRealm should fail with illegal state")
        } catch (ignored: IllegalStateException) {
        }

        // Nothing should happen when deleting a closed in-mem-realm.
        realm.close()
        Realm.deleteRealm(realm.configuration)
    }

    @Test
    fun writeCopyTo() {
        val key: ByteArray = TestHelper.getRandomKey()
        val fileName: String = tmpDir + ".realm"
        val encFileName: String = tmpDir + ".enc.realm"


        val conf = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(fileName)
            .build()
        val encConf = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(encFileName)
            .encryptionKey(key)
            .build()
        Realm.deleteRealm(conf)
        Realm.deleteRealm(encConf)
        realm.writeBlocking {
            copyToRealm(Sample().apply { stringField = "foo" })
        }

        // Tests a normal Realm file.
        realm.writeCopyTo(conf)
        val onDiskRealm: Realm = Realm.open(conf)
        assertEquals(1, onDiskRealm.query<Sample>().count().find())
        onDiskRealm.close()

        // Tests a encrypted Realm file.
        realm.writeCopyTo(encConf)
        val onDiskEncryptedRealm: Realm = Realm.open(encConf)
        assertEquals(1, onDiskEncryptedRealm.query<Sample>().count().find())
        onDiskEncryptedRealm.close()

        // Tests with a wrong key to see if it fails as expected.
        val randomKey = Random.nextBytes(64)
        RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(encFileName)
            .encryptionKey(randomKey)
            .build()
            .let { conf ->
                assertFailsWith(IllegalArgumentException::class, "Encrypted Realm should not be openable with a wrong encryption key") {
                    Realm.open(conf)
                }
            }
    }

    // Tests writeCopyTo result when called in a transaction.
    @Test
    fun writeCopyToInTransaction() {
        val fileName: String = tmpDir + ".realm"
        val conf = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(fileName)
            .build()
        Realm.deleteRealm(conf)
        lateinit var onDiskRealm: Realm
        // The value insertion is not completed until after the scope of writeBlocking, meaning that both
        // querys result will be 0
        realm.writeBlocking {
            copyToRealm(Sample().apply { stringField = "foo" })
            realm.writeCopyTo(conf)
            onDiskRealm = Realm.open(conf)
            assertEquals(0, onDiskRealm.query<Sample>().count().find())
        }
        assertEquals(0, onDiskRealm.query<Sample>().count().find())
        onDiskRealm.close()
    }

    // Test below scenario:
    // 1. Creates a in-memory Realm instance in the main thread.
    // 2. Creates a in-memory Realm with same name in another thread.
    // 3. Closes the in-memory Realm instance in the main thread and the Realm data should not be released since
    //    another instance is still held by the other thread.
    // 4. Closes the in-memory Realm instance and the Realm data should be released since no more instance with the
    //    specific name exists.
    @Test
    @Throws(InterruptedException::class, ExecutionException::class)
    fun multiThread() {
        val workerCommittedLatch = CountDownLatch(1)
        val workerClosedLatch = CountDownLatch(1)
        val realmInMainClosedLatch = CountDownLatch(1)
        val threadError = arrayOfNulls<AssertionFailedError>(1)

        // Step 2.
        val workerThread = Thread(Runnable {
            val realm: Realm = Realm.open(inMemConf)
            realm.writeBlocking {
                copyToRealm(Sample().apply { stringField = "foo" })
            }
            try {
                assertEquals(1, realm.query<Sample>().count().find())
            } catch (afe: AssertionFailedError) {
                threadError[0] = afe
                realm.close()
                return@Runnable
            }
            workerCommittedLatch.countDown()

            // Waits until Realm instance closed in main thread.
            try {
                realmInMainClosedLatch.await(10, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                threadError[0] = AssertionFailedError("Worker thread was interrupted.")
                realm.close()
                return@Runnable
            }
            realm.close()
            workerClosedLatch.countDown()
        })
        workerThread.start()


        // Waits until the worker thread started.
        workerCommittedLatch.await(10, TimeUnit.SECONDS)
        if (threadError[0] != null) {
            throw threadError[0]!!
        }

        // Refreshes will be ran in the next loop, manually refreshes it here.
        realm.refresh()
        assertEquals(1, testRealm.where(Dog::class.java).count())

        // Step 3.
        // Releases the main thread Realm reference, and the worker thread holds the reference still.
        testRealm.close()

        // Step 4.
        // Creates a new Realm reference in main thread and checks the data.
        testRealm = Realm.getInstance(inMemConf)
        assertEquals(1, testRealm.where(Dog::class.java).count())
        testRealm.close()

        // Let the worker thread continue.
        realmInMainClosedLatch.countDown()

        // Waits until the worker thread finished.
        workerClosedLatch.await(TestHelper.SHORT_WAIT_SECS, TimeUnit.SECONDS)
        if (threadError[0] != null) {
            throw threadError[0]
        }

        // Since all previous Realm instances has been closed before, below will create a fresh new in-mem-realm instance.
        testRealm = Realm.getInstance(inMemConf)
        assertEquals(0, testRealm.where(Dog::class.java).count())
    }
}