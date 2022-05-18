package io.realm.mongodb.internal

import io.realm.BaseRealm
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmSubscriptionSetPointer
import io.realm.internal.interop.RealmSubscriptionPointer
import io.realm.mongodb.sync.MutableSubscriptionSet
import io.realm.mongodb.sync.SubscriptionSet
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import io.realm.internal.interop.sync.CoreSubscriptionSetState
import io.realm.internal.interop.SubscriptionSetCallback
import io.realm.mongodb.exceptions.FlexibleSyncQueryException
import io.realm.internal.util.Validation
import io.realm.internal.ConfigurationImpl
import io.realm.internal.platform.freeze
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

internal class SubscriptionSetImpl<T : BaseRealm>(
    realm: T,
    nativePointer: RealmSubscriptionSetPointer
) : BaseSubscriptionSetImpl<T>(realm), SubscriptionSet<T> {

    override val nativePointer: AtomicRef<RealmSubscriptionSetPointer> = atomic(nativePointer)

    override suspend fun update(block: MutableSubscriptionSet.(realm: T) -> Unit): SubscriptionSet<T> {
        val ptr = RealmInterop.realm_sync_make_subscriptionset_mutable(nativePointer.value)
        val mut = MutableSubscriptionSetImpl(realm, ptr)
        mut.block(realm)
        nativePointer.value = RealmInterop.realm_sync_subscriptionset_commit(ptr)
        return this
    }

    override suspend fun waitForSynchronization(timeout: Duration): Boolean {
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
                            when(state) {
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
                        nativePointer.value,
                        CoreSubscriptionSetState.RLM_SYNC_SUBSCRIPTION_COMPLETE,
                        callback
                    )
                    channel.receive()
                }
            }
            when (result) {
                is Boolean -> {
                    if (result) {
                        refresh()
                        return true
                    } else {
                        throw FlexibleSyncQueryException(errorMessage!!)
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
        RealmInterop.realm_sync_subscriptionset_refresh(nativePointer.value)
        return this
    }
}
