package io.realm.kotlin.internal

import io.realm.kotlin.internal.interop.OBJECT_ID_BYTES_SIZE
import io.realm.kotlin.internal.platform.epochInSeconds
import io.realm.kotlin.internal.util.HEX_PATTERN
import io.realm.kotlin.internal.util.parseHex
import io.realm.kotlin.internal.util.toHexString
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import kotlin.random.Random

@Suppress("MagicNumber")
// Public as constructor is inlined in accessor converter method (Converters.kt)
public class ObjectIdImpl : ObjectId {
    /**
     * Represents an ObjectID from an array of 12 bytes.
     */
    public val bytes: ByteArray

    /**
     * Time in seconds from Unix epoch.
     */
    private val timestamp: Int

    /**
     * The incrementing counter.
     */
    private val counter: Int

    /**
     * The first four bytes of randomness.
     */
    private val randomValue1: Int

    /**
     * The last two bytes of randomness.
     */
    private val randomValue2: Short

    /**
     * Constructs a new instance using the given timestamp.
     *
     * @param timestamp the timestamp.
     */
    public constructor(
        timestamp: RealmInstant = RealmInstant.from(
            epochInSeconds(),
            0
        )
    ) : this(
        timestamp.epochSeconds.toInt(),
        NEXT_COUNTER.incrementAndGet() and LOW_ORDER_THREE_BYTES
    )

    /**
     * Constructs a new instance using the given timestamp.
     *
     * @param epochSeconds the number of seconds since the Unix epoch
     */
    public constructor(epochSeconds: Int) : this(
        epochSeconds,
        NEXT_COUNTER.incrementAndGet() and LOW_ORDER_THREE_BYTES
    )

    /**
     * Constructs a new instance from a 24-byte hexadecimal string representation.
     *
     * @param hexString the string to convert
     * @throws IllegalArgumentException if the string is not a valid hex string representation of an ObjectId
     */
    public constructor(hexString: String) : this(parseObjectIdString(hexString))

    /**
     * Constructs a new instance from the given unsigned byte array
     *
     * @param bytes the ByteBuffer
     * @throws IllegalArgumentException if the buffer is null or does not have at least 12 bytes remaining
     */
    public constructor(bytes: ByteArray) {
        if (bytes.size != OBJECT_ID_BYTES_SIZE) {
            throw IllegalArgumentException("byte array size must be $OBJECT_ID_BYTES_SIZE")
        }
        timestamp =
            makeInt(bytes[0], bytes[1], bytes[2], bytes[3])
        randomValue1 = makeInt(0.toByte(), bytes[4], bytes[5], bytes[6])
        randomValue2 = makeShort(bytes[7], bytes[8])
        counter = makeInt(0.toByte(), bytes[9], bytes[10], bytes[11])
        this.bytes = bytes
    }

    private constructor(timestamp: Int, counter: Int) : this(
        timestamp,
        RANDOM_VALUE1,
        RANDOM_VALUE2,
        counter
    )

    private constructor(
        timestamp: Int,
        randomValue1: Int,
        randomValue2: Short,
        counter: Int
    ) {
        if (randomValue1 and -0x1000000 != 0) {
            throw IllegalArgumentException("The random value must be between 0 and 16777215 (it must fit in three bytes).")
        }
        if (counter and -0x1000000 != 0) {
            throw IllegalArgumentException("The counter must be between 0 and 16777215 (it must fit in three bytes).")
        }
        this.timestamp = timestamp
        this.counter = counter and LOW_ORDER_THREE_BYTES
        this.randomValue1 = randomValue1
        this.randomValue2 = randomValue2
        this.bytes = toByteArray()
    }

    /**
     * Convert to a byte array.  Note that the numbers are stored in big-endian order.
     *
     * @return the byte array
     */
    private fun toByteArray(): ByteArray {
        val buffer = ByteArray(OBJECT_ID_BYTES_SIZE)

        buffer[0] = int3(timestamp)
        buffer[1] = int2(timestamp)
        buffer[2] = int1(timestamp)
        buffer[3] = int0(timestamp)
        buffer[4] = int2(randomValue1)
        buffer[5] = int1(randomValue1)
        buffer[6] = int0(randomValue1)
        buffer[7] = short1(randomValue2)
        buffer[8] = short0(randomValue2)
        buffer[9] = int2(counter)
        buffer[10] = int1(counter)
        buffer[11] = int0(counter)

        return buffer
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) {
            return true
        }
        if (o == null || this::class != o::class) {
            return false
        }
        val objectId = o as ObjectIdImpl
        if (counter != objectId.counter) {
            return false
        }
        if (timestamp != objectId.timestamp) {
            return false
        }
        if (randomValue1 != objectId.randomValue1) {
            return false
        }
        return randomValue2 == objectId.randomValue2
    }

    override fun hashCode(): Int {
        var result = timestamp
        result = 31 * result + counter
        result = 31 * result + randomValue1
        result = 31 * result + randomValue2
        return result
    }

    override operator fun compareTo(other: ObjectId): Int {
        for (i in 0 until OBJECT_ID_BYTES_SIZE) {
            if (this.bytes[i] != (other as ObjectIdImpl).bytes[i]) {
                return if (this.bytes[i] < other.bytes[i]) -1 else 1
            }
        }
        return 0
    }

    override fun toString(): String {
        return bytes.toHexString()
    }

    private companion object {
        private const val LOW_ORDER_THREE_BYTES = 0x00ffffff

        // Use primitives to represent the 5-byte random value.
        private val RANDOM_VALUE1 = Random.nextInt(0x01000000)
        private val RANDOM_VALUE2: Short = Random.nextInt(0x00008000).toShort()

        private val NEXT_COUNTER: AtomicInt =
            atomic(Random.nextInt())

        private val OBJECT_ID_REGEX by lazy {
            "$HEX_PATTERN{24}".toRegex()
        }

        /**
         * Checks if a string could be an `ObjectId`.
         *
         * @param hexString a potential ObjectId as a String.
         * @return whether the string could be an object id
         * @throws IllegalArgumentException if hexString is null
         */
        private fun parseObjectIdString(hexString: String): ByteArray {
            if (!OBJECT_ID_REGEX.matches(hexString)) {
                throw IllegalArgumentException("invalid hexadecimal representation of an ObjectId: [$hexString]")
            }
            return hexString.parseHex()
        }

        // Big-Endian helpers, in this class because all other BSON numbers are little-endian
        private fun makeInt(b3: Byte, b2: Byte, b1: Byte, b0: Byte): Int {
            return b3.toInt() shl 24 or
                (b2.toInt() and 0xff shl 16) or
                (b1.toInt() and 0xff shl 8) or
                (b0.toInt() and 0xff)
        }

        private fun makeShort(b1: Byte, b0: Byte): Short {
            return (b1.toInt() and 0xff shl 8 or (b0.toInt() and 0xff)).toShort()
        }

        private fun int3(x: Int): Byte {
            return (x shr 24).toByte()
        }

        private fun int2(x: Int): Byte {
            return (x shr 16).toByte()
        }

        private fun int1(x: Int): Byte {
            return (x shr 8).toByte()
        }

        private fun int0(x: Int): Byte {
            return x.toByte()
        }

        private fun short1(x: Short): Byte {
            return (x.toInt() shr 8).toByte()
        }

        private fun short0(x: Short): Byte {
            return x.toByte()
        }
    }
}
