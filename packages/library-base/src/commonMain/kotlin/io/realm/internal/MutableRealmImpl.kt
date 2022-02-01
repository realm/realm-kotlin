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
package io.realm.internal

import io.realm.Callback
import io.realm.Cancellable
import io.realm.MutableRealm
import io.realm.RealmObject
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmInterop
import io.realm.isFrozen
import io.realm.isManaged
import io.realm.isValid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

internal class MutableRealmImpl : LiveRealm, MutableRealm {

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
    internal constructor(
        parent: RealmImpl,
        configuration: InternalConfiguration,
        dispatcher: CoroutineDispatcher? = null
    ) : super(parent, configuration, dispatcher)

    internal fun beginTransaction() {
        try {
            RealmInterop.realm_begin_write(realmReference.dbPointer)
        } catch (exception: RealmCoreException) {
            throw genericRealmCoreExceptionHandler("Cannot begin the write transaction", exception)
        }
    }

    internal fun commitTransaction() {
        RealmInterop.realm_commit(realmReference.dbPointer)
    }

    internal fun isInTransaction(): Boolean {
        return RealmInterop.realm_is_in_transaction(realmReference.dbPointer)
    }

    override fun <T : RealmObject> findLatest(obj: T): T? {
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

    override fun cancelWrite() {
        try {
            RealmInterop.realm_rollback(realmReference.dbPointer)
        } catch (exception: RealmCoreException) {
            throw genericRealmCoreExceptionHandler("Cannot cancel the write transaction", exception)
        }
    }

    override fun <T : RealmObject> copyToRealm(instance: T): T {
        return copyToRealm(configuration.mediator, realmReference, instance)
    }

    override fun <T : RealmObject> delete(obj: T) {
        // TODO It is easy to call this with a wrong object. Should we use `findLatest` behind the scenes?
        val internalObject = obj as RealmObjectInternal
        checkObjectValid(internalObject)
        internalObject.`$realm$ObjectPointer`?.let { RealmInterop.realm_object_delete(it) }
    }

    // FIXME Consider adding a delete-all along with query support
    //  https://github.com/realm/realm-kotlin/issues/64
    // fun <T : RealmModel> delete(clazz: KClass<T>)

    override fun <T> registerObserver(t: Thawable<T>): Flow<T> {
        throw IllegalStateException("Changes to RealmResults cannot be observed during a write.")
    }

    internal override fun <T : RealmObject> registerResultsChangeListener(
        results: RealmResultsImpl<T>,
        callback: Callback<RealmResultsImpl<T>>
    ): Cancellable {
        throw IllegalStateException("Changes to RealmResults cannot be observed during a write.")
    }

    internal override fun <T : RealmObject> registerListChangeListener(
        list: List<T>,
        callback: Callback<List<T>>
    ): Cancellable {
        throw IllegalStateException("Changes to RealmResults cannot be observed during a write.")
    }

    internal override fun <T : RealmObject> registerObjectChangeListener(
        obj: T,
        callback: Callback<T?>
    ): Cancellable {
        throw IllegalStateException("Changes to RealmResults cannot be observed during a write.")
    }
}
