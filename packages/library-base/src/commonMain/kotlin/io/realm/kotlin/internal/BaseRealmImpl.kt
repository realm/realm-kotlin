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
package io.realm.kotlin.internal

import io.realm.kotlin.BaseRealm
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.notifications.internal.Callback
import io.realm.kotlin.notifications.internal.Cancellable
import io.realm.kotlin.types.BaseRealmObject
import kotlinx.coroutines.flow.Flow

@Suppress("UnnecessaryAbstractClass")
// TODO Public due to being a transitive dependency to RealmReference
public abstract class BaseRealmImpl internal constructor(
    final override val configuration: InternalConfiguration,
) : BaseRealm, RealmStateHolder {

    private companion object {
        private const val OBSERVABLE_NOT_SUPPORTED_MESSAGE =
            "Observing changes are not supported by this Realm."
    }

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
    public abstract val realmReference: RealmReference

    override fun realmState(): RealmState {
        return realmReference
    }

    override fun isClosed(): Boolean {
        return super.isClosed()
    }

    internal val log: RealmLog = RealmLog(configuration = configuration.log)

    init {
        log.info("Realm opened: ${configuration.path}")
    }

    override fun schemaVersion(): Long {
        return RealmInterop.realm_get_schema_version(realmReference.dbPointer)
    }

    internal open fun <T : CoreNotifiable<T, C>, C> registerObserver(t: Observable<T, C>): Flow<C> {
        throw UnsupportedOperationException(OBSERVABLE_NOT_SUPPORTED_MESSAGE)
    }

    internal open fun <T : BaseRealmObject> registerResultsChangeListener(
        results: RealmResultsImpl<T>,
        callback: Callback<RealmResultsImpl<T>>
    ): Cancellable {
        throw NotImplementedError(OBSERVABLE_NOT_SUPPORTED_MESSAGE)
    }

    internal open fun <T : BaseRealmObject> registerListChangeListener(
        list: List<T>,
        callback: Callback<List<T>>
    ): Cancellable {
        throw NotImplementedError(OBSERVABLE_NOT_SUPPORTED_MESSAGE)
    }

    internal open fun <T : BaseRealmObject> registerObjectChangeListener(
        obj: T,
        callback: Callback<T?>
    ): Cancellable {
        throw NotImplementedError(OBSERVABLE_NOT_SUPPORTED_MESSAGE)
    }

    override fun getNumberOfActiveVersions(): Long {
        val reference = realmReference
        reference.checkClosed()
        return RealmInterop.realm_get_num_versions(reference.dbPointer)
    }

    // Not all sub classes of `BaseRealm` can be closed by users.
    internal open fun close() {
        log.info("Realm closed: $this ${configuration.path}")
    }

    override fun toString(): String = "${this::class.simpleName}[${this.configuration.path}}]"
}
