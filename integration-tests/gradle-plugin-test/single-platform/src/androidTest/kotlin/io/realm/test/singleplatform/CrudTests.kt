package io.realm.test.singleplatform

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.query
import io.realm.test.singleplatform.model.TestClass
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.fail
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.random.Random


class CrudTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @Before
    fun setup() {
        tmpDir = Files.createTempDirectory("android_tests").absolutePathString()
        realm = RealmConfiguration.Builder(setOf(TestClass::class))
            .directory(tmpDir)
            .build()
            .let { Realm.open(it) }
    }

    @After
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        File(tmpDir).deleteRecursively()
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

        realm.writeBlocking {
            findLatest(updatedTestObject)?.let { delete(it) }
                ?: fail("Couldn't find test object")
        }

        assertEquals(0, realm.query<TestClass>("id = 1").find().size)
    }
}