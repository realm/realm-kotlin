package io.realm

import io.realm.internal.NotificationToken
import io.realm.internal.RealmModelInternal
import io.realm.internal.freeze
import io.realm.internal.unmanage
import io.realm.internal.worker.LiveRealm
import io.realm.internal.worker.NotifierThread
import io.realm.internal.worker.WriterThread
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

/**
 * Realm instance that allows access to the underlying Realm file.
 * FIMXE: Add detailed description here as well as examples of simple operations
 */
open class Realm(configuration: RealmConfiguration): LiveRealm(configuration) {

    // TODO: VersionID is a weird name, just copied from Core from now, maybe just `Realm.Version`
    //  but we also have a schema version. How to make it clear that there is a "data version" and
    //  a "schema version"?
    // TODO: ULong is marked experimental. Doubtfull if it is worth it to spread this warning
    //  everywhere for a very minor benefit?
    public data class VersionId constructor(val version: ULong, val index: ULong): Comparable<VersionId> {
        // TODO: Figure out exactly how to do comparison. In Realm Java we just used
        //  used version, but I assume index can also play a part somehow?
        override fun compareTo(other: VersionId): Int {
            return when {
                version > other.version -> 1
                version < other.version -> -1
                else -> 0
            }
        }

        internal companion object {
            /**
             * Read the Realm Version from the native transaction.
             */
            internal fun fromRealm(realm: NativePointer): VersionId {
                return RealmInterop.realm_get_version_id(realm).run {
                    if (this == null) {
                        throw IllegalStateException("No version was returned from the Realm.")
                    }
                    VersionId(first, second)
                }
            }
        }
    }

    // FIXME: Move these methods?
    companion object {
        // FIXME API-MUTABLE-REALM This should actually only be possible on a mutable realm, i.e. inside
        //  a transaction
        // FIXME EVALUATE Should this be on RealmModel instead?
        internal fun <T : RealmObject<T>> delete(obj: T) {
            val internalObject = obj as RealmModelInternal
            internalObject.`$realm$ObjectPointer`?.let { RealmInterop.realm_object_delete(it) }
                ?: throw IllegalArgumentException("Cannot delete unmanaged object")
            internalObject.unmanage()
        }

        /**
         * Observe change.
         *
         * Triggers calls to [callback] when there are changes to [obj].
         *
         * To receive asynchronous callbacks this must be called:
         * - Android: on a thread with a looper
         * - iOS/macOS: on the main thread (as we currently do not support opening Realms with
         *   different schedulers similarly to
         *   https://github.com/realm/realm-cocoa/blob/master/Realm/RLMRealm.mm#L424)
         *
         * Notes:
         * - Calls are triggered synchronously on a [beginTransaction] when the version is advanced.
         * - Ignoring the return value will eliminate the possibility to cancel the registration
         *   and will leak the [callback] and potentially the internals related to the registration.
         */
        // @CheckReturnValue Not available for Kotlin?
        fun <T : RealmObject<T>> addChangeListener(obj: T, callback: Callback): Cancellable {
            val internalObject = obj as RealmModelInternal
            internalObject.`$realm$ObjectPointer`?.let {
                val internalCallback = object : io.realm.interop.Callback {
                    override fun onChange(objectChanges: NativePointer) {
                        // FIXME Need to expose change details to the user
                        //  https://github.com/realm/realm-kotlin/issues/115
                        callback.onChange()
                    }
                }
                val token = RealmInterop.realm_object_add_notification_callback(it, internalCallback)
                return NotificationToken(internalCallback, token)
            } ?: throw IllegalArgumentException("Cannot register listeners on unmanaged object")
        }
    }

    // Mutex guarding updating the dbPointer and version
    var version: VersionId = VersionId(0.toULong(), 0.toULong())
    private val realmPointerMutex = Mutex()

    // TODO: Do we need a Realm specific coroutine scope? Like `Realm.scope`?
    private val writerThread = WriterThread(configuration)
    internal val notifierThread = NotifierThread(this)

    init {
        val liveDbPointer = RealmInterop.realm_open(configuration.nativeConfig)
        val frozenDbPointer = RealmInterop.realm_freeze(liveDbPointer);
        dbPointer = frozenDbPointer
        version = VersionId.fromRealm(frozenDbPointer)

        // Update the Realm if another process or background thread updates the Realm
        notifierThread.jobScope.launch {
            notifierThread.realmChanged().collect {
                updateRealmPointer(it.first, it.second)
            }
        }
    }

    protected suspend fun updateRealmPointer(newRealm: NativePointer, newVersion: Realm.VersionId) {
        realmPointerMutex.withLock {
            println("${version} <-> ${newVersion}")
            if (newVersion >= version) {
                dbPointer = newRealm
                version = newVersion
            }
        }
    }

    suspend fun <R> write(dispatcher: CoroutineDispatcher = writerThread.dispatcher, function: MutableRealm.() -> R): R {
        try {
            val result: Triple<NativePointer, VersionId, R> = withContext(dispatcher) {
                val writerRealm = writerThread.getOrCreateRealm()
                var result: R
                try {
                    writerRealm.beginTransaction()
                    result = function(writerRealm)
                    writerRealm.commitTransaction()
                } catch (e: Exception) {
                    writerRealm.cancelWrite()
                    // Should we wrap in a specific exception type like RealmWriteException?
                    throw e
                }

                // Freeze the triple of <Realm, Version, Result> while in the context
                // of the Dispatcher. The dispatcher should be single-threaded so will
                // guarantee that no other threads can modify the Realm between
                // the transaction is committed and we freeze it.
                // TODO: Can we guarantee the Dispatcher is single-threaded? Or otherwise
                //  lock this code?
                val newDbPointer = RealmInterop.realm_freeze(writerRealm.dbPointer!!)
                val newVersion = VersionId.fromRealm(dbPointer!!)
                if (shouldFreezeWriteReturnValue(result)) {
                    result = freezeWriteReturnValue(result, newDbPointer)
                }
                Triple(newDbPointer, newVersion, result)
            }

            // Update the user facing Realm before returning the result.
            // That way, querying the Realm right after the `write` completes will return
            // the written data. Otherwise, we would have to wait for the Notifier thread
            // to detect it and update the user Realm.
            updateRealmPointer(result.first, result.second)
            return result.third
        } catch (e: Exception) {
            throw e
        }
    }

    private fun <R> freezeWriteReturnValue(result: R, frozenDbPointer: NativePointer): R {
        return when(result) {
            is RealmResults<*> -> result.freeze(this) as R
            is RealmObject<*> -> {
                val obj: RealmModelInternal = (result as RealmModelInternal)
                obj.freeze<RealmObject<Any>>(this, frozenDbPointer) as R
            }
            else -> throw IllegalArgumentException("Did not recognize type to be frozen: $result")
        }
    }

    private fun <R> shouldFreezeWriteReturnValue(result: R): Boolean {
        // How to test for managed results?
        return when(result) {
            is RealmResults<*> -> return result.owner != null
            is RealmObject<*> -> return result is RealmModelInternal
            else -> false
        }
    }

    // TODO: We should only expose one way of getting objects..
    fun <T : RealmObject<T>> objects(clazz: KClass<T>): RealmResults<T> {
        return RealmResults.fromQuery(
            this,
            @Suppress("SpreadOperator") // TODO PERFORMANCE Spread operator triggers detekt
            { RealmInterop.realm_query_parse(dbPointer!!, clazz.simpleName!!, "TRUEPREDICATE") },
            clazz,
            configuration.schema
        )
    }

    /**
     * Observe change.
     *
     * Triggers calls to [callback] when there are changes to [obj].
     *
     * To receive asynchronous callbacks this must be called:
     * - Android: on a thread with a looper
     * - iOS/macOS: on the main thread (as we currently do not support opening Realms with
     *   different schedulers similarly to
     *   https://github.com/realm/realm-cocoa/blob/master/Realm/RLMRealm.mm#L424)
     *
     * Notes:
     * - Calls are triggered synchronously on a [beginTransaction] when the version is advanced.
     * - Ignoring the return value will eliminate the possibility to cancel the registration
     *   and will leak the [callback] and potentially the internals related to the registration.
     */
    // @CheckReturnValue Not available for Kotlin?
    fun <T : RealmObject<T>> observe(obj: T, callback: Callback): Cancellable {
        val internalObject = obj as RealmModelInternal
        internalObject.`$realm$ObjectPointer`?.let {
            val internalCallback = object : io.realm.interop.Callback {
                override fun onChange(objectChanges: NativePointer) {
                    // FIXME Need to expose change details to the user
                    //  https://github.com/realm/realm-kotlin/issues/115
                    callback.onChange()
                }
            }
            val token = RealmInterop.realm_object_add_notification_callback(it, internalCallback)
            return NotificationToken(internalCallback, token)
        } ?: throw IllegalArgumentException("Cannot register listeners on unmanaged object")
    }


    // FIXME Consider adding a delete-all along with query support
    //  https://github.com/realm/realm-kotlin/issues/64
    // fun <T : RealmModel> delete(clazz: KClass<T>)

    override fun close() {
        checkClosed()
        super.close()
        writerThread.close()
        notifierThread.close()
    }

    override fun isFrozen(): Boolean {
        return true
    }

}