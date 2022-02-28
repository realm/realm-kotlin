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

import io.realm.RealmObject
import io.realm.internal.interop.Callback
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.PropertyInfo
import io.realm.internal.interop.PropertyKey
import io.realm.internal.interop.RealmInterop
import io.realm.internal.schema.ClassMetadata
import io.realm.internal.util.Validation.sdkError
import io.realm.isValid
import io.realm.notifications.ObjectChange
import io.realm.notifications.internal.DeletedObjectImpl
import io.realm.notifications.internal.InitialObjectImpl
import io.realm.notifications.internal.UpdatedObjectImpl
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Internal interface for Realm objects.
 *
 * The interface is added by the compiler plugin to all [RealmObject] classes to have an interface
 * exposing our internal API and compiler plugin additions without leaking it to the public
 * [RealmObject].
 */
// TODO Public due to being a transative dependency of Mediator
@Suppress("VariableNaming")
public interface RealmObjectInternal : RealmObject, RealmStateHolder, io.realm.internal.interop.RealmObjectInterop, Observable<RealmObjectInternal, ObjectChange<RealmObjectInternal>>, Flowable<ObjectChange<RealmObjectInternal>> {
    // Names must match identifiers in compiler plugin (plugin-compiler/io.realm.compiler.Identifiers.kt)

    // Reference to the public Realm instance and internal transaction to which the object belongs.
    public var `$realm$IsManaged`: Boolean
    // Invariant: None of the below will be null for managed objects!
    public var `$realm$Owner`: RealmReference?
    public var `$realm$ClassName`: String?
    public var `$realm$Mediator`: Mediator?
    // Could be subclassed for DynamicClassMetadata that would query the realm on each lookup
    public var `$realm$metadata`: ClassMetadata?

    // Any methods added to this interface, needs to be fake overridden on the user classes by
    // the compiler plugin, see "RealmObjectInternal overrides" in RealmModelLowering.lower
    public fun propertyInfoOrThrow(propertyName: String): PropertyInfo = this.`$realm$metadata`?.getOrThrow(propertyName)
        // TODO Error could be eliminated if we only reached here on a ManagedRealmObject (or something like that)
        ?: sdkError("Class meta data should never be null for managed objects")

    override fun realmState(): RealmState {
        return `$realm$Owner` ?: UnmanagedState
    }

    override fun freeze(frozenRealm: RealmReference): RealmObjectInternal? {
        @Suppress("UNCHECKED_CAST")
        val type: KClass<RealmObjectInternal> = this::class as KClass<RealmObjectInternal>
        val mediator = `$realm$Mediator`!!
        val managedModel = mediator.createInstanceOf(type)
        return RealmInterop.realm_object_resolve_in(
            `$realm$ObjectPointer`!!,
            frozenRealm.dbPointer
        )?.let {
            managedModel.manage(
                frozenRealm,
                mediator,
                type,
                it
            )
        }
    }

    override fun thaw(liveRealm: RealmReference): RealmObjectInternal? {
        return thaw(liveRealm, this::class)
    }

    public fun thaw(liveRealm: RealmReference, clazz: KClass<out RealmObject>): RealmObjectInternal? {
        val mediator = `$realm$Mediator`!!
        val managedModel = mediator.createInstanceOf(clazz)
        val dbPointer = liveRealm.dbPointer
        return RealmInterop.realm_object_resolve_in(`$realm$ObjectPointer`!!, dbPointer)?.let {
            @Suppress("UNCHECKED_CAST")
            managedModel.manage(
                liveRealm,
                mediator,
                clazz as KClass<RealmObjectInternal>,
                it
            )
        }
    }

    override fun registerForNotification(callback: Callback): NativePointer {
        // We should never get here unless it is a managed object as unmanaged doesn't support observing
        return RealmInterop.realm_object_add_notification_callback(this.`$realm$ObjectPointer`!!, callback)
    }

    override fun emitFrozenUpdate(
        frozenRealm: RealmReference,
        change: NativePointer,
        channel: SendChannel<ObjectChange<RealmObjectInternal>>
    ): ChannelResult<Unit>? {
        val frozenObject: RealmObjectInternal? = this.freeze(frozenRealm)

        return if (frozenObject == null) {
            channel
                .trySend(DeletedObjectImpl())
                .also {
                    channel.close()
                }
        } else {
            val changedFieldNames = getChangedFieldNames(frozenRealm, change)

            // We can identify the initial ObjectChange event emitted by core because it has no changed fields.
            if (changedFieldNames.isEmpty()) {
                channel.trySend(InitialObjectImpl(frozenObject))
            } else {
                channel.trySend(UpdatedObjectImpl(frozenObject, changedFieldNames))
            }
        }
    }

    private fun getChangedFieldNames(
        frozenRealm: RealmReference,
        change: NativePointer
    ): Array<String> {
        return RealmInterop.realm_object_changes_get_modified_properties(
            change
        ).map { propertyKey: PropertyKey ->
            `$realm$metadata`?.get(propertyKey)?.name ?: ""
        }.toTypedArray()
    }

    override fun asFlow(): Flow<ObjectChange<RealmObjectInternal>> {
        return this.`$realm$Owner`!!.owner.registerObserver(this)
    }
}

internal fun RealmObject.realmObjectInternal(): RealmObjectInternal {
    return this as RealmObjectInternal
}

internal fun RealmObjectInternal.checkValid() {
    if (!this.isValid()) {
        throw IllegalStateException("Cannot perform this operation on an invalid/deleted object")
    }
}
