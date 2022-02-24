/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.realm

import io.realm.internal.InternalConfiguration
import io.realm.internal.RealmImpl
import io.realm.internal.interop.Constants
import io.realm.query.RealmQuery
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * A Realm instance is the main entry point for interacting with a persisted realm.
 *
 * @see Configuration
 */
interface Realm : TypedRealm {

    // FIXME Should this go to the end according to Kotlin conventions
    companion object {
        /**
         * Default name for realm files unless overridden by [Configuration.SharedBuilder.name].
         */
        const val DEFAULT_FILE_NAME = "default.realm"

        /**
         * Default tag used by log entries
         */
        const val DEFAULT_LOG_TAG = "REALM"

        /**
         * The required length for encryption keys used to encrypt Realm data.
         */
        const val ENCRYPTION_KEY_LENGTH = Constants.ENCRYPTION_KEY_LENGTH

        /**
         * The default implementation for determining if a file should be compacted or not. This
         * implementation will only trigger if the file is above 50 MB and 50% or more of the space
         * can be reclaimed.
         *
         * @see [RealmConfiguration.Builder.compactOnLaunch]
         */
        @Suppress("MagicNumber")
        public val DEFAULT_COMPACT_ON_LAUNCH_CALLBACK: CompactOnLaunchCallback =
            CompactOnLaunchCallback { totalBytes, usedBytes ->
                val thresholdSize = (50 * 1024 * 1024).toLong()
                totalBytes > thresholdSize && usedBytes.toDouble() / totalBytes.toDouble() >= 0.5
            }

        /**
         * Open a realm instance.
         *
         * This instance grants access to an underlying realm file defined by the provided
         * [Configuration].
         *
         * @param configuration the RealmConfiguration used to open the realm.
         *
         * @throws IllegalArgumentException on invalid Realm configurations.
         * @throws IllegalStateException if the schema has changed and migration failed.
         */
        fun open(configuration: Configuration): Realm {
            return RealmImpl(configuration as InternalConfiguration)
        }
    }

    /**
     * Returns a [RealmQuery] matching the predicate represented by [query].
     *
     * A reified version of this method is also available as an extension function,
     * `realm.query<YourClass>(...)`. Import `io.realm.query` to access it.
     *
     * The resulting query is lazily evaluated and will not perform any calculations until
     * [RealmQuery.find] is called or the [Flow] produced by [RealmQuery.asFlow] is collected.
     *
     * The results yielded by the query reflect the state of the realm at invocation time, so the
     * they do not change when the realm updates. You can access these results from any thread.
     *
     * @param query the Realm Query Language predicate to append.
     * @param args Realm values for the predicate.
     */
    override fun <T : RealmObject> query(
        clazz: KClass<T>,
        query: String,
        vararg args: Any?
    ): RealmQuery<T>

    /**
     * Modify the underlying Realm file in a suspendable transaction on the default Realm Write
     * Dispatcher.
     *
     * The write transaction always represent the latest version of data in the Realm file, even if
     * the calling realm not yet represent this.
     *
     * Write transactions automatically commit any changes made when the closure returns unless
     * [MutableRealm.cancelWrite] was called.
     *
     * @param block function that should be run within the context of a write transaction.
     * @return any value returned from the provided write block. If this is a RealmObject it is
     * frozen before being returned.
     * @see [Configuration.writeDispatcher]
     */
    suspend fun <R> write(block: MutableRealm.() -> R): R

    /**
     * Modify the underlying Realm file while blocking the calling thread until the transaction is
     * done. Write transactions automatically commit any changes made when the closure returns
     * unless [MutableRealm.cancelWrite] was called.
     *
     * The write transaction always represent the latest version of data in the Realm file, even if
     * the calling realm not yet represent this.
     *
     * @param block function that should be run within the context of a write transaction.
     * @return any value returned from the provided write block.
     *
     * @throws IllegalStateException if invoked inside an existing transaction.
     */
    fun <R> writeBlocking(block: MutableRealm.() -> R): R

    /**
     * Observe changes to the realm. If there is any change to the realm, the flow will emit the
     * updated realm. The flow will continue running indefinitely until canceled.
     *
     * The change calculations will run on the thread defined through the [Configuration]
     * Notification Dispatcher.
     *
     * @return a flow representing changes to this realm.
     */
    fun observe(): Flow<Realm>

    /**
     * Close this realm and all underlying resources. Accessing any methods or Realm Objects after
     * this method has been called will then an [IllegalStateException].
     *
     * This will block until underlying Realms (writer and notifier) are closed, including rolling
     * back any ongoing transactions when [close] is called. Calling this from the Realm Write
     * Dispatcher while inside a transaction block will throw, while calling this by some means of
     * a blocking operation on another thread (e.g. `runBlocking(Dispatcher.Default)`) inside a
     * transaction cause a deadlock.
     *
     * @throws IllegalStateException if called from the Realm Write Dispatcher while inside a
     * transaction block.
     */
    fun close()
}

/**
 * Returns a [RealmQuery] matching the predicate represented by [query].
 *
 * Reified convenience wrapper for [Realm.query].
 */
inline fun <reified T : RealmObject> Realm.query(
    query: String = "TRUEPREDICATE",
    vararg args: Any?
): RealmQuery<T> = query(T::class, query, *args)
