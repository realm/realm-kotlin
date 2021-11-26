package io.realm.test.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmInstant
import io.realm.entities.Sample
import io.realm.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RealmInstantTests {

    private lateinit var tmpDir: String
    private lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .path("$tmpDir/default.realm").build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (!realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    // Test both unmanaged and managed boundaries
    @Test
    fun timestamp_boundaries() {
        roundTrip(RealmInstant(Long.MIN_VALUE, -999_999_999)) { min ->
            assertEquals(Long.MIN_VALUE, min.epochSeconds)
            assertEquals(-999_999_999, min.nanosecondsOfSecond)
            assertEquals(RealmInstant.MIN, min)
        }

        roundTrip(RealmInstant(Long.MAX_VALUE, 999_999_999)) { max ->
            assertEquals(Long.MAX_VALUE, max.epochSeconds)
            assertEquals(999_999_999, max.nanosecondsOfSecond)
            assertEquals(RealmInstant.MAX, max)
        }

        roundTrip(RealmInstant(Long.MAX_VALUE, Int.MAX_VALUE)) { maxOverflow ->
            assertEquals(RealmInstant.MAX, maxOverflow)
        }

        roundTrip(RealmInstant(Long.MAX_VALUE, 1_000_000_000)) { minOverflow ->
            assertEquals(RealmInstant.MAX, minOverflow)
        }

        roundTrip(RealmInstant(Long.MIN_VALUE, Int.MIN_VALUE)) { maxUnderflow ->
            assertEquals(RealmInstant.MIN, maxUnderflow)
        }

        roundTrip(RealmInstant(Long.MIN_VALUE, -1_000_000_000)) { minUnderflow ->
            assertEquals(RealmInstant.MIN, minUnderflow)
        }

        roundTrip(RealmInstant(0, 0)) { zero ->
            assertEquals(0, zero.epochSeconds)
            assertEquals(0, zero.nanosecondsOfSecond)
        }

        roundTrip(RealmInstant(0, 1)) { zeroPlusOne ->
            assertEquals(0, zeroPlusOne.epochSeconds)
            assertEquals(1, zeroPlusOne.nanosecondsOfSecond)
        }

        roundTrip(RealmInstant(0, -1)) { zeroMinusOne ->
            assertEquals(0, zeroMinusOne.epochSeconds)
            assertEquals(-1, zeroMinusOne.nanosecondsOfSecond)
        }

        roundTrip(RealmInstant(-1, 2_000_000_200)) { crossingZeroFromNegative ->
            assertEquals(RealmInstant(1, 200), crossingZeroFromNegative)
        }

        roundTrip(RealmInstant(1, -2_000_000_200)) { crossingZeroFromPositive ->
            assertEquals(RealmInstant(-1, -200), crossingZeroFromPositive)
        }
    }

    // Store value and retrieve it again
    private fun roundTrip(timestamp: RealmInstant, function: (RealmInstant) -> Unit) {

        // Test unmanaged objects
        function(timestamp)

        // Test managed objects
        realm.writeBlocking {
            val sample = copyToRealm(
                Sample().apply {
                    timestampField = timestamp
                }
            )
            val managedTimestamp = objects(Sample::class).first().timestampField
            function(managedTimestamp)
            cancelWrite() // So we can use .first()
        }
    }

    @Test
    fun equals() {
        assertTrue(RealmInstant(42, 42) == (RealmInstant(42, 42)))
        assertFalse(RealmInstant(0, 0) == (RealmInstant(42, 42)))
    }

    @Test
    fun timestamp_hashCode() {
        assertEquals(RealmInstant(42, 42).hashCode(), (RealmInstant(42, 42).hashCode()))
        assertNotEquals(RealmInstant(0, 0).hashCode(), RealmInstant(42, 42).hashCode())
    }

    @Test
    fun timestamp_toString() {
        val ts = RealmInstant(100, 100)
        assertEquals("RealmInstant(epochSeconds=100, nanosecondsOfSecond=100)", ts.toString())
    }

    @Test
    fun compare() {
        val ts1 = RealmInstant(0, 0)
        val ts2 = RealmInstant(0, 1)
        val ts3 = RealmInstant(0, -1)
        val ts4 = RealmInstant(1, 0)
        val ts5 = RealmInstant(-1, 0)

        assertTrue(ts1.compareTo(ts2) < 0)
        assertTrue(ts1.compareTo(ts1) == 0)
        assertTrue(ts1.compareTo(ts3) > 0)
        assertTrue(ts1.compareTo(ts4) < 0)
        assertTrue(ts1.compareTo(ts5) > 0)
    }
}
