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
import io.realm.internal.interop.RealmInterop
import io.realm.isValid
import io.realm.notifications.DeletedObjectImpl
import io.realm.notifications.InitialObjectImpl
import io.realm.notifications.ObjectChange
import io.realm.notifications.UpdatedObjectImpl
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
@Suppress("VariableNaming")
interface RealmObjectInternal : RealmObject, RealmStateHolder, io.realm.internal.interop.RealmObjectInterop, Observable<RealmObjectInternal, ObjectChange<RealmObjectInternal>>, Flowable<ObjectChange<RealmObjectInternal>> {
    // Names must match identifiers in compiler plugin (plugin-compiler/io.realm.compiler.Identifiers.kt)

    // Reference to the public Realm instance and internal transaction to which the object belongs.
    var `$realm$Owner`: RealmReference?
    var `$realm$TableName`: String?
    var `$realm$IsManaged`: Boolean
    var `$realm$Mediator`: Mediator?

    // Any methods added to this interface, needs to be fake overridden on the user classes by
    // the compiler plugin, see "RealmObjectInternal overrides" in RealmModelLowering.lower
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
        @Suppress("UNCHECKED_CAST")
        val type: KClass<*> = this::class
        val mediator = `$realm$Mediator`!!
        val managedModel = mediator.createInstanceOf(type)
        val dbPointer = liveRealm.dbPointer
        return RealmInterop.realm_object_resolve_in(`$realm$ObjectPointer`!!, dbPointer)?.let {
            managedModel.manage(
                liveRealm,
                mediator,
                type as KClass<RealmObjectInternal>,
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
        change: NativePointer?,
        channel: SendChannel<ObjectChange<RealmObjectInternal>>
    ): ChannelResult<Unit>? {
        val f: RealmObjectInternal? = this.freeze(frozenRealm)
        return if (f == null) {
            channel.trySend(DeletedObjectImpl())
            channel.close()
            null
        } else {
            if(change == null) {
                channel.trySend(InitialObjectImpl(f))
            } else {
                channel.trySend(UpdatedObjectImpl(f))
            }
        }
    }

    override fun asFlow(): Flow<ObjectChange<RealmObjectInternal>> {
        return this.`$realm$Owner`!!.owner.registerObserver(this)
    }
}

internal inline fun RealmObject.realmObjectInternal(): RealmObjectInternal {
    return this as RealmObjectInternal
}

internal fun RealmObjectInternal.checkValid() {
    if (!this.isValid()) {
        throw IllegalStateException("Cannot perform this operation on an invalid/deleted object")
    }
}
