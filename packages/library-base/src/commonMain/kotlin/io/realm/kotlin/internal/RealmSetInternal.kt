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

import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.Versioned
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.internal.RealmValueArgumentConverter.convertToQueryArgs
import io.realm.kotlin.internal.interop.Callback
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.interop.RealmChangesPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmInterop.realm_set_get
import io.realm.kotlin.internal.interop.RealmNotificationTokenPointer
import io.realm.kotlin.internal.interop.RealmObjectInterop
import io.realm.kotlin.internal.interop.RealmSetPointer
import io.realm.kotlin.internal.interop.RealmValue
import io.realm.kotlin.internal.interop.ValueType
import io.realm.kotlin.internal.interop.getterScope
import io.realm.kotlin.internal.interop.inputScope
import io.realm.kotlin.internal.query.ObjectBoundQuery
import io.realm.kotlin.internal.query.ObjectQuery
import io.realm.kotlin.notifications.SetChange
import io.realm.kotlin.notifications.internal.DeletedSetImpl
import io.realm.kotlin.notifications.internal.InitialSetImpl
import io.realm.kotlin.notifications.internal.UpdatedSetImpl
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Implementation for unmanaged sets, backed by a [MutableSet].
 */
public class UnmanagedRealmSet<E>(
    private val backingSet: MutableSet<E> = mutableSetOf()
) : RealmSet<E>, InternalDeleteable, MutableSet<E> by backingSet {
    override fun asFlow(): Flow<SetChange<E>> {
        throw UnsupportedOperationException("Unmanaged sets cannot be observed.")
    }

    override fun delete() {
        throw UnsupportedOperationException("Unmanaged sets cannot be deleted.")
    }

    override fun toString(): String = "UnmanagedRealmSet{${joinToString()}}"

    override fun equals(other: Any?): Boolean = backingSet == other

    override fun hashCode(): Int = backingSet.hashCode()
}

/**
 * Implementation for managed sets, backed by Realm.
 */
internal class ManagedRealmSet<E> constructor(
    // Rework to allow RealmAny
    internal val parent: RealmObjectReference<*>?,
    internal val nativePointer: RealmSetPointer,
    val operator: SetOperator<E>
) : AbstractMutableSet<E>(), RealmSet<E>, InternalDeleteable, CoreNotifiable<ManagedRealmSet<E>, SetChange<E>>, Versioned by operator.realmReference {

    override val size: Int
        get() {
            operator.realmReference.checkClosed()
            return RealmInterop.realm_set_size(nativePointer).toInt()
        }

    override fun add(element: E): Boolean {
        return operator.add(element)
    }

    override fun clear() {
        operator.clear()
    }

    override fun contains(element: E): Boolean {
        return operator.contains(element)
    }

    override fun remove(element: E): Boolean {
        return operator.remove(element)
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        return operator.removeAll(elements)
    }

    override fun iterator(): MutableIterator<E> {
        return object : MutableIterator<E> {

            private var expectedModCount = operator.modCount // Current modifications in the map
            private var cursor = 0 // The position returned by next()
            private var lastReturned = -1 // The last known returned position

            override fun hasNext(): Boolean {
                checkConcurrentModification()

                return cursor < size
            }

            override fun next(): E {
                checkConcurrentModification()

                val position = cursor
                if (position >= size) {
                    throw IndexOutOfBoundsException("Cannot access index $position when size is $size. Remember to check hasNext() before using next().")
                }
                val next = operator.get(position)
                lastReturned = position
                cursor = position + 1
                return next
            }

            override fun remove() {
                checkConcurrentModification()

                if (size == 0) {
                    throw NoSuchElementException("Could not remove last element returned by the iterator: set is empty.")
                }
                if (lastReturned < 0) {
                    throw IllegalStateException("Could not remove last element returned by the iterator: iterator never returned an element.")
                }

                val erased = getterScope {
                    val element = operator.get(lastReturned)
                    operator.remove(element)
                        .also {
                            if (lastReturned < cursor) {
                                cursor -= 1
                            }
                            lastReturned = -1
                        }
                }
                expectedModCount = operator.modCount
                if (!erased) {
                    throw NoSuchElementException("Could not remove last element returned by the iterator: was there an element to remove?")
                }
            }

            private fun checkConcurrentModification() {
                if (operator.modCount != expectedModCount) {
                    throw ConcurrentModificationException("The underlying RealmSet was modified while iterating it.")
                }
            }
        }
    }

    override fun asFlow(): Flow<SetChange<E>> {
        return operator.realmReference.owner.registerObserver(this)
    }

    override fun freeze(frozenRealm: RealmReference): ManagedRealmSet<E>? {
        return RealmInterop.realm_set_resolve_in(nativePointer, frozenRealm.dbPointer)?.let {
            ManagedRealmSet(parent, it, operator.copy(frozenRealm, it))
        }
    }

    override fun thaw(liveRealm: RealmReference): ManagedRealmSet<E>? {
        return RealmInterop.realm_set_resolve_in(nativePointer, liveRealm.dbPointer)?.let {
            ManagedRealmSet(parent, it, operator.copy(liveRealm, it))
        }
    }

    override fun registerForNotification(
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer {
        return RealmInterop.realm_set_add_notification_callback(nativePointer, callback)
    }

    override fun changeFlow(scope: ProducerScope<SetChange<E>>): ChangeFlow<ManagedRealmSet<E>, SetChange<E>> =
        RealmSetChangeFlow(scope)

    override fun delete() {
        RealmInterop.realm_set_remove_all(nativePointer)
    }

    override fun isValid(): Boolean {
        return !nativePointer.isReleased() && RealmInterop.realm_set_is_valid(nativePointer)
    }
}

internal fun <E : BaseRealmObject> ManagedRealmSet<E>.query(
    query: String,
    args: Array<out Any?>
): RealmQuery<E> {
    val operator: RealmObjectSetOperator<E> = operator as RealmObjectSetOperator<E>
    val queryPointer = inputScope {
        val queryArgs = convertToQueryArgs(args)
        RealmInterop.realm_query_parse_for_set(
            this@query.nativePointer,
            query,
            queryArgs
        )
    }
    if (parent == null) error("Cannot perform subqueries on non-object sets")
    return ObjectBoundQuery(
        parent,
        ObjectQuery(
            operator.realmReference,
            operator.classKey,
            operator.clazz,
            operator.mediator,
            queryPointer,
        )
    )
}

/**
 * Operator interface abstracting the connection between the API and and the interop layer.
 */
internal interface SetOperator<E> : CollectionOperator<E, RealmSetPointer> {

    // Modification counter used to detect concurrent writes from the iterator, taken from Java's
    // AbstractList implementation
    var modCount: Int
    override val nativePointer: RealmSetPointer

    fun add(
        element: E,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Boolean {
        return addInternal(element, updatePolicy, cache)
            .also { modCount++ }
    }

    fun addInternal(
        element: E,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Boolean

    fun addAll(
        elements: Collection<E>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Boolean {
        realmReference.checkClosed()
        return addAllInternal(elements, updatePolicy, cache)
            .also { modCount++ }
    }

    fun addAllInternal(
        elements: Collection<E>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Boolean {
        @Suppress("VariableNaming")
        var changed = false
        for (e in elements) {
            val hasChanged = addInternal(e, updatePolicy, cache)
            if (hasChanged) {
                changed = true
            }
        }
        return changed
    }

    fun clear() {
        realmReference.checkClosed()
        RealmInterop.realm_set_clear(nativePointer)
        modCount++
    }

    fun removeInternal(element: E): Boolean
    fun remove(element: E): Boolean {
        return removeInternal(element).also {
            // FIXME Should this only be updated if above value is true?
            modCount++
        }
    }

    fun removeAll(elements: Collection<E>): Boolean {
        return elements.fold(false) { accumulator, value ->
            remove(value) or accumulator
        }
    }

    fun get(index: Int): E
    fun contains(element: E): Boolean
    fun copy(realmReference: RealmReference, nativePointer: RealmSetPointer): SetOperator<E>
}

internal fun realmAnySetOperator(
    mediator: Mediator,
    realm: RealmReference,
    nativePointer: RealmSetPointer,
    issueDynamicObject: Boolean = false,
    issueDynamicMutableObject: Boolean = false,
): RealmAnySetOperator = RealmAnySetOperator(
    mediator,
    realm,
    nativePointer,
    issueDynamicObject,
    issueDynamicMutableObject
)

internal class RealmAnySetOperator(
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    override val nativePointer: RealmSetPointer,
    val issueDynamicObject: Boolean,
    val issueDynamicMutableObject: Boolean
) : SetOperator<RealmAny?> {

    override var modCount: Int = 0

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): RealmAny? {
        return getterScope {
            val transport = realm_set_get(nativePointer, index.toLong())
            return realmValueToRealmAny(
                transport, null, mediator, realmReference,
                issueDynamicObject,
                issueDynamicMutableObject,
                { error("Set should never container sets") },
                { error("Set should never container lists") }
            ) { error("Set should never container dictionaries") }
        }
    }

    override fun addInternal(
        element: RealmAny?,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): Boolean {
        return inputScope {
            realmAnyHandler(
                value = element,
                primitiveValues = { realmValue: RealmValue ->
                    RealmInterop.realm_set_insert(nativePointer, realmValue)
                },
                reference = { realmValue ->
                    val obj = when (issueDynamicObject) {
                        true -> realmValue.asRealmObject<DynamicRealmObject>()
                        false -> realmValue.asRealmObject<RealmObject>()
                    }
                    val objRef =
                        realmObjectToRealmReferenceWithImport(obj, mediator, realmReference, updatePolicy, cache)
                    RealmInterop.realm_set_insert(nativePointer, realmObjectTransport(objRef))
                },
                set = { realmValue -> throw IllegalArgumentException("Sets cannot contain other collections") },
                list = { realmValue -> throw IllegalArgumentException("Sets cannot contain other collections ") },
                dictionary = { realmValue -> throw IllegalArgumentException("Sets cannot contain other collections ") },
            )
        }
    }

    override fun removeInternal(element: RealmAny?): Boolean {
        if (element?.type == RealmAny.Type.OBJECT) {
            if (!element.asRealmObject<RealmObjectInternal>().isManaged()) return false
        }
        return inputScope {
            val transport = realmAnyToRealmValueWithoutImport(element)
            RealmInterop.realm_set_erase(nativePointer, transport)
        }
    }

    override fun contains(element: RealmAny?): Boolean {
        // Unmanaged objects are never found in a managed dictionary
        if (element?.type == RealmAny.Type.OBJECT) {
            if (!element.asRealmObject<RealmObjectInternal>().isManaged()) return false
        }
        return inputScope {
            val transport = realmAnyToRealmValueWithoutImport(element)
            RealmInterop.realm_set_find(nativePointer, transport)
        }
    }

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmSetPointer
    ): SetOperator<RealmAny?> =
        RealmAnySetOperator(mediator, realmReference, nativePointer, issueDynamicObject, issueDynamicMutableObject)
}

internal class PrimitiveSetOperator<E>(
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    val realmValueConverter: RealmValueConverter<E>,
    override val nativePointer: RealmSetPointer
) : SetOperator<E> {

    override var modCount: Int = 0

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        return getterScope {
            with(realmValueConverter) {
                val transport = realm_set_get(nativePointer, index.toLong())
                realmValueToPublic(transport)
            } as E
        }
    }

    override fun addInternal(
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): Boolean {
        return inputScope {
            with(realmValueConverter) {
                val transport = publicToRealmValue(element)
                RealmInterop.realm_set_insert(nativePointer, transport)
            }
        }
    }

    override fun removeInternal(element: E): Boolean {
        return inputScope {
            with(realmValueConverter) {
                val transport = publicToRealmValue(element)
                RealmInterop.realm_set_erase(nativePointer, transport)
            }
        }
    }

    override fun contains(element: E): Boolean {
        return inputScope {
            with(realmValueConverter) {
                val transport = publicToRealmValue(element)
                RealmInterop.realm_set_find(nativePointer, transport)
            }
        }
    }

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmSetPointer
    ): SetOperator<E> =
        PrimitiveSetOperator(mediator, realmReference, realmValueConverter, nativePointer)
}

internal class RealmObjectSetOperator<E : BaseRealmObject?> : SetOperator<E> {

    override val mediator: Mediator
    override val realmReference: RealmReference
    override val nativePointer: RealmSetPointer
    val clazz: KClass<E & Any>
    val classKey: ClassKey

    constructor(
        mediator: Mediator,
        realmReference: RealmReference,
        nativePointer: RealmSetPointer,
        clazz: KClass<E & Any>,
        classKey: ClassKey
    ) {
        this.mediator = mediator
        this.realmReference = realmReference
        this.nativePointer = nativePointer
        this.clazz = clazz
        this.classKey = classKey
    }

    override var modCount: Int = 0

    override fun addInternal(
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): Boolean {
        return inputScope {
            val objRef = realmObjectToRealmReferenceWithImport(
                element as BaseRealmObject?,
                mediator,
                realmReference,
                updatePolicy,
                cache
            )
            val transport = realmObjectTransport(objRef as RealmObjectInterop)
            RealmInterop.realm_set_insert(nativePointer, transport)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        return getterScope {
            realm_set_get(nativePointer, index.toLong())
                .let { transport ->
                    when (ValueType.RLM_TYPE_NULL) {
                        transport.getType() -> null
                        else -> realmValueToRealmObject(transport, clazz, mediator, realmReference)
                    }
                } as E
        }
    }

    override fun removeInternal(element: E): Boolean {
        // Unmanaged objects are never found in a managed set
        element?.also {
            if (!(it as RealmObjectInternal).isManaged()) return false
        }
        return inputScope {
            val transport = realmObjectToRealmValue(element as BaseRealmObject?)
            RealmInterop.realm_set_erase(nativePointer, transport)
        }
    }

    override fun contains(element: E): Boolean {
        // Unmanaged objects are never found in a managed set
        element?.also {
            if (!(it as RealmObjectInternal).isManaged()) return false
        }
        return inputScope {
            val objRef = realmObjectToRealmReferenceOrError(element as BaseRealmObject?)
            val transport = realmObjectTransport(objRef as RealmObjectInterop)
            RealmInterop.realm_set_find(nativePointer, transport)
        }
    }

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmSetPointer
    ): SetOperator<E> {
        return RealmObjectSetOperator(mediator, realmReference, nativePointer, clazz, classKey)
    }
}

internal class RealmSetChangeFlow<E>(scope: ProducerScope<SetChange<E>>) :
    ChangeFlow<ManagedRealmSet<E>, SetChange<E>>(scope) {
    override fun initial(frozenRef: ManagedRealmSet<E>): SetChange<E> = InitialSetImpl(frozenRef)

    override fun update(frozenRef: ManagedRealmSet<E>, change: RealmChangesPointer): SetChange<E>? {
        val builder = SetChangeSetBuilderImpl(change)
        return UpdatedSetImpl(frozenRef, builder.build())
    }

    override fun delete(): SetChange<E> = DeletedSetImpl(UnmanagedRealmSet())
}

internal fun <T> Array<out T>.asRealmSet(): RealmSet<T> =
    UnmanagedRealmSet<T>().apply { addAll(this@asRealmSet) }
