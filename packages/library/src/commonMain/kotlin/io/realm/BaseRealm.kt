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
import io.realm.internal.checkClosed
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
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

    internal open val realm: RealmReference = RealmReference(this, dbPointer)

    /**
     * The current data version of this Realm and data fetched from it.
     */
    // TODO Could be abstracted into base implementation of RealmLifeCycle!?
    public var version: VersionId = VersionId(0)
        get() { return realm.version() }

    internal val log: RealmLog = RealmLog(configuration = configuration.log)

    init {
        log.info("Realm opened: ${configuration.path}")
    }

    fun <T : RealmObject> objects(clazz: KClass<T>): RealmResults<T> {
        // Use same reference through out all operations to avoid locking
        val realmReference = this.realm
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

    /**
     * Returns the current number of active versions in the Realm file. A large number of active versions can have
     * a negative impact on the Realm file size on disk.
     *
     * @see [RealmConfiguration.Builder.maxNumberOfActiveVersions]
     */
    public fun getNumberOfActiveVersions(): Long {
        val reference = realm
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
        return realm.closed()
    }

    // Not all sub classes of `BaseRealm` can be closed by users.
    internal open fun close() {
        val reference = realm
        reference.checkClosed()
        RealmInterop.realm_close(reference.dbPointer)
        log.info("Realm closed: ${configuration.path}")
    }
}
