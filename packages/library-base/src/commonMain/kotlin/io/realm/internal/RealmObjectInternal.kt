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
import io.realm.interop.Callback
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import io.realm.isValid
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
internal interface RealmObjectInternal : RealmObject, RealmStateHolder, io.realm.interop.RealmObjectInterop, Observable<RealmObjectInternal> {
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

    override fun freeze(frozenRealm: RealmReference):RealmObjectInternal {
        @Suppress("UNCHECKED_CAST")
        val type: KClass<RealmObjectInternal> = this::class as KClass<RealmObjectInternal>
        val mediator = `$realm$Mediator`!!
        val managedModel = mediator.createInstanceOf(type)
        return managedModel.manage(
            frozenRealm!!,
            mediator,
            type,
            RealmInterop.realm_object_freeze(
                `$realm$ObjectPointer`!!,
                frozenRealm.dbPointer
            )
        )
    }

    override fun thaw(liveRealm: RealmReference): Observable<RealmObjectInternal>? {
        @Suppress("UNCHECKED_CAST")
        val type: KClass<*> = this::class
        val mediator = `$realm$Mediator`!!
        val managedModel = mediator.createInstanceOf(type)
        val dbPointer = liveRealm.dbPointer
        @Suppress("TooGenericExceptionCaught")
        try {
            val realmObjectThaw: NativePointer? =
                RealmInterop.realm_object_thaw(`$realm$ObjectPointer`!!, dbPointer)
            val realmObjectThaw1: NativePointer = realmObjectThaw!!
            val let: RealmObjectInternal = realmObjectThaw1.let { thawedObject: NativePointer ->
                managedModel.manage(
                    liveRealm,
                    mediator,
                    type as KClass<RealmObjectInternal>,
                    thawedObject
                )
            }
            return let
        } catch (e: Exception) {
            // FIXME C-API is currently throwing an error if the object has been deleted, so currently just
            //  catching that and returning null. Only treat unknown null pointers as non-existing objects
            //  to avoid handling unintended situations here.
            if (e.message?.startsWith("[2]: null") ?: false) {
                return null
            } else {
                throw e
            }
        }
    }

    override fun registerForNotification(callback: Callback): NativePointer {
        // We should never get here unless it is a managed object as unmanaged doesn't support observing
        return RealmInterop.realm_object_add_notification_callback(this.`$realm$ObjectPointer`!!, callback)
    }

    override fun emitFrozenUpdate(
        frozenRealm: RealmReference,
        change: NativePointer,
        channel: SendChannel<RealmObjectInternal>
    ): ChannelResult<Unit>? {
        @Suppress("TooGenericExceptionCaught")
        val f: RealmObjectInternal? = try {
            this.freeze(frozenRealm)
        } catch (e: RuntimeException) {
            // FIXME C-API is currently throwing an error if the object has been deleted, so currently just
            //  catching that and returning null. Only treat unknown null pointers as non-existing objects
            //  to avoid handling unintended situations here.
            if (e.message?.startsWith("[2]: null") ?: false) {
                null
            } else {
                throw e
            }
        }
        return if (f == null) {
            channel.close()
            null
        } else {
            channel.trySend(f)
        }
    }

    override fun observe(): Flow<RealmObjectInternal> {
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
