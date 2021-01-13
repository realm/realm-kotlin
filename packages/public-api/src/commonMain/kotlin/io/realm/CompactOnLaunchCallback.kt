package io.realm

interface CompactOnLaunchCallback {
    fun shouldCompact(totalBytes: Long, usedBytes: Long): Boolean

}
