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

package io.realm.internal

import io.realm.RealmObject
import io.realm.internal.interop.Callback
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.PropertyInfo
import io.realm.internal.interop.PropertyKey
import io.realm.internal.interop.RealmInterop
import io.realm.internal.schema.ClassMetadata
import io.realm.notifications.ObjectChange
import io.realm.notifications.internal.DeletedObjectImpl
import io.realm.notifications.internal.InitialObjectImpl
import io.realm.notifications.internal.UpdatedObjectImpl
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * A RealmObjectReference that links a specific Kotlin RealmObjectInternal instance with an underlying C++
 * Realm Object.
 *
 * It contains a pointer to the object and it is the main entry point to the Realm object features.
 */
public class RealmObjectReference<T : RealmObject>(
    public val className: String,
    internal val type: KClass<T>,
    public val owner: RealmReference,
    public val mediator: Mediator,
    public override val objectPointer: NativePointer,
) :
    RealmStateHolder,
    io.realm.internal.interop.RealmObjectInterop,
    InternalDeleteable,
    Observable<RealmObjectReference<out RealmObject>, ObjectChange<out RealmObject>>,
    Flowable<ObjectChange<out RealmObject>> {

    public val metadata: ClassMetadata = owner.schemaMetadata[className]!!

    // Any methods added to this interface, needs to be fake overridden on the user classes by
    // the compiler plugin, see "RealmObjectInternal overrides" in RealmModelLowering.lower
    public fun propertyInfoOrThrow(
        propertyName: String
    ): PropertyInfo = this.metadata.getOrThrow(propertyName)

    override fun realmState(): RealmState {
        return owner
    }

    private fun newObjectReference(
        owner: RealmReference,
        pointer: NativePointer,
        clazz: KClass<out RealmObject> = type
    ): RealmObjectReference<out RealmObject> = RealmObjectReference(
        type = clazz,
        owner = owner,
        mediator = mediator,
        className = className,
        objectPointer = pointer
    )

    override fun freeze(
        frozenRealm: RealmReference
    ): RealmObjectReference<out RealmObject>? {
        return RealmInterop.realm_object_resolve_in(
            objectPointer,
            frozenRealm.dbPointer
        )?.let { pointer: NativePointer ->
            newObjectReference(frozenRealm, pointer)
        }
    }

    override fun thaw(liveRealm: RealmReference): RealmObjectReference<out RealmObject>? {
        return thaw(liveRealm, type)
    }

    public fun thaw(
        liveRealm: RealmReference,
        clazz: KClass<out RealmObject>
    ): RealmObjectReference<out RealmObject>? {
        val dbPointer = liveRealm.dbPointer
        return RealmInterop.realm_object_resolve_in(objectPointer, dbPointer)
            ?.let { pointer: NativePointer ->
                newObjectReference(liveRealm, pointer, clazz)
            }
    }

    override fun registerForNotification(callback: Callback): NativePointer {
        // We should never get here unless it is a managed object as unmanaged doesn't support observing
        return RealmInterop.realm_object_add_notification_callback(
            this.objectPointer,
            callback
        )
    }

    override fun emitFrozenUpdate(
        frozenRealm: RealmReference,
        change: NativePointer,
        channel: SendChannel<ObjectChange<out RealmObject>>
    ): ChannelResult<Unit>? {
        val frozenObject: RealmObjectReference<out RealmObject>? = this.freeze(frozenRealm)

        return if (frozenObject == null) {
            channel
                .trySend(DeletedObjectImpl())
                .also {
                    channel.close()
                }
        } else {
            val obj: RealmObject = frozenObject.toRealmObject()
            val changedFieldNames = obj.getObjectReference()!!.getChangedFieldNames(change)

            // We can identify the initial ObjectChange event emitted by core because it has no changed fields.
            if (changedFieldNames.isEmpty()) {
                channel.trySend(InitialObjectImpl(obj))
            } else {
                channel.trySend(UpdatedObjectImpl(obj, changedFieldNames))
            }
        }
    }

    private fun getChangedFieldNames(
        change: NativePointer
    ): Array<String> {
        return RealmInterop.realm_object_changes_get_modified_properties(
            change
        ).map { propertyKey: PropertyKey ->
            metadata[propertyKey]?.name ?: ""
        }.toTypedArray()
    }

    override fun asFlow(): Flow<ObjectChange<out RealmObject>> {
        return this.owner.owner.registerObserver(this)
    }

    override fun delete() {
        if (isFrozen()) {
            throw IllegalArgumentException(
                "Frozen objects cannot be deleted. They must be converted to live objects first " +
                    "by using `MutableRealm/DynamicMutableRealm.findLatest(frozenObject)`."
            )
        }
        if (!isValid()) {
            throw IllegalArgumentException("Cannot perform this operation on an invalid/deleted object")
        }
        objectPointer.let { RealmInterop.realm_object_delete(it) }
    }

    internal fun isValid(): Boolean {
        val ptr = objectPointer
        return if (ptr != null) {
            RealmInterop.realm_object_is_valid(ptr)
        } else {
            false
        }
    }

    internal fun checkValid() {
        if (!this.isValid()) {
            throw IllegalStateException("Cannot perform this operation on an invalid/deleted object")
        }
    }
}

internal fun <T : RealmObject> RealmObjectReference<T>.checkNotificationsAvailable() {
    if (RealmInterop.realm_is_closed(owner.dbPointer)) {
        throw IllegalStateException("Changes cannot be observed when the Realm has been closed.")
    }
    if (!isValid()) {
        throw IllegalStateException("Changes cannot be observed on objects that have been deleted from the Realm.")
    }
}
