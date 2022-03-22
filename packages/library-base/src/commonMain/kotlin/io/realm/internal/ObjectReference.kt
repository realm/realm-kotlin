package io.realm.internal

import io.realm.RealmObject
import io.realm.internal.schema.ClassMetadata
import io.realm.internal.util.Validation
import io.realm.notifications.ObjectChange
import io.realm.internal.interop.Callback
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.PropertyInfo
import io.realm.internal.interop.PropertyKey
import io.realm.internal.interop.RealmInterop
import io.realm.notifications.internal.DeletedObjectImpl
import io.realm.notifications.internal.InitialObjectImpl
import io.realm.notifications.internal.UpdatedObjectImpl
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

public class ObjectReference<T : RealmObject>(private val type: KClass<T>) :
    RealmStateHolder,
    io.realm.internal.interop.RealmObjectInterop,
    InternalDeleteable,
    Observable<ObjectReference<T>, ObjectChange<T>>,
    Flowable<ObjectChange<T>> {

    public lateinit var `$realm$Owner`: RealmReference
    public lateinit var `$realm$ClassName`: String
    public lateinit var `$realm$Mediator`: Mediator

    // Could be subclassed for DynamicClassMetadata that would query the realm on each lookup
    public lateinit var `$realm$metadata`: ClassMetadata
    public override var `$realm$ObjectPointer`: NativePointer? = null

    // Any methods added to this interface, needs to be fake overridden on the user classes by
    // the compiler plugin, see "RealmObjectInternal overrides" in RealmModelLowering.lower
    public fun propertyInfoOrThrow(
        propertyName: String
    ): PropertyInfo =
        this.`$realm$metadata`?.getOrThrow(propertyName)
        // TODO Error could be eliminated if we only reached here on a ManagedRealmObject (or something like that)
            ?: Validation.sdkError("Class meta data should never be null for managed objects")

    override fun realmState(): RealmState {
        return `$realm$Owner` ?: UnmanagedState
    }

    private fun clone(pointer: NativePointer) = ObjectReference(type).apply {
        `$realm$Owner` = this@ObjectReference.`$realm$Owner`
        `$realm$ClassName` = this@ObjectReference.`$realm$ClassName`
        `$realm$Mediator` = this@ObjectReference.`$realm$Mediator`
        `$realm$metadata` = this@ObjectReference.`$realm$metadata`
        `$realm$ObjectPointer` = pointer
    }

    override fun freeze(
        frozenRealm: RealmReference
    ): ObjectReference<T>? {
        return RealmInterop.realm_object_resolve_in(
            `$realm$ObjectPointer`!!,
            frozenRealm.dbPointer
        )?.let { it: NativePointer ->
            clone(it)
        }
    }

    override fun thaw(liveRealm: RealmReference): ObjectReference<T>? {
        val dbPointer = liveRealm.dbPointer
        return RealmInterop.realm_object_resolve_in(`$realm$ObjectPointer`!!, dbPointer)
            ?.let { it: NativePointer ->
                clone(it)
            }
    }

    override fun registerForNotification(callback: Callback): NativePointer {
        // We should never get here unless it is a managed object as unmanaged doesn't support observing
        return RealmInterop.realm_object_add_notification_callback(
            this.`$realm$ObjectPointer`!!,
            callback
        )
    }

    override fun emitFrozenUpdate(
        frozenRealm: RealmReference,
        change: NativePointer,
        channel: SendChannel<ObjectChange<T>>
    ): ChannelResult<Unit>? {
        val frozenObject: ObjectReference<T>? = this.freeze(frozenRealm)

        return if (frozenObject == null) {
            channel
                .trySend(DeletedObjectImpl())
                .also {
                    channel.close()
                }
        } else {
            val obj: T = frozenObject.asRealmObject()
            val changedFieldNames = getChangedFieldNames(frozenRealm, change)

            // We can identify the initial ObjectChange event emitted by core because it has no changed fields.
            if (changedFieldNames.isEmpty()) {
                channel.trySend(InitialObjectImpl(obj))
            } else {
                channel.trySend(UpdatedObjectImpl(obj, changedFieldNames))
            }
        }
    }

    internal fun <T: RealmObject> asRealmObject(): T {
        val mediator = `$realm$Mediator`
        val managedModel: RealmObjectInternal = mediator.createInstanceOf(type)
        managedModel.manage(
            type,
            this
        )
        @Suppress("UNCHECKED_CAST")
        return managedModel as T
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

    override fun asFlow(): Flow<ObjectChange<T>> {
        return this.`$realm$Owner`.owner.registerObserver(this)
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
        `$realm$ObjectPointer`?.let { RealmInterop.realm_object_delete(it) }
    }

    private fun isValid(): Boolean {
        val ptr = `$realm$ObjectPointer`
        return if (ptr != null) {
            RealmInterop.realm_object_is_valid(ptr)
        } else {
            false
        }
    }
}