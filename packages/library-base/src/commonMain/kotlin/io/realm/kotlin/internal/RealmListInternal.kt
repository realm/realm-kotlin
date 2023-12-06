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

package io.realm.kotlin.internal

import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.Versioned
import io.realm.kotlin.internal.RealmValueArgumentConverter.convertToQueryArgs
import io.realm.kotlin.internal.interop.Callback
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.interop.RealmChangesPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmInterop.realm_list_get
import io.realm.kotlin.internal.interop.RealmInterop.realm_list_set_embedded
import io.realm.kotlin.internal.interop.RealmKeyPathArrayPointer
import io.realm.kotlin.internal.interop.RealmListPointer
import io.realm.kotlin.internal.interop.RealmNotificationTokenPointer
import io.realm.kotlin.internal.interop.RealmObjectInterop
import io.realm.kotlin.internal.interop.getterScope
import io.realm.kotlin.internal.interop.inputScope
import io.realm.kotlin.internal.query.ObjectBoundQuery
import io.realm.kotlin.internal.query.ObjectQuery
import io.realm.kotlin.internal.util.Validation
import io.realm.kotlin.notifications.ListChange
import io.realm.kotlin.notifications.internal.DeletedListImpl
import io.realm.kotlin.notifications.internal.InitialListImpl
import io.realm.kotlin.notifications.internal.UpdatedListImpl
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Implementation for unmanaged lists, backed by a [MutableList].
 */
internal class UnmanagedRealmList<E>(
    private val backingList: MutableList<E> = mutableListOf()
) : RealmList<E>, InternalDeleteable, MutableList<E> by backingList {
    override fun asFlow(keyPaths: List<String>?): Flow<ListChange<E>> =
        throw UnsupportedOperationException("Unmanaged lists cannot be observed.")

    override fun delete() {
        throw UnsupportedOperationException("Unmanaged lists cannot be deleted.")
    }

    override fun toString(): String = "UnmanagedRealmList{${joinToString()}}"

    override fun equals(other: Any?): Boolean = backingList == other

    override fun hashCode(): Int = backingList.hashCode()
}

/**
 * Implementation for managed lists, backed by Realm.
 */
internal class ManagedRealmList<E>(
    internal val parent: RealmObjectReference<*>,
    internal val nativePointer: RealmListPointer,
    val operator: ListOperator<E>,
) : AbstractMutableList<E>(), RealmList<E>, InternalDeleteable, CoreNotifiable<ManagedRealmList<E>, ListChange<E>>, Versioned by operator.realmReference {

    override val size: Int
        get() {
            operator.realmReference.checkClosed()
            return RealmInterop.realm_list_size(nativePointer).toInt()
        }

    override fun get(index: Int): E {
        operator.realmReference.checkClosed()
        return operator.get(index)
    }

    override fun add(index: Int, element: E) {
        operator.insert(index, element)
    }

    // We need explicit overrides of these to ensure that we capture duplicate references to the
    // same unmanaged object in our internal import caching mechanism
    override fun addAll(elements: Collection<E>): Boolean = operator.insertAll(size, elements)

    // We need explicit overrides of these to ensure that we capture duplicate references to the
    // same unmanaged object in our internal import caching mechanism
    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        checkPositionIndex(index, size)
        return operator.insertAll(index, elements)
    }

    override fun clear() {
        operator.realmReference.checkClosed()
        RealmInterop.realm_list_clear(nativePointer)
    }

    override fun removeAt(index: Int): E = get(index).also {
        operator.realmReference.checkClosed()
        RealmInterop.realm_list_erase(nativePointer, index.toLong())
    }

    override fun set(index: Int, element: E): E {
        operator.realmReference.checkClosed()
        return operator.set(index, element)
    }

    override fun asFlow(keyPaths: List<String>?): Flow<ListChange<E>> {
        operator.realmReference.checkClosed()
        val keyPathInfo = keyPaths?.let {
            Validation.isType<RealmObjectListOperator<*>>(operator, "Keypaths are only supported for lists of objects.")
            Pair(operator.classKey, keyPaths)
        }
        return operator.realmReference.owner.registerObserver(this, keyPathInfo)
    }

    override fun freeze(frozenRealm: RealmReference): ManagedRealmList<E>? {
        return RealmInterop.realm_list_resolve_in(nativePointer, frozenRealm.dbPointer)?.let {
            ManagedRealmList(parent, it, operator.copy(frozenRealm, it))
        }
    }

    override fun thaw(liveRealm: RealmReference): ManagedRealmList<E>? {
        return RealmInterop.realm_list_resolve_in(nativePointer, liveRealm.dbPointer)?.let {
            ManagedRealmList(parent, it, operator.copy(liveRealm, it))
        }
    }

    override fun registerForNotification(
        keyPaths: RealmKeyPathArrayPointer?,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer {
        return RealmInterop.realm_list_add_notification_callback(nativePointer, keyPaths, callback)
    }

    override fun changeFlow(scope: ProducerScope<ListChange<E>>): ChangeFlow<ManagedRealmList<E>, ListChange<E>> =
        RealmListChangeFlow(scope)

    // TODO from LifeCycle interface
    internal fun isValid(): Boolean =
        !nativePointer.isReleased() && RealmInterop.realm_list_is_valid(nativePointer)

    override fun delete() = RealmInterop.realm_list_remove_all(nativePointer)
}

internal class RealmListChangeFlow<E>(producerScope: ProducerScope<ListChange<E>>) :
    ChangeFlow<ManagedRealmList<E>, ListChange<E>>(producerScope) {
    override fun initial(frozenRef: ManagedRealmList<E>): ListChange<E> =
        InitialListImpl(frozenRef)

    override fun update(
        frozenRef: ManagedRealmList<E>,
        change: RealmChangesPointer
    ): ListChange<E> {
        val builder = ListChangeSetBuilderImpl(change)
        return UpdatedListImpl(frozenRef, builder.build())
    }

    override fun delete(): ListChange<E> = DeletedListImpl(UnmanagedRealmList())
}

internal fun <E : BaseRealmObject> ManagedRealmList<E>.query(
    query: String,
    args: Array<out Any?>
): RealmQuery<E> {
    val operator: BaseRealmObjectListOperator<E> = operator as BaseRealmObjectListOperator<E>
    val queryPointer = inputScope {
        val queryArgs = convertToQueryArgs(args)
        try {
            RealmInterop.realm_query_parse_for_list(
                this@query.nativePointer,
                query,
                queryArgs
            )
        } catch (e: IndexOutOfBoundsException) {
            throw IllegalArgumentException(e.message, e.cause)
        }
    }
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

// Cloned from https://github.com/JetBrains/kotlin/blob/master/libraries/stdlib/src/kotlin/collections/AbstractList.kt
private fun checkPositionIndex(index: Int, size: Int) {
    if (index < 0 || index > size) {
        throw IndexOutOfBoundsException("index: $index, size: $size")
    }
}

/**
 * Operator interface abstracting the connection between the API and and the interop layer.
 */
internal interface ListOperator<E> : CollectionOperator<E, RealmListPointer> {

    override val nativePointer: RealmListPointer

    fun get(index: Int): E

    // TODO OPTIMIZE We technically don't need update policy and cache for primitie lists but right now RealmObjectHelper.assign doesn't know how to differentiate the calls to the operator
    fun insert(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    )

    fun insertAll(
        index: Int,
        elements: Collection<E>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Boolean {
        @Suppress("VariableNaming")
        var _index = index
        var changed = false
        for (e in elements) {
            insert(_index++, e, updatePolicy, cache)
            changed = true
        }
        return changed
    }

    fun set(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): E

    // Creates a new operator from an existing one to be able to issue frozen/thawed instances of the list operating on the new version of the list
    fun copy(realmReference: RealmReference, nativePointer: RealmListPointer): ListOperator<E>
}

internal class PrimitiveListOperator<E>(
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    override val valueConverter: RealmValueConverter<E>,
    override val nativePointer: RealmListPointer
) : ListOperator<E> {

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        return getterScope {
            val transport = realm_list_get(nativePointer, index.toLong())
            with(valueConverter) {
                realmValueToPublic(transport) as E
            }
        }
    }

    override fun insert(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ) {
        inputScope {
            with(valueConverter) {
                val transport = publicToRealmValue(element)
                RealmInterop.realm_list_add(nativePointer, index.toLong(), transport)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun set(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): E {
        return get(index).also {
            inputScope {
                with(valueConverter) {
                    val transport = publicToRealmValue(element)
                    RealmInterop.realm_list_set(nativePointer, index.toLong(), transport)
                }
            }
        }
    }

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmListPointer
    ): ListOperator<E> =
        PrimitiveListOperator(mediator, realmReference, valueConverter, nativePointer)
}

internal abstract class BaseRealmObjectListOperator<E>(
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    override val valueConverter: RealmValueConverter<E>,
    override val nativePointer: RealmListPointer,
    val clazz: KClass<E & Any>,
    val classKey: ClassKey,
) : ListOperator<E> {

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        return getterScope {
            val transport = realm_list_get(nativePointer, index.toLong())
            with(valueConverter) {
                realmValueToPublic(transport) as E
            }
        }
    }
}

internal class RealmObjectListOperator<E>(
    mediator: Mediator,
    realmReference: RealmReference,
    converter: RealmValueConverter<E>,
    nativePointer: RealmListPointer,
    clazz: KClass<E & Any>,
    classKey: ClassKey,
) : BaseRealmObjectListOperator<E>(mediator, realmReference, converter, nativePointer, clazz, classKey) {

    override fun insert(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ) {
        inputScope {
            val objRef = realmObjectToRealmReferenceWithImport(
                element as BaseRealmObject?,
                mediator,
                realmReference,
                updatePolicy,
                cache
            )
            val transport = realmObjectTransport(objRef as RealmObjectInterop)
            RealmInterop.realm_list_add(nativePointer, index.toLong(), transport)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun set(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): E {
        return inputScope {
            val objRef = realmObjectToRealmReferenceWithImport(
                element as BaseRealmObject?,
                mediator,
                realmReference,
                updatePolicy,
                cache
            )
            val transport = realmObjectTransport(objRef as RealmObjectInterop)
            with(valueConverter) {
                val originalValue = get(index)
                RealmInterop.realm_list_set(nativePointer, index.toLong(), transport)
                originalValue
            }
        }
    }

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmListPointer
    ): ListOperator<E> {
        val converter: RealmValueConverter<E> =
            converter<E>(clazz, mediator, realmReference) as CompositeConverter<E, *>
        return RealmObjectListOperator(
            mediator,
            realmReference,
            converter,
            nativePointer,
            clazz,
            classKey
        )
    }
}

internal class EmbeddedRealmObjectListOperator<E : BaseRealmObject>(
    mediator: Mediator,
    realmReference: RealmReference,
    converter: RealmValueConverter<E>,
    nativePointer: RealmListPointer,
    clazz: KClass<E>,
    classKey: ClassKey,
) : BaseRealmObjectListOperator<E>(mediator, realmReference, converter, nativePointer, clazz, classKey) {

    @Suppress("UNCHECKED_CAST")
    override fun insert(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ) {
        val embedded = RealmInterop.realm_list_insert_embedded(nativePointer, index.toLong())
        val newObj = embedded.toRealmObject(
            element::class as KClass<BaseRealmObject>,
            mediator,
            realmReference
        )
        RealmObjectHelper.assign(newObj, element, updatePolicy, cache)
    }

    @Suppress("UNCHECKED_CAST")
    override fun set(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): E {
        return inputScope {
            // We cannot return the old object as it is deleted when losing its parent and cannot
            // return null as this is not allowed for lists with non-nullable elements, so just return
            // the newly created object even though it goes against the list API.
            val embedded = realm_list_set_embedded(nativePointer, index.toLong())
            with(valueConverter) {
                val newEmbeddedRealmObject = realmValueToPublic(embedded) as BaseRealmObject
                RealmObjectHelper.assign(newEmbeddedRealmObject, element, updatePolicy, cache)
                newEmbeddedRealmObject as E
            }
        }
    }

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmListPointer
    ): EmbeddedRealmObjectListOperator<E> {
        val converter: RealmValueConverter<E> =
            converter(clazz, mediator, realmReference) as CompositeConverter<E, *>
        return EmbeddedRealmObjectListOperator(
            mediator,
            realmReference,
            converter,
            nativePointer,
            clazz,
            classKey
        )
    }
}

internal fun <T> Array<out T>.asRealmList(): RealmList<T> =
    UnmanagedRealmList<T>().apply { addAll(this@asRealmList) }
