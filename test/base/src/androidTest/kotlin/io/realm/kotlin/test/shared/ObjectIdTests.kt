package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.ext.query
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.Sort
import io.realm.kotlin.query.find
import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.use
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.delay
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ObjectIdTests {

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
    fun invalid_arguments() {
        assertFailsWithMessage<IllegalArgumentException>("invalid hexadecimal representation of an ObjectId: []") {
            ObjectId.from("") // empty string
        }

        assertFailsWithMessage<IllegalArgumentException>("invalid hexadecimal representation of an ObjectId: [garbage]") {
            ObjectId.from("garbage") // 12 char needed
        }

        assertFailsWithMessage<IllegalArgumentException>("invalid hexadecimal representation of an ObjectId: [56X1fc72e0c917e9c4714161]") {
            ObjectId.from("56X1fc72e0c917e9c4714161") // invalid hex value
        }

        assertFailsWithMessage<IllegalArgumentException>("byte array size must be 12") {
            ObjectId.from(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
        }
    }

    @Test
    fun boundaries() {
        roundTrip(ObjectId.from("000000000000000000000000")) { value ->
            assertEquals(ObjectId.from("000000000000000000000000"), value)
        }

        val min = ObjectId.from(RealmInstant.MIN)
        roundTrip(min) { value ->
            assertEquals(min, value)
        }

        roundTrip(ObjectId.from("ffffffffffffffffffffffff")) { value ->
            assertEquals(ObjectId.from("ffffffffffffffffffffffff"), value)
        }

        val max = ObjectId.from(RealmInstant.MAX)
        roundTrip(max) { value ->
            assertEquals(max, value)
        }

        roundTrip(ObjectId.from("56e1fc72e0c917e9c4714161")) { value ->
            assertEquals(ObjectId.from("56e1fc72e0c917e9c4714161"), value)
        }

        val fromDate = ObjectId.from(RealmInstant.fromEpochSeconds(42, 42))
        roundTrip(fromDate) { value ->
            assertEquals(fromDate, value)
        }

        val bytes = byteArrayOf(
            0x56.toByte(),
            0xe1.toByte(),
            0xfc.toByte(),
            0x72.toByte(),
            0xe0.toByte(),
            0xc9.toByte(),
            0x17.toByte(),
            0xe9.toByte(),
            0xc4.toByte(),
            0x71.toByte(),
            0x41.toByte(),
            0x61.toByte()
        )
        roundTrip(ObjectId.from(bytes)) { value ->
            assertEquals(ObjectId.from(bytes), value)
        }
    }

    // Store value and retrieve it again
    private fun roundTrip(objectId: ObjectId, function: (ObjectId) -> Unit) {
        // Test managed objects
        realm.writeBlocking {
            copyToRealm(
                Sample().apply {
                    objectIdField = objectId
                }
            )
            val managedObjectId = query<Sample>()
                .first()
                .find { sampleObject ->
                    assertNotNull(sampleObject)
                    sampleObject.objectIdField
                }
            function(managedObjectId)
            cancelWrite() // So we can use .first()
        }
    }

    @Test
    fun equals() {
        assertEquals(ObjectId.from("56e1fc72e0c917e9c4714161"), ObjectId.from("56E1FC72E0C917E9C4714161"))
        val bytes = byteArrayOf(
            0x56.toByte(),
            0xe1.toByte(),
            0xfc.toByte(),
            0x72.toByte(),
            0xe0.toByte(),
            0xc9.toByte(),
            0x17.toByte(),
            0xe9.toByte(),
            0xc4.toByte(),
            0x71.toByte(),
            0x41.toByte(),
            0x61.toByte()
        )
        assertEquals(ObjectId.from(bytes), ObjectId.from(bytes))

        assertNotEquals(ObjectId.from(bytes), ObjectId.from("6281720cd500030571452df6"))
        assertNotEquals(ObjectId.create(), ObjectId.from("6281720cd500030571452df6"))
    }

    @Test
    fun to_String() {
        assertEquals(
            "56e1fc72e0c917e9c4714161",
            ObjectId.from("56E1FC72E0C917E9C4714161").toString()
        )
    }

    @Test
    fun compare() {
        val oid1 = ObjectId.from(RealmInstant.fromEpochSeconds(1, 0))
        val oid2 = ObjectId.from(RealmInstant.fromEpochSeconds(2, 0))
        val oid3 = ObjectId.from(RealmInstant.fromEpochSeconds(0, 0))
        val oid4 = ObjectId.from(RealmInstant.fromEpochSeconds(3, 0))
        val oid5 = ObjectId.from(RealmInstant.fromEpochSeconds(-1, 0))

        assertTrue(oid1.compareTo(oid2) < 0)
        assertTrue(oid1.compareTo(oid1) == 0)
        assertTrue(oid1.compareTo(oid3) > 0)
        assertTrue(oid1.compareTo(oid4) < 0)
        assertTrue(oid1.compareTo(oid5) > 0)
    }

    @Test
    fun queries() = runBlocking {
        val objectId1 = ObjectId.create()
        delay(100)
        val objectId2 = ObjectId.create()
        delay(100)
        val objectId3 = ObjectId.create()
        delay(100)

        val config = RealmConfiguration.Builder(setOf(Sample::class))
            .directory(tmpDir)
            .build()
        Realm.open(config).use { realm ->
            val objWithPK1 = Sample().apply {
                stringField = "obj1"
                objectIdField = objectId1
            }
            val objWithPK2 = Sample().apply {
                stringField = "obj2"
                objectIdField = objectId2
            }
            val objWithPK3 = Sample().apply {
                stringField = "obj3"
                objectIdField = objectId3
            }

            realm.writeBlocking {
                copyToRealm(objWithPK1)
                copyToRealm(objWithPK2)
                copyToRealm(objWithPK3)
            }

            val ids: RealmResults<Sample> =
                realm.query<Sample>().sort("objectIdField", Sort.ASCENDING).find()
            assertEquals(3, ids.size)

            assertEquals("obj1", ids[0].stringField)
            assertEquals(objectId1, ids[0].objectIdField)

            assertEquals("obj2", ids[1].stringField)
            assertEquals(objectId2, ids[1].objectIdField)

            assertEquals("obj3", ids[2].stringField)
            assertEquals(objectId3, ids[2].objectIdField)
        }
    }
}
