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
    protected val dbPointer: NativePointer
) {

    /**
     * The current data version of this Realm and data fetched from it.
     */
    public var version: VersionId = VersionId(0, 0)
        get() {
            checkClosed()
            val (version: Long, index: Long) = RealmInterop.realm_get_version_id(dbPointer)
            return VersionId(version, index)
        }

    // Use this boolean to track closed instead of `NativePointer?` to avoid forcing
    // null checks everywhere, when it is rarely needed.
    private var isClosed: Boolean = false
    internal val log: RealmLog = RealmLog(configuration = configuration.log)

    init {
        log.info("Realm opened: ${configuration.path}")
    }

    fun <T : RealmObject> objects(clazz: KClass<T>): RealmResults<T> {
        checkClosed()
        return RealmResults(
            configuration,
            dbPointer,
            { RealmInterop.realm_query_parse(dbPointer, clazz.simpleName!!, "TRUEPREDICATE") },
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
        checkClosed()
        return RealmInterop.realm_get_num_versions(dbPointer)
    }

    /**
     * Check if this Realm has been closed or not. If the Realm has been closed, most methods
     * will throw [IllegalStateException] if called.
     *
     * @return `true` if the Realm has been closed. `false` if not.
     */
    public fun isClosed(): Boolean {
        return isClosed
    }

    // Inline this for a cleaner stack trace in case it throws.
    @Suppress("MemberVisibilityCanBePrivate")
    internal inline fun checkClosed() {
        if (isClosed) {
            throw IllegalStateException("Realm has been closed and is no longer accessible: ${configuration.path}")
        }
    }

    // Not all sub classes of `BaseRealm` can be closed by users.
    internal open fun close() {
        checkClosed()
        RealmInterop.realm_close(dbPointer)
        isClosed = true
        log.info("Realm closed: ${configuration.path}")
    }
}
