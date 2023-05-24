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
package io.realm.kotlin

import io.realm.kotlin.internal.InternalConfiguration
import io.realm.kotlin.internal.RealmImpl
import io.realm.kotlin.internal.interop.Constants
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.platform.fileExists
import io.realm.kotlin.internal.platform.isWindows
import io.realm.kotlin.notifications.RealmChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * A Realm instance is the main entry point for interacting with a persisted realm.
 *
 * @see Configuration
 */
public interface Realm : TypedRealm {

    // FIXME Should this go to the end according to Kotlin conventions
    public companion object {
        /**
         * Default name for realm files unless overridden by [Configuration.SharedBuilder.name].
         */
        public const val DEFAULT_FILE_NAME: String = "default.realm"

        /**
         * Default tag used by log entries
         */
        public const val DEFAULT_LOG_TAG: String = "REALM"

        /**
         * The required length for encryption keys used to encrypt Realm data.
         */
        public const val ENCRYPTION_KEY_LENGTH: Int = Constants.ENCRYPTION_KEY_LENGTH

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
                totalBytes > thresholdSize && usedBytes.toDouble() / totalBytes.toDouble() <= 0.5
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
        public fun open(configuration: Configuration): Realm {
            return RealmImpl.create(configuration as InternalConfiguration)
        }

        /**
         * Deletes the realm file along with other related temporary files specified by the given
         * [RealmConfiguration] from the filesystem. The temporary file with ".lock" extension won't
         * be deleted.
         *
         * All Realm instances pointing to the same file must be closed before calling this method.
         *
         * **WARNING**: For synchronized realms there is a chance that an internal Realm instance on
         * the background thread is not closed even though the user controlled Realm instances are
         * closed. This will result in an `IllegalStateException`. See issue
         * https://github.com/realm/realm-java/issues/5416 for more details.
         *
         * @param configuration a [Configuration] object that defines the Realm.
         * @throws IllegalStateException if an error occurred while deleting the Realm files.
         */
        public fun deleteRealm(configuration: Configuration) {
            if (!fileExists(configuration.path)) return
            RealmInterop.realm_delete_files(configuration.path)
        }

        /**
         * Compacts the Realm file defined by the given configuration. Compaction can only succeed
         * if all references to the Realm file has been closed.
         *
         * This method is not available on Windows (JVM), and will throw an [NotImplementedError]
         * there.
         *
         * @param configuration configuration for the Realm to compact.
         * @return `true` if compaction succeeded, `false` if not.
         */
        public fun compactRealm(configuration: Configuration): Boolean {
            if (isWindows()) {
                throw NotImplementedError("Realm.compact() is not supported on Windows. See https://github.com/realm/realm-core/issues/4111 for more information.")
            }
            if (!fileExists(configuration.path)) return false
            val config = (configuration as InternalConfiguration)
            val (dbPointer, _) = RealmInterop.realm_open(config.createNativeConfiguration())
            return RealmInterop.realm_compact(dbPointer).also {
                RealmInterop.realm_close(dbPointer)
            }
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
    public override fun <T : BaseRealmObject> query(
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
     * @return any value returned from the provided write block. If this is a [RealmObject] it is
     * frozen before being returned.
     */
    public suspend fun <R> write(block: MutableRealm.() -> R): R

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
    public fun <R> writeBlocking(block: MutableRealm.() -> R): R

    /**
     * Observe changes to the realm. The flow will emit a [RealmChange] once subscribed and then, on
     * every change to the realm. The flow will continue running indefinitely until canceled or the
     * realm instance is closed.
     *
     * The change calculations will run on the thread defined through the [Configuration]
     * Notification Dispatcher.
     *
     * The flow has an internal buffer of [Channel.BUFFERED] but if the consumer fails to consume
     * the elements in a timely manner the coroutine scope will be cancelled with a
     * [CancellationException].
     *
     * @return a flow representing changes to this realm.
     */
    public fun asFlow(): Flow<RealmChange<Realm>>

    /**
     * Writes a compacted copy of the Realm to the given destination as defined by the
     * [targetConfiguration]. The resulting file can be used for a number of purposes:
     *
     * - Backup of a local realm.
     * - Backup of a synchronized realm, but all local changes must be uploaded first.
     * - Convert a local realm to a partition-based realm.
     * - Convert a synchronized (partition-based or flexible) realm to a local realm.
     *
     * Encryption can be configured for the target Realm independently from the current Realm.
     *
     * The destination file cannot already exist.
     *
     * @param targetConfiguration configuration that defines what type of backup to make and where
     * to write it by using [Configuration.path].
     * @throws IllegalArgumentException if [targetConfiguration] points to a file that already
     * exists.
     * @throws IllegalArgumentException if [targetConfiguration] has Flexible Sync enabled and
     * the Realm being copied doesn't.
     * @throws IllegalStateException if this Realm is a synchronized Realm, and not all client
     * changes are integrated in the server.
     */
    public fun writeCopyTo(targetConfiguration: Configuration)

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
    public fun close()
}
