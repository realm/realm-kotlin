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

import io.realm.internal.RealmImpl
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * A Realm instance is the main entry point for interacting with a persisted Realm.
 *
 * @see RealmConfiguration
 */
// FIXME Observable
interface Realm : TypedRealm {

    // FIXME Should this go to the end according to Kotlin conventions
    companion object {
        /**
         * Default name for Realm files unless overridden by [RealmConfiguration.Builder.name].
         */
        public const val DEFAULT_FILE_NAME = "default.realm"

        /**
         * Default tag used by log entries
         */
        public const val DEFAULT_LOG_TAG = "REALM"

        /**
         * The required length for encryption keys used to encrypt Realm data.
         */
        public const val ENCRYPTION_KEY_LENGTH = io.realm.interop.Constants.ENCRYPTION_KEY_LENGTH

        /**
         * Open a Realm instance. This instance grants access to an underlying Realm file defined by
         * the provided [RealmConfiguration].
         *
         * @param configuration The RealmConfiguration used to open the Realm.
         */
        public fun open(configuration: RealmConfiguration): Realm {
            return RealmImpl(configuration)
        }
    }

    /**
     * Returns the results of querying for all objects of a specific type.
     *
     * The result reflects the state of the realm at invocation time, so the results
     * do not change when the realm updates. You can access these results from any thread.
     *
     * @param clazz The class of the objects to query for.
     * @return The result of the query as of the time of invoking this method.
     */
    override fun <T : RealmObject> objects(clazz: KClass<T>): RealmResults<T>

    /**
     * Modify the underlying Realm file in a suspendable transaction on the default Realm Write
     * Dispatcher.
     *
     * The write transaction always represent the latest version of data in the Realm file, even if
     * the calling Realm not yet represent this.
     *
     * Write transactions automatically commit any changes made when the closure returns unless
     * [MutableRealm.cancelWrite] was called.
     *
     * @param block function that should be run within the context of a write transaction.
     * @return any value returned from the provided write block. If this is a RealmObject it is
     * frozen before being returned.
     * @see [RealmConfiguration.writeDispatcher]
     */
    suspend fun <R> write(block: MutableRealm.() -> R): R

    /**
     * Modify the underlying Realm file while blocking the calling thread until the transaction is
     * done. Write transactions automatically commit any changes made when the closure returns
     * unless [MutableRealm.cancelWrite] was called.
     *
     * The write transaction always represent the latest version of data in the Realm file, even if the calling
     * Realm not yet represent this.
     *
     * @param block function that should be run within the context of a write transaction.
     * @return any value returned from the provided write block.
     *
     * @throws IllegalStateException if invoked inside an existing transaction.
     */
    fun <R> writeBlocking(block: MutableRealm.() -> R): R

    /**
     * Observe changes to the Realm. If there is any change to the Realm, the flow will emit the
     * updated Realm. The flow will continue running indefinitely until canceled.
     *
     * The change calculations will run on the thread defined by [RealmConfiguration.notificationDispatcher].
     *
     * @return a flow representing changes to this Realm.
     */
    fun observe(): Flow<Realm>

    /**
     * Close this Realm and all underlying resources. Accessing any methods or Realm Objects after this
     * method has been called will then an [IllegalStateException].
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
