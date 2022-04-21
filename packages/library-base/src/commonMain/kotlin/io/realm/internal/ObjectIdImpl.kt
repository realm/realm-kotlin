package io.realm.internal

import io.realm.ObjectId
import io.realm.RealmInstant
import io.realm.internal.interop.ObjectIdWrapper
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import kotlinx.datetime.Clock
import kotlin.random.Random

@OptIn(ExperimentalUnsignedTypes::class)
@Suppress("MagicNumber")
internal class ObjectIdImpl : ObjectId, ObjectIdWrapper {
    constructor(wrapper: ObjectIdWrapper) : this(wrapper.bytes)

    override val bytes: UByteArray
        get() = unsignedBytes

    /**
     * Represents an ObjectID from an array of 12 unsigned bytes
     */
    private val unsignedBytes: UByteArray

    /**
     * The timestamp
     */
    private val timestamp: Int

    /**
     * The counter.
     */
    private val counter: Int

    /**
     * the first four bits of randomness.
     */
    private val randomValue1: Int

    /**
     * The last two bits of randomness.
     */
    private val randomValue2: Short

    /**
     * Constructs a new instance using the given date.
     *
     * @param date the date
     */
    constructor(
        date: RealmInstant = RealmInstant.fromEpochSeconds(
            Clock.System.now().epochSeconds,
            0
        )
    ) : this(
        date.epochSeconds.toInt(),
        NEXT_COUNTER.incrementAndGet() and LOW_ORDER_THREE_BYTES
    )

    /**
     * Constructs a new instance using the given timestamp (Unix epoch).
     *
     * @param epochSeconds the number of seconds since the Unix epoch
     */
    constructor(epochSeconds: Long) : this(
        epochSeconds.toInt(),
        NEXT_COUNTER.incrementAndGet() and LOW_ORDER_THREE_BYTES
    )

    /**
     * Constructs a new instance from a 24-byte hexadecimal string representation.
     *
     * @param hexString the string to convert
     * @throws IllegalArgumentException if the string is not a valid hex string representation of an ObjectId
     */
    constructor(hexString: String) : this(parseHexString(hexString))

    /**
     * Constructs a new instance from the given unsigned byte array
     *
     * @param bytes the UByteBuffer
     * @throws IllegalArgumentException if the buffer is null or does not have at least 12 bytes remaining
     */
    constructor(bytes: UByteArray) {
        if (bytes.size != OBJECT_ID_LENGTH) {
            throw IllegalArgumentException("byte array size must be $OBJECT_ID_LENGTH")
        }
        timestamp =
            makeInt(bytes[0].toByte(), bytes[1].toByte(), bytes[2].toByte(), bytes[3].toByte())
        randomValue1 = makeInt(0.toByte(), bytes[4].toByte(), bytes[5].toByte(), bytes[6].toByte())
        randomValue2 = makeShort(bytes[7].toByte(), bytes[8].toByte())
        counter = makeInt(0.toByte(), bytes[9].toByte(), bytes[10].toByte(), bytes[11].toByte())
        this.unsignedBytes = bytes
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
        this.unsignedBytes = toUByteArray()
    }

    /**
     * Converts this instance into a 24-byte hexadecimal string representation.
     *
     * @return a string representation of the ObjectId in hexadecimal format
     */
    public fun toHexString(): String {
        val chars = CharArray(OBJECT_ID_LENGTH * 2)
        var i = 0
        for (b in toUByteArray()) {
            chars[i++] = HEX_CHARS[b.toInt() shr 4 and 0xF]
            chars[i++] = HEX_CHARS[b.toInt() and 0xF]
        }
        return chars.concatToString()
    }

    /**
     * Convert to a byte array.  Note that the numbers are stored in big-endian order.
     *
     * @return the byte array
     */
    private fun toUByteArray(): UByteArray {
        val buffer = UByteArray(OBJECT_ID_LENGTH)

        buffer[0] = int3(timestamp).toUByte()
        buffer[1] = int2(timestamp).toUByte()
        buffer[2] = int1(timestamp).toUByte()
        buffer[3] = int0(timestamp).toUByte()
        buffer[4] = int2(randomValue1).toUByte()
        buffer[5] = int1(randomValue1).toUByte()
        buffer[6] = int0(randomValue1).toUByte()
        buffer[7] = short1(randomValue2).toUByte()
        buffer[8] = short0(randomValue2).toUByte()
        buffer[9] = int2(counter).toUByte()
        buffer[10] = int1(counter).toUByte()
        buffer[11] = int0(counter).toUByte()

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
        for (i in 0 until OBJECT_ID_LENGTH) {
            if (this.unsignedBytes[i] != (other as ObjectIdImpl).unsignedBytes[i]) {
                return if (this.unsignedBytes[i].toInt() and 0xff < other.unsignedBytes[i].toInt() and 0xff) -1 else 1
            }
        }
        return 0
    }

    override fun toString(): String {
        return toHexString()
    }

    private companion object {
        private const val OBJECT_ID_LENGTH = 12
        private const val LOW_ORDER_THREE_BYTES = 0x00ffffff

        // Use primitives to represent the 5-byte random value.
        private val RANDOM_VALUE1 = Random.nextInt(0x01000000)
        private val RANDOM_VALUE2: Short = Random.nextInt(0x00008000).toShort()

        // TODO replace with stately AtomicInt
        private val NEXT_COUNTER: AtomicInt =
            atomic(Random.nextInt())
        private val HEX_CHARS = charArrayOf(
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
        )
        //
        // /**
        //  * Gets a new object id.
        //  *
        //  * @return the new id
        //  */
        // fun get(): ObjectId {
        //     return ObjectId()
        // }

        /**
         * Checks if a string could be an `ObjectId`.
         *
         * @param hexString a potential ObjectId as a String.
         * @return whether the string could be an object id
         * @throws IllegalArgumentException if hexString is null
         */
        @Suppress("ReturnCount", "LoopWithTooManyJumpStatements")
        private fun isValid(hexString: String?): Boolean {
            if (hexString == null) {
                throw IllegalArgumentException()
            }
            val len = hexString.length
            if (len != 24) {
                return false
            }
            for (i in 0 until len) {
                val c = hexString[i]
                if (c in '0'..'9') {
                    continue
                }
                if (c in 'a'..'f') {
                    continue
                }
                if (c in 'A'..'F') {
                    continue
                }
                return false
            }
            return true
        }

        private fun parseHexString(s: String): UByteArray {
            if (!isValid(s)) {
                throw IllegalArgumentException("invalid hexadecimal representation of an ObjectId: [$s]")
            }
            val b = UByteArray(OBJECT_ID_LENGTH)
            for (i in b.indices) {
                b[i] = s.substring(i * 2, i * 2 + 2).toInt(16).toUByte()
            }
            return b
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
