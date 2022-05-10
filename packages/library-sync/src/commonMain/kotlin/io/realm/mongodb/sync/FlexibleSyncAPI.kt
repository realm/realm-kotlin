package io.realm.mongodb.sync

import io.realm.BaseRealm
import io.realm.RealmInstant
import io.realm.RealmObject
import io.realm.internal.interop.sync.CoreSubscriptionSetState
import io.realm.query.RealmQuery
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// FIXME Split this file into seperate classes once the API settles

/**
 * TODO
 */
public interface Subscription {
    public val createdAt: RealmInstant
    public val updatedAt: RealmInstant
    public val name: String?
    public val objectType: String
    public val queryDescription: String

    /**
     * Converts the [Subscription.queryDescription] back to a [RealmQuery] that can be run against
     * the current state of the local Realm.
     *
     * @throws IllegalArgumentException if the type does not match the type of objects this query
     * can return.
     */
    public fun <T : RealmObject> asQuery(type: KClass<T>): RealmQuery<T>
}

public inline fun <reified T : RealmObject> Subscription.asQuery(): RealmQuery<T> =
    asQuery(T::class)

/**
 * TODO
 */
public enum class SubscriptionSetState {
    UNCOMMITTED,
    PENDING,
    BOOTSTRAPPING,
    COMPLETE,
    ERROR,
    SUPERCEDED;

    internal companion object {
        internal fun from(coreState: CoreSubscriptionSetState): SubscriptionSetState {
            return when (coreState) {
                CoreSubscriptionSetState.RLM_SYNC_SUBSCRIPTION_UNCOMMITTED ->
                    UNCOMMITTED
                CoreSubscriptionSetState.RLM_SYNC_SUBSCRIPTION_PENDING ->
                    PENDING
                CoreSubscriptionSetState.RLM_SYNC_BOOTSTRAPPING ->
                    BOOTSTRAPPING
                CoreSubscriptionSetState.RLM_SYNC_SUBSCRIPTION_COMPLETE ->
                    COMPLETE
                CoreSubscriptionSetState.RLM_SYNC_SUBSCRIPTION_ERROR ->
                    ERROR
                CoreSubscriptionSetState.RLM_SYNC_SUBSCRIPTION_SUPERSEDED ->
                    SUPERCEDED
                else -> TODO("Unsupported state: $coreState")
            }
        }
    }
}

/**
 * Base class for [SubscriptionSet] and [MutableSubscriptionSet]
 */
public interface BaseSubscriptionSet : Iterable<Subscription> {
    /**
     * TODO
     */
    public fun <T : RealmObject> findByQuery(query: RealmQuery<T>): Subscription?

    /**
     * TODO
     */
    public fun findByName(name: String): Subscription?

    /**
     * TODO
     */
    public val state: SubscriptionSetState

    /**
     * TODO
     */
    public val errorMessage: String?

    /**
     * TODO
     */
    public val size: Int
}

/**
 * TODO
 */
public interface SubscriptionSet<T : BaseRealm> : BaseSubscriptionSet {
    /**
     * TODO
     */
    public suspend fun update(block: MutableSubscriptionSet.(realm: T) -> Unit): SubscriptionSet<T>

    /**
     * Wait for the subscription set to synchronize with the server. It will return when the
     * server either accepts the set of queries and has downloaded data for them, or if an
     * error has occurred.
     *
     * If an error occurred, the underlying reason can be found through {@link #getErrorMessage()}.
     *
     * @param timeOut how long to wait for the synchronization to either succeed or fail.
     * @param unit unit of time used for the timeout.
     * @return {@code true} if all current subscriptions were accepted by the server and data has
     * been downloaded, or {@code false} if an error occurred.
     * @throws RuntimeException if the timeout is exceeded.
     */
    public suspend fun waitForSynchronization(timeout: Duration = 30.seconds): Boolean

    /**
     * TODO
     */
    public fun refresh(): SubscriptionSet<T>
}

/**
 * TODO
 */
public interface MutableSubscriptionSet : BaseSubscriptionSet {
    public fun <T : RealmObject> add(query: RealmQuery<T>, name: String = "", updateExisting: Boolean = false): Subscription
    public fun remove(subscription: Subscription): Boolean
    public fun remove(name: String): Boolean
    public fun removeAll(objectType: String): Boolean
    public fun <T : RealmObject> removeAll(type: KClass<T>): Boolean
    public fun removeAll(): Boolean

    /**
     * Creates an anonymous [Subscription] in the current [MutableSubscriptionSet] directly from
     * a [RealmQuery].
     *
     * @return the [Subscription] that was added.
     */
    public fun RealmQuery<*>.subscribe(): Subscription = add(this)

    /**
     * Creates a named [Subscription] in the current [MutableSubscriptionSet] directly from a
     * [RealmQuery].
     *
     * @param name name of the subscription.
     * @param updateExisting if a different query is already registered with the provided [name],
     * then set this to `true` to update the subscription. If set to `false` an exception is
     * thrown instead of updating the query.
     * @return the [Subscription] that was added or updated.
     * @throws IllegalArgumentException if [updateExisting] is false, and another query was already
     * registered with the given [name].
     */
    public fun RealmQuery<*>.subscribe(name: String, updateExisting: Boolean = false): Subscription =
        add(this, name, updateExisting)
}
