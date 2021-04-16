package io.realm

import org.junit.Test
import test.Sample
import io.realm.internal.worker.PublicRealm
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class WorkerTests {

    @RealmModule(Sample::class)
    class MySchema

    lateinit var tmpDir: String
    lateinit var configuration: RealmConfiguration

    @BeforeTest
    fun setup() {
        tmpDir = Utils.createTempDir()
        configuration = RealmConfiguration.Builder(schema = MySchema(), path = "$tmpDir/default.realm").build()
    }

    @AfterTest
    fun tearDown() {
        Utils.deleteTempDir(tmpDir)
    }

    @Test
    fun write() {
        val realm = PublicRealm(configuration)

        runBlocking {
            val result: Sample = realm.write {
                val result = copyToRealm(Sample().apply { stringField = "TestObject" })
                assertEquals(1, this.objects(Sample::class).size)
                result // FIXME: Don't attempt to accces this afterwards. It is still not frozen correctly
            }

            // Realm is updated as soon as the write completes
            assertEquals(1, realm.objects(Sample::class).size)
        }

        assertFalse(realm.isClosed())
        realm.close()
        assertTrue(realm.isClosed())
    }
}