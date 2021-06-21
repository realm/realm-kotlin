/*
 * Copyright 2021 Realm Inc.
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

import io.realm.internal.RealmLog
import io.realm.internal.RealmReference
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Base class for all Realm instances.
 */
@Suppress("UnnecessaryAbstractClass")
public abstract class BaseRealm internal constructor(
    /**
     * Configuration used to configure this Realm instance.
     */
    public val configuration: RealmConfiguration,
    dbPointer: NativePointer
) {

    /**
     * Realm reference that links the Kotlin instance with the underlying C++ SharedRealm.
     *
     * The C++ SharedRealm can be either a frozen or live realm, so even though this reference is
     * not updated the version of the underlying Realm can change.
     *
     * NOTE: [Realm] overwrites this to an updatable property which is advanced when the [Realm] is
     * updated to point to a new frozen version after writes or notification, so care should be
     * taken not to spread operations over different references.
     */
    internal open var realmReference: RealmReference = RealmReference(this, dbPointer)
        set(_) {
            throw UnsupportedOperationException("BaseRealm reference should never be updated")
        }

    /**
     * The current data version of this Realm and data fetched from it.
     */
    // TODO Could be abstracted into base implementation of RealmLifeCycle!?
    public var version: VersionId = VersionId(0)
        get() { return realmReference.version() }

    internal val log: RealmLog = RealmLog(configuration = configuration.log)

    init {
        log.info("Realm opened: ${configuration.path}")
    }

    fun <T : RealmObject> objects(clazz: KClass<T>): RealmResults<T> {
        // Use same reference through out all operations to avoid locking
        val realmReference = this.realmReference
        realmReference.checkClosed()
        return RealmResults.fromQuery(
            realmReference,
            RealmInterop.realm_query_parse(realmReference.dbPointer, clazz.simpleName!!, "TRUEPREDICATE"),
            clazz,
            configuration.mediator
        )
    }
    // Convenience inline method for the above to skip KClass argument
    inline fun <reified T : RealmObject> objects(): RealmResults<T> { return objects(T::class) }

    internal abstract fun <T: RealmObject> addResultsChangeListener(
        results: RealmResults<T>,
        callback: Callback<RealmResults<T>>
    ): Cancellable

    internal abstract fun <T: RealmObject> addListChangeListener(
        list: List<T>,
        callback: Callback<List<T>>
    ): Cancellable

    internal abstract fun <T: RealmObject> addObjectChangeListener(
        obj: T,
        callback: Callback<T?>
    ): Cancellable

    internal abstract fun <T: RealmObject> observeResults(results: RealmResults<T>): Flow<RealmResults<T>>
    internal abstract fun <T: RealmObject> observeList(list: List<T>): Flow<List<T>>
    internal abstract fun <T: RealmObject> observeObject(obj: T): Flow<T>

    /**
     * Returns the current number of active versions in the Realm file. A large number of active versions can have
     * a negative impact on the Realm file size on disk.
     *
     * @see [RealmConfiguration.Builder.maxNumberOfActiveVersions]
     */
    public fun getNumberOfActiveVersions(): Long {
        val reference = realmReference
        reference.checkClosed()
        return RealmInterop.realm_get_num_versions(reference.dbPointer)
    }

    /**
     * Check if this Realm has been closed or not. If the Realm has been closed, most methods
     * will throw [IllegalStateException] if called.
     *
     * @return `true` if the Realm has been closed. `false` if not.
     */
    public fun isClosed(): Boolean {
        return realmReference.isClosed()
    }

    // Not all sub classes of `BaseRealm` can be closed by users.
    internal open fun close() {
        val reference = realmReference
        reference.checkClosed()
        RealmInterop.realm_close(reference.dbPointer)
        log.info("Realm closed: ${configuration.path}")
    }
}
