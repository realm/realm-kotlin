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

import io.realm.internal.RealmObjectInternal
import io.realm.internal.unmanage
import io.realm.interop.RealmInterop
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * This class represents the writeable state of a Realm file. The only way to modify data in a Realm is through
 * instances of this class. These are provided and managed automatically through either [Realm.write] or
 * [Realm.writeBlocking].
 */
class MutableRealm : BaseRealm {

    // Track wether or not the write should be persisted
    internal var commitWrite: Boolean = true

    // TODO Also visible as a companion method to allow for `RealmObject.delete()`, but this
    //  has drawbacks. See https://github.com/realm/realm-kotlin/issues/181
    internal companion object {
        internal fun <T : RealmObject> delete(obj: T) {
            val internalObject = obj as RealmObjectInternal
            internalObject.`$realm$ObjectPointer`?.let { RealmInterop.realm_object_delete(it) }
                ?: throw IllegalArgumentException("Cannot delete unmanaged object")
            internalObject.unmanage()
        }
    }

    // TODO Do we actually need the scoped realm (inheriting context from the parent).
    /**
     * Create a MutableRealm which lifecycle must be managed by its own, i.e. any modifications
     * done inside the MutableRealm is not immediately reflected in the `parentRealm`.
     */
    internal constructor(configuration: RealmConfiguration) :
        super(configuration, RealmInterop.realm_open(configuration.nativeConfig))


    internal fun beginTransaction() {
        RealmInterop.realm_begin_write(dbPointer)
        commitWrite = true
    }

    internal fun commitTransaction() {
        RealmInterop.realm_commit(dbPointer)
    }

    internal fun isInTransaction(): Boolean {
        return RealmInterop.realm_is_in_transaction(dbPointer)
    }

    /**
     * Cancel the write. Any changes will not be persisted to disk.
     */
    public fun cancelWrite() {
        RealmInterop.realm_rollback(dbPointer)
        commitWrite = false
    }

    @Deprecated("Use MutableRealm.copyToRealm() instead", ReplaceWith("io.realm.MutableRealm.copyToRealm(obj)"))
    fun <T : RealmObject> create(type: KClass<T>): T {
        return io.realm.internal.create(configuration.mediator, getSnapshotId(), type)
    }
    // Convenience inline method for the above to skip KClass argument
    @Deprecated("Use MutableRealm.copyToRealm() instead", ReplaceWith("io.realm.MutableRealm.copyToRealm(obj)"))
    inline fun <reified T : RealmObject> create(): T { return create(T::class) }

    @Deprecated("Use MutableRealm.copyToRealm() instead", ReplaceWith("io.realm.MutableRealm.copyToRealm(obj)"))
    fun <T : RealmObject> create(type: KClass<T>, primaryKey: Any?): T {
        return io.realm.internal.create(configuration.mediator, getSnapshotId(), type, primaryKey)
    }

    /**
     * Creates a copy of an object in the Realm.
     *
     * This will create a copy of an object and all it's children. Any already managed objects will
     * not be copied, including the root `instance`. So invoking this with an already managed
     * object is a no-operation.
     *
     * @param instance The object to create a copy from.
     * @return The managed version of the `instance`.
     */
    fun <T : RealmObject> copyToRealm(instance: T): T {
        return io.realm.internal.copyToRealm(configuration.mediator, getSnapshotId(), instance)
    }

    /**
     * Deletes the object from the underlying Realm.
     *
     * @throws IllegalArgumentException if the object is not managed by Realm.
     */
    fun <T : RealmObject> delete(obj: T) {
        val internalObject = obj as RealmObjectInternal
        internalObject.`$realm$ObjectPointer`?.let { RealmInterop.realm_object_delete(it) }
            ?: throw IllegalArgumentException("An unmanaged unmanaged object cannot be deleted from the Realm.")
        internalObject.unmanage()
    }

    override fun <T : RealmObject> observeResults(results: RealmResults<T>): Flow<RealmResults<T>> {
        throw IllegalStateException("Changes to RealmResults cannot be observed during a write.")
    }

    override fun <T : RealmObject> observeList(list: List<T>): Flow<List<T>> {
        throw IllegalStateException("Changes to RealmList cannot be observed during a write.")
    }

    override fun <T : RealmObject> observeObject(obj: T): Flow<T> {
        throw IllegalStateException("Changes to RealmObject cannot be observed during a write.")
    }

    override fun <T : RealmObject> addResultsChangeListener(
        results: RealmResults<T>,
        callback: Callback<RealmResults<T>>
    ): Cancellable {
        throw IllegalStateException("Changes to RealmResults cannot be observed during a write.")
    }

    override fun <T : RealmObject> addListChangeListener(list: List<T>, callback: Callback<List<T>>): Cancellable {
        throw IllegalStateException("Changes to RealmResults cannot be observed during a write.")
    }

    override fun <T : RealmObject> addObjectChangeListener(obj: T, callback: Callback<T?>): Cancellable {
        throw IllegalStateException("Changes to RealmResults cannot be observed during a write.")
    }

    // FIXME Consider adding a delete-all along with query support
    //  https://github.com/realm/realm-kotlin/issues/64
    // fun <T : RealmModel> delete(clazz: KClass<T>)
}
