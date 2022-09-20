package io.realm.test.multiplatform

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query
import io.realm.test.multiplatform.model.TestClass
import io.realm.test.multiplatform.util.platform.PlatformUtils
import kotlin.test.*

class CrudTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir("integration_test")
        realm = RealmConfiguration.Builder(setOf(TestClass::class))
            .directory(tmpDir)
            .build()
            .let { Realm.open(it) }
    }

    @AfterTest
    fun teadDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun crud() {
        // CREATE
        realm.writeBlocking {
            copyToRealm(TestClass().apply {
                id = 1
                text = "TEST"
            })
        }

        // READ
        val testObject = realm.query<TestClass>("id = 1").find().single()
        assertEquals("TEST", testObject.text)

        // UPDATE
        realm.writeBlocking {
            findLatest(testObject)?.apply {
                text = "UPDATED"
            }
        }
        val updatedTestObject = realm.query<TestClass>("id = 1").find().single()
        assertEquals("UPDATED", updatedTestObject.text)

        // DELETE
        realm.writeBlocking {
            findLatest(updatedTestObject)?.let { delete(it) }
                ?: fail("Couldn't find test object")
        }
        assertEquals(0, realm.query<TestClass>("id = 1").find().size)
    }
}