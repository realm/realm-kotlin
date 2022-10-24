package io.realm.kotlin.test.shared

import io.realm.kotlin.Configuration
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.fileExists
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TestHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class RealmInMemoryTests {
    private lateinit var tmpDir: String
    private lateinit var realm: Realm
    private lateinit var inMemConf: Configuration
    private lateinit var onDiskConf: Configuration
    @BeforeTest
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

    @AfterTest
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
        if (!realm.isClosed()) {
            realm.close()
        }
    }

    @Test
    fun inMemoryRealm_wipedOnClose() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        assertEquals(1, realm.query(Sample::class).count().find())
        realm.close()
        realm = Realm.open(inMemConf)
        assertEquals(0, realm.query(Sample::class).count().find())
    }

    @Test
    fun inMemoryRealm_noExistingFileAfterDelete() {
        realm.writeBlocking {
            copyToRealm(Sample())
        }
        assertEquals(1, realm.query(Sample::class).count().find())
        realm.close()
        assertFalse(fileExists(inMemConf.path))
    }

    @Test
    fun inMemoryRealm_withDifferentNames() {
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
        try {
            assertEquals(1, realm.query(Sample::class).count().find())
            assertEquals("foo", realm.query<Sample>("stringField == 'foo'").find().first().stringField)
            assertEquals(1, realm2.query(Sample::class).count().find())
            assertEquals("bar", realm2.query<Sample>("stringField == 'bar'").find().first().stringField)
        } finally {
            realm2.close()
            PlatformUtils.deleteTempDir(tmpDir2)
        }
    }

    @Test
    fun inMemoryRealm_delete() {
        assertFailsWith<java.lang.IllegalStateException> {
            Realm.deleteRealm(realm.configuration)
        }
        // Nothing should happen when deleting a closed in-mem-realm.
        realm.close()
        Realm.deleteRealm(realm.configuration)
    }

    @Test
    fun inMemoryRealm_writeCopyTo() {
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
    fun multiThread() {
        val threadError = arrayOfNulls<AssertionFailedError>(1)
        val workerCommittedLatch = Channel<Boolean>(1)
        val workerClosedLatch = Channel<Boolean>(1)
        val realmInMainClosedLatch = Channel<Boolean>(1)
        CoroutineScope.async {

        }
        async {
            realm = Realm.open(inMemConf)
            realm.writeBlocking {
                copyToRealm(Sample().apply { stringField = "foo" })
            }
            try {
                assertEquals(1, realm.query<Sample>().count().find())
            } catch (err : AssertionFailedError) {
                threadError[0] = err
                realm.close()
                return@runBlocking
            }
            workerCommittedLatch.send(true)

            // Waits until Realm instance closed in main thread.
            try {
                realmInMainClosedLatch.receive()
            } catch (err : InterruptedException) {
                threadError[0] = AssertionError("Worker thread was interrupted")
                realm.close()
                return@runBlocking
            }
        }
        withTimeout(10000L) {

        }
    }
}
