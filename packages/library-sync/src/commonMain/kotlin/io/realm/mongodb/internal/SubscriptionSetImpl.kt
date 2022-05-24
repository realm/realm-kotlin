package io.realm.mongodb.internal

import io.realm.BaseRealm
import io.realm.internal.BaseRealmImpl
import io.realm.internal.RealmImpl
import io.realm.internal.interop.RealmBaseSubscriptionSetPointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmSubscriptionSetPointer
import io.realm.internal.interop.SubscriptionSetCallback
import io.realm.internal.interop.sync.CoreSubscriptionSetState
import io.realm.internal.platform.freeze
import io.realm.internal.util.Validation
import io.realm.mongodb.exceptions.BadFlexibleSyncQueryException
import io.realm.mongodb.sync.MutableSubscriptionSet
import io.realm.mongodb.sync.SubscriptionSet
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

internal class SubscriptionSetImpl<T : BaseRealm>(
    realm: T,
    nativePointer: RealmSubscriptionSetPointer
) : BaseSubscriptionSetImpl<T>(realm), SubscriptionSet<T> {

    private val _nativePointer: AtomicRef<RealmSubscriptionSetPointer> = atomic(nativePointer)
    override val nativePointer: RealmSubscriptionSetPointer
        get() = _nativePointer.value

    override fun getIteratorSafePointer(): RealmBaseSubscriptionSetPointer {
        return if (realm.isClosed()) {
            // If the Realm is closed, no further changes can happen to the SubscriptionSet,
            // So just return the current set
            nativePointer
        } else {
            RealmInterop.realm_sync_get_latest_subscriptionset(
                (realm as BaseRealmImpl).realmReference.dbPointer
            )
        }
    }

    override suspend fun update(block: MutableSubscriptionSet.(realm: T) -> Unit): SubscriptionSet<T> {
        checkClosed()
        val ptr = RealmInterop.realm_sync_make_subscriptionset_mutable(nativePointer)
        val mut = MutableSubscriptionSetImpl(realm, ptr)
        try {
            mut.block(realm)
            _nativePointer.value = RealmInterop.realm_sync_subscriptionset_commit(ptr)
        } finally {
            // Manually release the MutableSubscriptionSetPointer as it holds on to DB resources
            // that should not be controlled by the GC.
            RealmInterop.realm_release(ptr)
        }
        return this
    }

    override suspend fun waitForSynchronization(timeout: Duration): Boolean {
        checkClosed()
        Validation.require(timeout.isPositive()) {
            "'timeout' must be > 0. It was: $timeout"
        }

        // Channel to work around not being able to use `suspendCoroutine` to wrap the callback, as
        // that results in the `Continuation` being frozen, which breaks it.
        val channel = Channel<Any>(1)
        try {
            val result: Any = withTimeout(timeout) {
                withContext((realm.configuration as SyncConfigurationImpl).notificationDispatcher) {
                    val callback = object : SubscriptionSetCallback {
                        override fun onChange(state: CoreSubscriptionSetState) {
                            when (state) {
                                CoreSubscriptionSetState.RLM_SYNC_SUBSCRIPTION_COMPLETE -> {
                                    channel.trySend(true)
                                }
                                CoreSubscriptionSetState.RLM_SYNC_SUBSCRIPTION_ERROR -> {
                                    channel.trySend(false)
                                }
                                else -> {
                                    // Ignore all other states, wait for either complete or error.
                                }
                            }
                        }
                    }.freeze()
                    RealmInterop.realm_sync_on_subscriptionset_state_change_async(
                        nativePointer,
                        CoreSubscriptionSetState.RLM_SYNC_SUBSCRIPTION_COMPLETE,
                        callback
                    )
                    channel.receive()
                }
            }
            refresh()
            // Also refresh the Realm as the data has only been written on a background thread
            // when this is called. So the user facing Realm might not see the data yet.
            //
            if (realm is RealmImpl) {
                realm.refresh()
            } else {
                // Currently we only support accessing subscriptions through
                // `Realm.subscriptions`.
                TODO("Calling `waitForSynchronization` on this type of Realm is not supported: $realm")
            }

            when (result) {
                is Boolean -> {
                    if (result) {
                        return true
                    } else {
                        throw BadFlexibleSyncQueryException(errorMessage!!)
                    }
                }
                else -> throw IllegalStateException("Unexpected value: $result")
            }
        } catch (ex: TimeoutCancellationException) {
            // Don't throw if timeout is hit, instead just return false per the API contract.
            return false
        } finally {
            channel.close()
        }
    }

    override fun refresh(): SubscriptionSet<T> {
        checkClosed()
        RealmInterop.realm_sync_subscriptionset_refresh(nativePointer)
        return this
    }
}
