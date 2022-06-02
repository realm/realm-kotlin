package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.RealmInstant
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.query
import io.realm.kotlin.query.find
import io.realm.kotlin.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RealmInstantTests {

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

    // Test both unmanaged and managed boundaries
    @Test
    fun timestamp_boundaries() {
        roundTrip(RealmInstant.from(Long.MIN_VALUE, -999_999_999)) { min ->
            assertEquals(Long.MIN_VALUE, min.epochSeconds)
            assertEquals(-999_999_999, min.nanosecondsOfSecond)
            assertEquals(RealmInstant.MIN, min)
        }

        roundTrip(RealmInstant.from(Long.MAX_VALUE, 999_999_999)) { max ->
            assertEquals(Long.MAX_VALUE, max.epochSeconds)
            assertEquals(999_999_999, max.nanosecondsOfSecond)
            assertEquals(RealmInstant.MAX, max)
        }

        roundTrip(RealmInstant.from(Long.MAX_VALUE, Int.MAX_VALUE)) { maxOverflow ->
            assertEquals(RealmInstant.MAX, maxOverflow)
        }

        roundTrip(RealmInstant.from(Long.MAX_VALUE, 1_000_000_000)) { minOverflow ->
            assertEquals(RealmInstant.MAX, minOverflow)
        }

        roundTrip(RealmInstant.from(Long.MIN_VALUE, Int.MIN_VALUE)) { maxUnderflow ->
            assertEquals(RealmInstant.MIN, maxUnderflow)
        }

        roundTrip(RealmInstant.from(Long.MIN_VALUE, -1_000_000_000)) { minUnderflow ->
            assertEquals(RealmInstant.MIN, minUnderflow)
        }

        roundTrip(RealmInstant.from(0, 0)) { zero ->
            assertEquals(0, zero.epochSeconds)
            assertEquals(0, zero.nanosecondsOfSecond)
        }

        roundTrip(RealmInstant.from(0, 1)) { zeroPlusOne ->
            assertEquals(0, zeroPlusOne.epochSeconds)
            assertEquals(1, zeroPlusOne.nanosecondsOfSecond)
        }

        roundTrip(RealmInstant.from(0, -1)) { zeroMinusOne ->
            assertEquals(0, zeroMinusOne.epochSeconds)
            assertEquals(-1, zeroMinusOne.nanosecondsOfSecond)
        }

        roundTrip(RealmInstant.from(-1, 2_000_000_200)) { crossingZeroFromNegative ->
            assertEquals(RealmInstant.from(1, 200), crossingZeroFromNegative)
        }

        roundTrip(RealmInstant.from(1, -2_000_000_200)) { crossingZeroFromPositive ->
            assertEquals(RealmInstant.from(-1, -200), crossingZeroFromPositive)
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
            val managedTimestamp = query<Sample>()
                .first()
                .find { sampleObject ->
                    assertNotNull(sampleObject)
                    sampleObject.timestampField
                }
            function(managedTimestamp)
            cancelWrite() // So we can use .first()
        }
    }

    @Test
    fun equals() {
        assertTrue(RealmInstant.from(42, 42) == (RealmInstant.from(42, 42)))
        assertFalse(RealmInstant.from(0, 0) == (RealmInstant.from(42, 42)))
        assertFalse(RealmInstant.from(42, 0) == (RealmInstant.from(42, 42)))
        assertFalse(RealmInstant.from(0, 42) == (RealmInstant.from(42, 42)))
    }

    @Test
    fun timestamp_hashCode() {
        assertEquals(
            RealmInstant.from(42, 42).hashCode(),
            (RealmInstant.from(42, 42).hashCode())
        )
        assertNotEquals(
            RealmInstant.from(0, 0).hashCode(),
            RealmInstant.from(42, 42).hashCode()
        )
        assertNotEquals(
            RealmInstant.from(42, 0).hashCode(),
            RealmInstant.from(42, 42).hashCode()
        )
        assertNotEquals(
            RealmInstant.from(0, 42).hashCode(),
            RealmInstant.from(42, 42).hashCode()
        )
    }

    @Test
    fun timestamp_toString() {
        val ts = RealmInstant.from(42, 420)
        assertEquals("RealmInstant(epochSeconds=42, nanosecondsOfSecond=420)", ts.toString())
    }

    @Test
    fun compare() {
        val ts1 = RealmInstant.from(0, 0)
        val ts2 = RealmInstant.from(0, 1)
        val ts3 = RealmInstant.from(0, -1)
        val ts4 = RealmInstant.from(1, 0)
        val ts5 = RealmInstant.from(-1, 0)

        assertTrue(ts1.compareTo(ts2) < 0)
        assertTrue(ts1.compareTo(ts1) == 0)
        assertTrue(ts1.compareTo(ts3) > 0)
        assertTrue(ts1.compareTo(ts4) < 0)
        assertTrue(ts1.compareTo(ts5) > 0)
    }
}
