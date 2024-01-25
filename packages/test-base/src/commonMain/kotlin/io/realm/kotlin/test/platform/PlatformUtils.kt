package io.realm.kotlin.test.platform

import io.realm.kotlin.test.util.Utils
import kotlin.time.Duration

// Platform dependant helper methods
expect object PlatformUtils {
    fun createTempDir(prefix: String = Utils.createRandomString(16), readOnly: Boolean = false): String
    fun deleteTempDir(path: String)
    fun sleep(duration: Duration)
    fun threadId(): ULong
    fun triggerGC()

    /**
     * Allocate a 64 byte array in native memory that contains the encryption key to be used.
     *
     * @param aesKey the value of the byte array to be copied.
     * @return the address pointer to the memory region allocated.
     */
    fun allocateEncryptionKeyOnNativeMemory(aesKey: ByteArray): Long

    /**
     * Zero-out and release a previously written encryption key from native memory.
     */
    fun freeEncryptionKeyFromNativeMemory(aesKeyPointer: Long)
}
