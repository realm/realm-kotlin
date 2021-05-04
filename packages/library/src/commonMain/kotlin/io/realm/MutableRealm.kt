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
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
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

    /**
     * Create a MutableRealm which lifecycle must be managed by its own, i.e. any modifications
     * done inside the MutableRealm is not immediately reflected in the `parentRealm`.
     */
    internal constructor(parentRealm: Realm) :
        super(parentRealm.configuration, RealmInterop.realm_open(parentRealm.configuration.nativeConfig))

    /**
     * Create a MutableRealm which represents a standard write transaction, i.e. any modifications
     * are immediately represented in the `parentRealm`.
     */
    @Deprecated("Should be avoided and will be removed once we have the proper Frozen Architecture in place.")
    internal constructor(configuration: RealmConfiguration, parentRealm: NativePointer) :
        super(configuration, parentRealm)

    internal fun beginTransaction() {
        RealmInterop.realm_begin_write(dbPointer)
        commitWrite = true
    }

    internal fun commitTransaction() {
        RealmInterop.realm_commit(dbPointer)
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
        return io.realm.internal.create(configuration.mediator, dbPointer, type)
    }
    // Convenience inline method for the above to skip KClass argument
    @Deprecated("Use MutableRealm.copyToRealm() instead", ReplaceWith("io.realm.MutableRealm.copyToRealm(obj)"))
    inline fun <reified T : RealmObject> create(): T { return create(T::class) }

    @Deprecated("Use MutableRealm.copyToRealm() instead", ReplaceWith("io.realm.MutableRealm.copyToRealm(obj)"))
    fun <T : RealmObject> create(type: KClass<T>, primaryKey: Any?): T {
        return io.realm.internal.create(configuration.mediator, dbPointer, type, primaryKey)
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
        return io.realm.internal.copyToRealm(configuration.mediator, dbPointer, instance)
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

    // FIXME Consider adding a delete-all along with query support
    //  https://github.com/realm/realm-kotlin/issues/64
    // fun <T : RealmModel> delete(clazz: KClass<T>)
}
