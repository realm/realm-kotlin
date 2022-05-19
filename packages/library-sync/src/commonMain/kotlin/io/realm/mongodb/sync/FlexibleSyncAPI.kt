package io.realm.mongodb.sync

import io.realm.BaseRealm
import io.realm.RealmInstant
import io.realm.RealmObject
import io.realm.internal.interop.sync.CoreSubscriptionSetState
import io.realm.mongodb.exceptions.FlexibleSyncQueryException
import io.realm.query.RealmQuery
import kotlin.reflect.KClass
import kotlin.time.Duration

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
     * @param timeout how long to wait for the synchronization to either succeed or fail.
     * @return `true` if all current subscriptions were accepted by the server and data has
     * been downloaded, or `false` the [timeout] was hit before all data could be downloaded.
     * @throws FlexibleSyncQueryException if the server did not accept the set of queries. The
     * exact reason is found in the exception message. The [SubscriptionSet] will also enter a
     * [SubscriptionSetState.ERROR] state.
     */
    public suspend fun waitForSynchronization(timeout: Duration = Duration.INFINITE): Boolean

    /**
     * TODO
     */
    public fun refresh(): SubscriptionSet<T>
}

/**
 * TODO
 */
public interface MutableSubscriptionSet : BaseSubscriptionSet {
    /**
     * Adding a query that already exists is a no-op and the existing subscription will be returned.
     */
    public fun <T : RealmObject> add(query: RealmQuery<T>, name: String? = null, updateExisting: Boolean = false): Subscription

    /**
     * TODO
     */
    public fun remove(subscription: Subscription): Boolean
    /**
     * TODO
     */
    public fun remove(name: String): Boolean
    /**
     * TODO
     */
    public fun removeAll(objectType: String): Boolean
    /**
     * TODO
     */
    public fun <T : RealmObject> removeAll(type: KClass<T>): Boolean
    /**
     * TODO
     */
    public fun removeAll(): Boolean

    /**
     * Creates an anonymous [Subscription] in the current [MutableSubscriptionSet] directly from
     * a [RealmQuery].
     *
     * @return the [Subscription] that was added.
     */
    public fun RealmQuery<out RealmObject>.subscribe(): Subscription = add(this)

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
    public fun RealmQuery<out RealmObject>.subscribe(name: String, updateExisting: Boolean = false): Subscription =
        add(this, name, updateExisting)
}
