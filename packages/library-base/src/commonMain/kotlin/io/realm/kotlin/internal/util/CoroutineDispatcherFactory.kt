package io.realm.kotlin.internal.util

import io.realm.kotlin.internal.platform.multiThreadDispatcher
import io.realm.kotlin.internal.platform.singleThreadDispatcher
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Factory wrapper for passing around dispatchers without needing to create them. This makes it
 * possible to configure dispatchers in Configuration objects, but allow Realm instances to control
 * their lifecycle.
 */
public class CoroutineDispatcherFactory private constructor(
    private val isManaged: Boolean,
    private val createDispatcher: () -> CoroutineDispatcher,
) {
    public companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        public fun internal(name: String, threads: Int = 1): CoroutineDispatcherFactory {
            return CoroutineDispatcherFactory(isManaged = true) {
                when (threads) {
                    1 -> singleThreadDispatcher(name)
                    else -> multiThreadDispatcher(threads)
                }
            }
        }

        public fun external(dispatcher: CoroutineDispatcher): CoroutineDispatcherFactory {
            return CoroutineDispatcherFactory(isManaged = false) {
                dispatcher
            }
        }
    }

    public fun create(): ManagedCoroutineDispatcher {
        return ManagedCoroutineDispatcher(createDispatcher(), isManaged)
    }
}

/**
 * Class that tracks if a Coroutine Dispatcher was created by Realm or is external.
 * Dispatchers that are internal should follow the Realm lifecycle and be closed, when the Realm
 * is closed.
 */
public class ManagedCoroutineDispatcher(
    private val dispatcher: CoroutineDispatcher,
    private val isManaged: Boolean
) {

    public fun get(): CoroutineDispatcher {
        return dispatcher
    }

    public fun closeIfInternal(): Boolean {
        return if (isManaged && dispatcher is CloseableCoroutineDispatcher) {
            dispatcher.close()
            true
        } else {
            false
        }
    }
}
