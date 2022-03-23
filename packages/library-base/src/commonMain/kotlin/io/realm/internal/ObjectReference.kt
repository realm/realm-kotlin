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

public class ObjectReference<T : RealmObject>(internal val type: KClass<T>) :
    RealmStateHolder,
    io.realm.internal.interop.RealmObjectInterop,
    InternalDeleteable,
    Observable<ObjectReference<out RealmObject>, ObjectChange<out RealmObject>>,
    Flowable<ObjectChange<out RealmObject>> {

    public lateinit var owner: RealmReference
    public lateinit var className: String
    public lateinit var mediator: Mediator

    // Could be subclassed for DynamicClassMetadata that would query the realm on each lookup
    public lateinit var metadata: ClassMetadata
    public override lateinit var objectPointer: NativePointer

    // Any methods added to this interface, needs to be fake overridden on the user classes by
    // the compiler plugin, see "RealmObjectInternal overrides" in RealmModelLowering.lower
    public fun propertyInfoOrThrow(
        propertyName: String
    ): PropertyInfo =
        this.metadata.getOrThrow(propertyName)

    override fun realmState(): RealmState {
        return owner
    }

    private fun newObjectReference(
        owner: RealmReference,
        pointer: NativePointer,
        clazz: KClass<out RealmObject> = type
    ): ObjectReference<out RealmObject> = ObjectReference(clazz).apply {
        this.owner = owner
        this.mediator = this@ObjectReference.mediator
        this.objectPointer = pointer
    }

    override fun freeze(
        frozenRealm: RealmReference
    ): ObjectReference<out RealmObject>? {
        return RealmInterop.realm_object_resolve_in(
            objectPointer,
            frozenRealm.dbPointer
        )?.let { pointer: NativePointer ->
            newObjectReference(frozenRealm, pointer)
        }
    }

    override fun thaw(liveRealm: RealmReference): ObjectReference<out RealmObject>? {
        return thaw(liveRealm, type)
    }

    public fun thaw(
        liveRealm: RealmReference,
        clazz: KClass<out RealmObject>
    ): ObjectReference<out RealmObject>? {
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
        val frozenObject: ObjectReference<out RealmObject>? = this.freeze(frozenRealm)

        return if (frozenObject == null) {
            channel
                .trySend(DeletedObjectImpl())
                .also {
                    channel.close()
                }
        } else {
            val obj: RealmObject = frozenObject.toRealmObject()
            val changedFieldNames = obj.asObjectReference()!!.getChangedFieldNames(change)

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
            metadata.get(propertyKey)?.name ?: ""
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

    private fun isValid(): Boolean {
        val ptr = objectPointer
        return if (ptr != null) {
            RealmInterop.realm_object_is_valid(ptr)
        } else {
            false
        }
    }
}
