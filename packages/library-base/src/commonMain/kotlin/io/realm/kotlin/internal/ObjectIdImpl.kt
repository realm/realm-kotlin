package io.realm.kotlin.internal

import io.realm.kotlin.internal.interop.OBJECT_ID_BYTES_SIZE
import io.realm.kotlin.internal.platform.epochInSeconds
import io.realm.kotlin.internal.util.HEX_PATTERN
import io.realm.kotlin.internal.util.parseHex
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

private fun RealmInstant.toEpochMillis(): Long = (epochSeconds.seconds + nanosecondsOfSecond.nanoseconds).inWholeMilliseconds

@Suppress("MagicNumber")
// Public as constructor is inlined in accessor converter method (Converters.kt)
public class ObjectIdImpl : ObjectId {

    private val inner: org.mongodb.kbson.ObjectId

    /**
     * Represents an ObjectID from an array of 12 bytes.
     */
    public val bytes: ByteArray
        get() = inner.toByteArray()

    /**
     * Constructs a new instance using the given timestamp.
     *
     * @param timestamp the timestamp.
     */
    public constructor(timestamp: RealmInstant = RealmInstant.from(epochInSeconds(), 0)
    ) : this(org.mongodb.kbson.ObjectId(timestamp.toEpochMillis()))

    /**
     * Constructs a new instance using the given timestamp.
     *
     * @param epochSeconds the number of seconds since the Unix epoch
     */
    public constructor(epochSeconds: Int) : this(org.mongodb.kbson.ObjectId(epochSeconds.toLong()*1000))

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
        this.inner = org.mongodb.kbson.ObjectId(bytes)
    }

    private constructor(inner: org.mongodb.kbson.ObjectId) {
        this.inner = inner
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ObjectIdImpl

        if (inner != other.inner) return false

        return true
    }

    override fun hashCode(): Int = inner.hashCode()

    override operator fun compareTo(other: ObjectId): Int {
        for (i in 0 until OBJECT_ID_BYTES_SIZE) {
            if (this.bytes[i] != (other as ObjectIdImpl).bytes[i]) {
                return if (this.bytes[i] < other.bytes[i]) -1 else 1
            }
        }
        return 0
    }

    override fun toString(): String {
        return inner.toHexString()
    }

    private companion object {
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
    }
}
