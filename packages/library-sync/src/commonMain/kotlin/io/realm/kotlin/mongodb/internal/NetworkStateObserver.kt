package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.internal.interop.SynchronizableObject

// Register a system specific network listener (if supported)
internal expect fun registerSystemNetworkObserver()

/**
 * This class is responsible for keeping track of system events related to the network so it can
 * delegate them to interested parties.
 */
internal object NetworkStateObserver {

    /**
     * This interface is used in a thread-safe manner, i.e. implementers do not have to think
     * about race conditions.
     */
    internal fun interface ConnectionListener {
        fun onChange(connectionAvailable: Boolean)
    }

    private val mutex = SynchronizableObject()
    private val listeners = mutableListOf<ConnectionListener>()

    init {
        registerSystemNetworkObserver()
    }

    /**
     * Called by each custom network implementation whenever a network change is detected.
     */
    fun notifyConnectionChange(isOnline: Boolean) {
        mutex.withLock {
            listeners.forEach {
                it.onChange(isOnline)
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
