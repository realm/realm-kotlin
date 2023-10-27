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

import io.realm.kotlin.internal.interop.Callback
import io.realm.kotlin.internal.interop.PropertyKey
import io.realm.kotlin.internal.interop.RealmChangesPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmKeyPathArray
import io.realm.kotlin.internal.interop.RealmNotificationTokenPointer
import io.realm.kotlin.internal.interop.RealmObjectInterop
import io.realm.kotlin.internal.interop.RealmObjectPointer
import io.realm.kotlin.internal.schema.ClassMetadata
import io.realm.kotlin.internal.schema.PropertyMetadata
import io.realm.kotlin.notifications.ObjectChange
import io.realm.kotlin.notifications.internal.DeletedObjectImpl
import io.realm.kotlin.notifications.internal.InitialObjectImpl
import io.realm.kotlin.notifications.internal.UpdatedObjectImpl
import io.realm.kotlin.types.BaseRealmObject
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * A RealmObjectReference that links a specific Kotlin RealmObjectInternal instance with an underlying C++
 * Realm Object.
 *
 * It contains a pointer to the object and it is the main entry point to the Realm object features.
 */
// TODO Public due to being a transitive dependency of RealmObjectInternal
public class RealmObjectReference<T : BaseRealmObject>(
    public val className: String,
    public val type: KClass<T>,
    public val owner: RealmReference,
    public val mediator: Mediator,
    public override val objectPointer: RealmObjectPointer,
) :
    RealmStateHolder,
    RealmObjectInterop,
    InternalDeleteable,
    CoreNotifiable<RealmObjectReference<T>, ObjectChange<T>> {

    public val metadata: ClassMetadata = owner.schemaMetadata[className]!!

    // Any methods added to this interface, needs to be fake overridden on the user classes by
    // the compiler plugin, see "RealmObjectInternal overrides" in RealmModelLowering.lower
    public fun propertyInfoOrThrow(
        propertyName: String
    ): PropertyMetadata = this.metadata.getOrThrow(propertyName)

    override fun realmState(): RealmState {
        return owner
    }

    private fun newObjectReference(
        owner: RealmReference,
        pointer: RealmObjectPointer,
        clazz: KClass<out BaseRealmObject> = type
    ): RealmObjectReference<out BaseRealmObject> = RealmObjectReference(
        type = clazz,
        owner = owner,
        mediator = mediator,
        className = className,
        objectPointer = pointer
    )

    override fun freeze(
        frozenRealm: RealmReference
    ): RealmObjectReference<T>? {
        return RealmInterop.realm_object_resolve_in(
            objectPointer,
            frozenRealm.dbPointer
        )?.let { pointer: RealmObjectPointer ->
            newObjectReference(frozenRealm, pointer)
        } as RealmObjectReference<T>?
    }

    override fun thaw(liveRealm: RealmReference): RealmObjectReference<T>? {
        return thaw(liveRealm, type)
    }

    public fun thaw(
        liveRealm: RealmReference,
        clazz: KClass<out BaseRealmObject>
    ): RealmObjectReference<T>? {
        val dbPointer = liveRealm.dbPointer
        return RealmInterop.realm_object_resolve_in(objectPointer, dbPointer)
            ?.let { pointer: RealmObjectPointer ->
                newObjectReference(liveRealm, pointer, clazz)
            } as RealmObjectReference<T>?
    }

    override fun registerForNotification(keyPaths: RealmKeyPathArray?,
                                         callback: Callback<RealmChangesPointer>): RealmNotificationTokenPointer {
        // We should never get here unless it is a managed object as unmanaged doesn't support observing
        return RealmInterop.realm_object_add_notification_callback(
            this.owner.dbPointer,
            this.metadata.classKey,
            this.objectPointer,
            keyPaths,
            callback
        )
    }

    override fun changeFlow(scope: ProducerScope<ObjectChange<T>>): ChangeFlow<RealmObjectReference<T>, ObjectChange<T>> =
        ObjectChangeFlow(scope)

    internal fun getChangedFieldNames(
        change: RealmChangesPointer
    ): Array<String> {
        return RealmInterop.realm_object_changes_get_modified_properties(
            change
        ).map { propertyKey: PropertyKey ->
            metadata[propertyKey]?.name ?: ""
        }.toTypedArray()
    }

    override fun asFlow(keyPath: List<String>?): Flow<ObjectChange<T>> {
        return this.owner.owner.registerObserver(this, keyPath)
    }

    override fun delete() {
        if (isFrozen()) {
            throw IllegalArgumentException(
                "Frozen objects cannot be deleted. They must be converted to live objects first " +
                    "by using `MutableRealm/DynamicMutableRealm.findLatest(frozenObject)`."
            )
        }
        if (!isValid()) {
            throw IllegalArgumentException(INVALID_OBJECT_MSG)
        }
        objectPointer.let { RealmInterop.realm_object_delete(it) }
    }

    internal fun isValid(): Boolean {
        val ptr = objectPointer
        return if (ptr != null) {
            !ptr.isReleased() && RealmInterop.realm_object_is_valid(ptr)
        } else {
            false
        }
    }

    internal fun checkValid() {
        if (!this.isValid()) {
            throw IllegalStateException(INVALID_OBJECT_MSG)
        }
    }

    public companion object {
        public const val INVALID_OBJECT_MSG: String = "Cannot perform this operation on an invalid/deleted object"
    }
}

internal fun <T : BaseRealmObject> RealmObjectReference<T>.checkNotificationsAvailable() {
    if (RealmInterop.realm_is_closed(owner.dbPointer)) {
        throw IllegalStateException("Changes cannot be observed when the Realm has been closed.")
    }
    if (!isValid()) {
        throw IllegalStateException("Changes cannot be observed on objects that have been deleted from the Realm.")
    }
}

internal class ObjectChangeFlow<E : BaseRealmObject>(scope: ProducerScope<ObjectChange<E>>) :
    ChangeFlow<RealmObjectReference<E>, ObjectChange<E>>(scope) {

    override fun initial(frozenRef: RealmObjectReference<E>): ObjectChange<E> {
        val obj: E = frozenRef.toRealmObject()
        return InitialObjectImpl(obj)
    }

    override fun update(
        frozenRef: RealmObjectReference<E>,
        change: RealmChangesPointer
    ): ObjectChange<E> {
        val obj: E = frozenRef.toRealmObject()
        val changedFieldNames = frozenRef.getChangedFieldNames(change)
        return UpdatedObjectImpl(obj, changedFieldNames)
    }

    override fun delete(): ObjectChange<E> = DeletedObjectImpl()
}
