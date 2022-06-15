package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.find
import io.realm.kotlin.test.platform.PlatformUtils
import kotlin.random.Random
import kotlin.reflect.KMutableProperty1
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

const val MAX_BINARY_SIZE = 0xFFFFF8 - 8 /*array header size*/

class ByteArrayTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (!realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    @Ignore // TODO Fails on native at RealmInterop line 2385 when assigning the value via setter
    fun boundaries() {
        val longBinary = Random.nextBytes(MAX_BINARY_SIZE)
        val tooLongBinary = Random.nextBytes(MAX_BINARY_SIZE + 1)

        // Test long array
        val sample = Sample().apply { binaryField = longBinary }
        roundTrip(sample, Sample::binaryField) { value ->
            assertContentEquals(longBinary, value)
        }

        // Test too long array
        realm.writeBlocking {
            query<Sample>()
                .first()
                .find { sample ->
                    assertNotNull(sample)
                    assertFailsWith<IllegalArgumentException> {
                        sample.binaryField = tooLongBinary
                    }.let {
                        assertTrue(it.message!!.contains("too big"))
                    }
                }
        }
    }

    @Test
    fun equals_defaultValues() {
        val sample = Sample()
        roundTrip(sample, Sample::binaryField) { value ->
            assertContentEquals(sample.binaryField, value)
        }
        roundTrip(sample, Sample::nullableBinaryField) { value ->
            assertContentEquals(sample.nullableBinaryField, value)
        }
    }

    @Test
    fun equals_assignedValues() {
        val byteArray = byteArrayOf(22, 44, 66)
        roundTrip(byteArray, Sample::binaryField) { value ->
            assertContentEquals(byteArray, value)
        }
        roundTrip(byteArray, Sample::nullableBinaryField) { value ->
            assertContentEquals(byteArray, value)
        }
    }

    // Store value and retrieve it again
    private fun <T> roundTrip(
        sample: Sample,
        property: KMutableProperty1<Sample, T>,
        function: (T) -> Unit
    ) {
        // Test managed objects
        realm.writeBlocking {
            copyToRealm(sample)
            val managedByteArray = query<Sample>()
                .first()
                .find { sampleObject ->
                    assertNotNull(sampleObject)
                    val binaryProperty = property.get(sampleObject)
                    binaryProperty
                }
            function(managedByteArray)
            cancelWrite()
        }
    }

    // Store value and retrieve it again
    private fun <T> roundTrip(
        byteArray: ByteArray,
        property: KMutableProperty1<Sample, T>,
        function: (T) -> Unit
    ) {
        roundTrip(Sample().apply { property.set(this, byteArray as T) }, property, function)
    }
}
