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
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmSetPointer
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmSet
import kotlin.reflect.KClass

/**
 * TODO
 */
internal class UnmanagedRealmSet<E> : RealmSet<E>, MutableSet<E> by mutableSetOf()

/**
 * TODO
 */
internal class ManagedRealmSet<E>(
    internal val nativePointer: RealmSetPointer,
    val operator: SetOperator<E>
) : AbstractMutableSet<E>(), RealmSet<E> {

    override val size: Int
        get() {
            operator.realmReference.checkClosed()
            return RealmInterop.realm_set_size(nativePointer).toInt()
        }

    override fun add(element: E): Boolean {
        try {
            return operator.add(element)
        } catch (exception: Throwable) {
            throw CoreExceptionConverter.convertToPublicException(
                exception,
                "Could not add element to set"
            )
        }
    }

    override fun clear() {
        RealmInterop.realm_set_clear(nativePointer)
    }

    override fun iterator(): MutableIterator<E> = object : MutableIterator<E> {

        private var pos = -1

        override fun hasNext(): Boolean = pos + 1 < size

        override fun next(): E {
            pos = pos.inc()
            if (pos >= size) {
                throw NoSuchElementException("Cannot access index $pos when size is $size. Remember to check hasNext() before using next().")
            }
            return operator.get(pos)
        }

        override fun remove() {
            val element = RealmInterop.realm_set_get(nativePointer, pos.toLong())
            RealmInterop.realm_set_erase(nativePointer, element)
        }
    }
}

/**
 * TODO
 */
internal interface SetOperator<E> : CollectionOperator<E> {

    fun add(
        element: E,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: ObjectCache = mutableMapOf()
    ): Boolean

    fun addAll(
        elements: Collection<E>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: ObjectCache = mutableMapOf()
    ): Boolean {
        @Suppress("VariableNaming")
        var changed = false
        for (e in elements) {
            val hasChanged = add(e, updatePolicy, cache)
            if (hasChanged) {
                changed = true
            }
        }
        return changed
    }

    fun get(position: Int): E

    // TODO other inserts
    // TODO get
    // TODO contains
    // TODO containsAll
    // TODO intersection
    // TODO union

    fun copy(realmReference: RealmReference, nativePointer: RealmSetPointer): SetOperator<E>
}

/**
 * TODO
 */
internal class PrimitiveSetOperator<E>(
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    override val converter: RealmValueConverter<E>,
    private val nativePointer: RealmSetPointer
) : SetOperator<E> {

    override fun add(element: E, updatePolicy: UpdatePolicy, cache: ObjectCache): Boolean {
        val value = converter.publicToRealmValue(element)
        return RealmInterop.realm_set_insert(nativePointer, value)
    }

    override fun get(position: Int): E =
        RealmInterop.realm_set_get(nativePointer, position.toLong())
            ?.let { converter.realmValueToPublic(it) as E }

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmSetPointer
    ): SetOperator<E> = PrimitiveSetOperator(mediator, realmReference, converter, nativePointer)
}

/**
 * TODO
 */
internal class RealmObjectSetOperator<E>(
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    override val converter: RealmValueConverter<E>,
    private val clazz: KClass<*>,
    private val nativePointer: RealmSetPointer
) : SetOperator<E> {

    override fun add(element: E, updatePolicy: UpdatePolicy, cache: ObjectCache): Boolean {
        val realmObjectToRealmValue = realmObjectToRealmValue(
            element as BaseRealmObject?,
            mediator,
            realmReference,
            updatePolicy,
            cache
        )
        return RealmInterop.realm_set_insert(nativePointer, realmObjectToRealmValue)
    }

    override fun get(position: Int): E =
        RealmInterop.realm_set_get(nativePointer, position.toLong())
            ?.let { converter.realmValueToPublic(it) as E }

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmSetPointer
    ): SetOperator<E> {
        val converter = converter<E>(clazz, mediator, realmReference) as CompositeConverter<E, *>
        return RealmObjectSetOperator(mediator, realmReference, converter, clazz, nativePointer)
    }
}

internal fun <T> Array<out T>.asRealmSet(): RealmSet<T> =
    UnmanagedRealmSet<T>().apply { addAll(this@asRealmSet) }
