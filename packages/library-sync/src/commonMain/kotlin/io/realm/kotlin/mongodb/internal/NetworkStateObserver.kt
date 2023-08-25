package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.internal.interop.SynchronizableObject
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic

// Register a system specific network listener (if supported)
internal expect fun registerSystemNetworkObserver()

/**
 * This class is responsible for keeping track of system events related to the network so it can
 * delegate them to interested parties.
 */
internal object NetworkStateObserver {

    internal fun interface ConnectionListener {
        fun onChange(connectionAvailable: Boolean)
    }

    private val mutex = SynchronizableObject()
    private var isConnected = atomic(true)
    private val listeners = mutableListOf<ConnectionListener>()

    init {
        registerSystemNetworkObserver()
    }

    /**
     * Called by each custom network implementation whenever a network change is detected.
     */
    fun notifyConnectionChange(isOnline: Boolean) {
        if (isConnected.compareAndSet(expect = !isOnline, update = isOnline)) {
            mutex.withLock {
                listeners.forEach {
                    it.onChange(isOnline)
                }
            }
        }
    }

    /**
     * Add a listener to be notified about any network changes.
     * This method is thread safe.
     * IMPORTANT: Not removing it again will result in leaks.
     * @param listener the listener to add.
     */
    fun addListener(listener: ConnectionListener) {
        mutex.withLock {
            listeners.add(listener)
        }
    }

    /**
     * Removes a network listener.
     * This method is thread safe.
     *
     * @param listener the listener to remove.
     * @return `true` if the listener was removed.
     */
    fun removeListener(listener: ConnectionListener): Boolean {
        mutex.withLock {
            return listeners.remove(listener)
        }
    }
}
