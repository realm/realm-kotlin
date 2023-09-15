/*
 * Copyright 2022 Realm Inc.
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

package io.realm.kotlin.internal

import io.realm.kotlin.VersionId
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmSchemaPointer
import io.realm.kotlin.internal.interop.SynchronizableObject
import io.realm.kotlin.internal.platform.WeakReference
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.internal.util.LiveRealmContext
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.withContext

/**
 * A live realm that can be updated and receive notifications on data and schema changes when
 * updated by other threads.
 *
 * NOTE: Must be constructed with a single thread dispatcher and must be constructed on the same
 * thread that is backing the dispatcher. Further, this is not thread safe so must only be modified
 * on the dispatcher's thread.
 *
 * @param owner The owner of the snapshot references of this realm.
 * @param configuration The configuration of the realm.
 * @param scheduler The single thread dispatcher backing the realm scheduler of this realm. The
 * realm itself must only be access on the same thread.
 */
internal abstract class LiveRealm(
    val owner: RealmImpl,
    configuration: InternalConfiguration,
    private val scheduler: LiveRealmContext
) : BaseRealmImpl(configuration) {

    private val realmChangeRegistration: NotificationToken
    private val schemaChangeRegistration: NotificationToken

    internal val versionTracker = VersionTracker(this, owner.log)

    override val realmReference: LiveRealmReference by lazy {
        val (dbPointer, _) = RealmInterop.realm_open(
            configuration.createNativeConfiguration(),
            scheduler.scheduler
        )
        LiveRealmReference(this, dbPointer)
    }

    /**
     * References the latest frozen reference snapshot of the live realm. This is advanced from
     * [onRealmChanged]-callbacks from the C-API or when explicitly requested by calls to
     * [updateSnapshot]. If this is advanced without issuing references to it though
     * [gcTrackedSnapshot] then the old reference will be closed, allowing Core to release the
     * underlying resources of the no-longer referenced version.
     */
    private val _snapshot: AtomicRef<FrozenRealmReference> = atomic(realmReference.snapshot(owner))
    /**
     * Flag used to control whether to close or track the [_snapshot] when advancing to a newer
     * version.
     */
    private var _closeSnapshotWhenAdvancing: Boolean = true
    /**
     * Lock used to synchronize access to the above two properties, allowing us to trigger tracking
     * of the [_snapshot] when obtained by other threads with the purpose of issuing other
     * object, query, etc. references.
     */
    private val snapshotLock = SynchronizableObject()

    /**
     * Version of the internal frozen snapshot reference that points to the most recent frozen
     * head of the realm known by this [LiveRealm]. This is allowed to be accessed from other
     * threads, but is not guaranteed to always be pointing to the same version as the live realm
     * (which can be newer, but never older).
     */
    internal val snapshotVersion: VersionId
        get() = _snapshot.value.uncheckedVersion()

    /**
     * Garbage collector tracked snapshot that can be used to issue other object, query, etc.
     * reference which lifetime will be controlled by the GC.
     *
     * This will update the status of the snapshot so that it will be tracked through the garbage
     * collector and closed once not reference anymore. This update will happen while holding the
     * [snapshotLock].
     */
    internal fun gcTrackedSnapshot(): FrozenRealmReference {
        return snapshotLock.withLock {
            _snapshot.value.also { snapshot ->
                if (_closeSnapshotWhenAdvancing && !snapshot.isClosed()) {
                    log.trace("${this@LiveRealm} ENABLE-TRACKING ${snapshot.version()}")
                    _closeSnapshotWhenAdvancing = false
                }
            }
        }
    }

    init {
        @Suppress("LeakingThis") // Should be ok as we do not rely on this to be fully initialized
        val callback = WeakLiveRealmCallback(this)
        realmChangeRegistration = NotificationToken(RealmInterop.realm_add_realm_changed_callback(realmReference.dbPointer, callback::onRealmChanged))
        schemaChangeRegistration = NotificationToken(RealmInterop.realm_add_schema_changed_callback(realmReference.dbPointer, callback::onSchemaChanged))
    }

    // Always executed on the live realm's backing thread
    internal open fun onRealmChanged() {
        updateSnapshot()
    }
    // Always executed on the live realm's backing thread
    internal fun updateSnapshot() {
        snapshotLock.withLock {
            val version = _snapshot.value.version()
            if (realmReference.isClosed() || version == realmReference.version()) {
                return
            }
            if (_closeSnapshotWhenAdvancing) {
                log.trace("${this@LiveRealm} CLOSE-UNTRACKED $version")
                _snapshot.value.close()
            } else {
                versionTracker.trackReference(_snapshot.value)
            }
            _snapshot.value = realmReference.snapshot(owner)
            log.trace("${this@LiveRealm} ADVANCING $version -> ${_snapshot.value.version()}")
            _closeSnapshotWhenAdvancing = true
        }

        versionTracker.closeExpiredReferences()
    }

    protected open fun onSchemaChanged(schema: RealmSchemaPointer) {
        realmReference.refreshSchemaMetadata()
    }

    internal fun refresh() {
        RealmInterop.realm_refresh(realmReference.dbPointer)
    }

    internal fun unregisterCallbacks() {
        realmChangeRegistration.cancel()
        schemaChangeRegistration.cancel()
    }

    override fun close() {
        // Close actual live reference. From this point off the snapshot will not be updated.
        realmReference.close()
        // Close current reference
        _snapshot.value.let {
            log.trace("$this CLOSE-ACTIVE ${it.version()}")
            it.close()
        }
        // Close all intermediate references
        versionTracker.close()
        // Ensure that we unregister callbacks
        unregisterCallbacks()
        super.close()
    }

    /**
     * Dump the current snapshot and tracked versions for debugging purpose.
     */
    internal fun versions(): VersionData = runBlocking {
        withContext(scheduler.dispatcher) {
            snapshotLock.withLock {
                val active = if (!_closeSnapshotWhenAdvancing) {
                    versionTracker.versions() + _snapshot.value.version()
                } else {
                    versionTracker.versions()
                }
                VersionData(_snapshot.value.version(), active)
            }
        }
    }

    private class WeakLiveRealmCallback(liveRealm: LiveRealm) {
        val realm: WeakReference<LiveRealm> = WeakReference(liveRealm)
        fun onRealmChanged() { realm.get()?.onRealmChanged() }
        fun onSchemaChanged(schema: RealmSchemaPointer) { realm.get()?.onSchemaChanged(schema) }
    }
}
