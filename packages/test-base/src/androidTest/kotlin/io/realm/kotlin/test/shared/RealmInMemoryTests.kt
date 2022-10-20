package io.realm.kotlin.test.shared

import io.realm.kotlin.Configuration
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.ext.query
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.TestHelper
import junit.framework.Assert
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.fail

class RealmInMemoryTests {
    private lateinit var tmpDir: String
    private lateinit var realm: Realm
    private lateinit var configuration: Configuration
    @Before
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .inMemory()
            .build()
        realm = Realm.open(configuration)
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
        realm = Realm.open(configuration)
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
        assertEquals("bar", realm2.query<Sample>("stringfield == 'bar'").find().first().stringField)
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
        try {
            val wrongKeyConf = RealmConfiguration.Builder(schema = setOf(Sample::class))
                .directory(encFileName)
                .encryptionKey(TestHelper.getRandomKey(42))
                .build()
            Realm.open(wrongKeyConf)
            fail("Realm.getInstance should fail with RealmFileException")
        } catch (expected: Exception) {
            expected.message
        }
    }
}