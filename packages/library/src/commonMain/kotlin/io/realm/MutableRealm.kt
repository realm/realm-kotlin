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
import io.realm.internal.coreErrorToThrowable
import io.realm.internal.thaw
import io.realm.interop.RealmInterop
import io.realm.interop.errors.RealmCoreException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Represents the writeable state of a Realm file.
 *
 * To modify data in a [Realm], use instances of this class.
 * These are provided and managed automatically through either [Realm.write] or
 * [Realm.writeBlocking].
 *
 * All objects created and/or obtained from the _mutable realm_ in a write-transaction are bound to
 * the thread executing the transaction. All operations on the _mutable realm_ or on any of the
 * objects contained in that realm must execute on the thread executing the transaction. The only exception is objects returned
 * from [Realm.write] and [Realm.writeBlocking], which are frozen and remain tied to the resulting
 * version of the write-transaction.
 */
class MutableRealm : BaseRealm {

    // TODO Also visible as a companion method to allow for `RealmObject.delete()`, but this
    //  has drawbacks. See https://github.com/realm/realm-kotlin/issues/181
    internal companion object {
        internal fun <T : RealmObject> delete(obj: T) {
            val internalObject = obj as RealmObjectInternal
            checkObjectValid(internalObject)
            internalObject.`$realm$ObjectPointer`?.let { RealmInterop.realm_object_delete(it) }
        }

        private fun checkObjectValid(obj: RealmObjectInternal) {
            if (!obj.isValid()) {
                throw IllegalArgumentException("Cannot perform this operation on an invalid/deleted object")
            }
        }
    }

    /**
     * Create a MutableRealm which lifecycle must be managed by its own, i.e. any modifications
     * done inside the MutableRealm is not immediately reflected in the `parentRealm`.
     *
     * The core scheduler used to deliver notifications are:
     * - Android: The default Android scheduler, which delivers notifications on the looper of
     * the current thread.
     * - Native: Either a scheduler dispatching to the supplied dispatcher or the default Darwin
     * scheduler, that delivers notifications on the main run loop.
     */
    internal constructor(configuration: RealmConfiguration, dispatcher: CoroutineDispatcher? = null) :
        super(configuration, RealmInterop.realm_open(configuration.nativeConfig, dispatcher))

    internal fun beginTransaction() {
        try {
            RealmInterop.realm_begin_write(realmReference.dbPointer)
        } catch (exception: RealmCoreException) {
            throw coreErrorToThrowable("Cannot begin a write transaction", exception)
        }
    }

    internal fun commitTransaction() {
        RealmInterop.realm_commit(realmReference.dbPointer)
    }

    internal fun isInTransaction(): Boolean {
        return RealmInterop.realm_is_in_transaction(realmReference.dbPointer)
    }

    /**
     * Get latest version of an object.
     *
     * Realm write transactions always operate on the latest version of data. This method
     * makes it possible to easily find the latest version of any frozen Realm Object and
     * return a copy of it that can be modified while inside the write block.
     *
     * *Note:* This object is not readable outside the write block unless it has been explicitly
     * returned from the write.
     *
     * @param obj Realm object to look up. Its latest state will be returned. If the object
     * has been deleted, `null` will be returned.
     *
     * @throws IllegalArgumentException if called on an unmanaged object.
     */
    public fun <T : RealmObject> findLatest(obj: T?): T? {
        return if (obj == null || !obj.isValid()) {
            null
        } else if (!obj.isManaged()) {
            throw IllegalArgumentException(
                "Unmanaged objects must be part of the Realm, before " +
                    "they can be queried this way. Use `MutableRealm.copyToRealm()` to turn it into " +
                    "a managed object."
            )
        } else if (!obj.isFrozen()) {
            // If already valid, managed and not frozen, it must be live, and thus already
            // up to date, just return input
            obj
        } else {
            val liveRealm = realmReference.owner
            (obj as RealmObjectInternal).thaw(liveRealm)
        }
    }

    /**
     * Cancel the write. Any changes will not be persisted to disk.
     */
    public fun cancelWrite() {
        try {
            RealmInterop.realm_rollback(realmReference.dbPointer)
        } catch (exception: RealmCoreException) {
            throw coreErrorToThrowable("Cannot cancel the write transaction", exception)
        }
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
     *
     * @throws RuntimeException if the class has a primary key field and an object with the same
     * primary key already exists.
     */
    fun <T : RealmObject> copyToRealm(instance: T): T {
        return io.realm.internal.copyToRealm(configuration.mediator, realmReference, instance)
    }

    /**
     * Returns the results of querying for all objects of a specific type.
     *
     * The result is live and thus also reflects any update to the [MutableRealm].
     *
     * The result is only valid on the calling thread.
     *
     * @param clazz The class of the objects to query for.
     * @return The result of the query, reflecting future updates to the mutable realm.
     */
    override fun <T : RealmObject> objects(clazz: KClass<T>): RealmResults<T> {
        return super.objects(clazz)
    }

    /**
     * Deletes the object from the underlying Realm.
     *
     * @throws IllegalArgumentException if the object is not managed by Realm.
     */
    fun <T : RealmObject> delete(obj: T) {
        // TODO It is easy to call this with a wrong object. Should we use `findLatest` behind the scenes?
        val internalObject = obj as RealmObjectInternal
        checkObjectValid(internalObject)
        internalObject.`$realm$ObjectPointer`?.let { RealmInterop.realm_object_delete(it) }
    }

    // FIXME Consider adding a delete-all along with query support
    //  https://github.com/realm/realm-kotlin/issues/64
    // fun <T : RealmModel> delete(clazz: KClass<T>)

    internal override fun <T : RealmObject> registerResultsObserver(results: RealmResults<T>): Flow<RealmResults<T>> {
        throw IllegalStateException("Changes to RealmResults cannot be observed during a write.")
    }

    internal override fun <T : RealmObject> registerObjectObserver(obj: T): Flow<T> {
        throw IllegalStateException("Changes to RealmObject cannot be observed during a write.")
    }

    internal override fun <T : RealmObject> registerResultsChangeListener(
        results: RealmResults<T>,
        callback: Callback<RealmResults<T>>
    ): Cancellable {
        throw IllegalStateException("Changes to RealmResults cannot be observed during a write.")
    }

    internal override fun <T : RealmObject> registerListChangeListener(list: List<T>, callback: Callback<List<T>>): Cancellable {
        throw IllegalStateException("Changes to RealmResults cannot be observed during a write.")
    }

    internal override fun <T : RealmObject> registerObjectChangeListener(obj: T, callback: Callback<T?>): Cancellable {
        throw IllegalStateException("Changes to RealmResults cannot be observed during a write.")
    }

    internal override fun <T> registerListObserver(list: RealmList<T>): Flow<RealmList<T>> {
        throw IllegalStateException("Changes to RealmList cannot be observed during a write.")
    }
}
