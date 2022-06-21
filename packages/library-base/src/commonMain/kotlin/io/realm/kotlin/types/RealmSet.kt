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

package io.realm.kotlin.types

import io.realm.kotlin.Deleteable
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.internal.CollectionOperator
import io.realm.kotlin.internal.CoreExceptionConverter
import io.realm.kotlin.internal.InternalDeleteable
import io.realm.kotlin.internal.Mediator
import io.realm.kotlin.internal.ObjectCache
import io.realm.kotlin.internal.RealmReference
import io.realm.kotlin.internal.RealmValueConverter
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmSetPointer
import io.realm.kotlin.internal.realmObjectToRealmValue
import kotlin.reflect.KClass

/**
 * SEE
 * https://docs.google.com/document/d/1fhsHtMSV3UtXBriZCbr655GWQjVNVrmDmFa8LXLQQRg/edit
 */
public interface RealmSet<E> : MutableSet<E>, Deleteable

internal class UnmanagedRealmSet<E> : RealmSet<E>, InternalDeleteable,
    MutableSet<E> by mutableSetOf() {
    override fun delete() {
        throw UnsupportedOperationException("Unmanaged sets cannot be deleted.")
    }
}

internal class ManagedRealmSet<E>(
    internal val nativePointer: RealmSetPointer,
    val operator: SetOperator<E>
) : AbstractMutableSet<E>(), RealmSet<E>, InternalDeleteable {

    override val size: Int
        get() {
            operator.realmReference.checkClosed()
            return RealmInterop.realm_set_size(nativePointer).toInt()
        }

    override fun add(element: E): Boolean {
        // TODO("Not yet implemented")
        try {
            return operator.insert(element)
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

    override fun iterator(): MutableIterator<E> {
        TODO("Not yet implemented")
    }

    override fun delete() {
        TODO("Not yet implemented")
    }
}

internal interface SetOperator<E> : CollectionOperator<E> {

    fun insert(
        element: E,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: ObjectCache = mutableMapOf()
    ): Boolean

    fun insertAll(
        elements: Collection<E>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: ObjectCache = mutableMapOf()
    ): Boolean {
        @Suppress("VariableNaming")
        var changed = false
        for (e in elements) {
            val hasChanged = insert(e, updatePolicy, cache)
            if (hasChanged) {
                changed = true
            }
        }
        return changed
    }

    // TODO other inserts
    // TODO iterator
    // TODO contains
    // TODO containsAll
    // TODO intersection
    // TODO union
}

internal class PrimitiveSetOperator<E>(
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    override val converter: RealmValueConverter<E>,
    private val nativePointer: RealmSetPointer
) : SetOperator<E> {

    override fun insert(element: E, updatePolicy: UpdatePolicy, cache: ObjectCache): Boolean {
        return RealmInterop.realm_set_insert(nativePointer, converter.publicToRealmValue(element))
    }
}

internal class RealmObjectSetOperator<E>(
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    override val converter: RealmValueConverter<E>,
    private val clazz: KClass<*>,
    private val nativePointer: RealmSetPointer
) : SetOperator<E> {

    override fun insert(element: E, updatePolicy: UpdatePolicy, cache: ObjectCache): Boolean {
        val realmObjectToRealmValue = realmObjectToRealmValue(
            element as BaseRealmObject?,
            mediator,
            realmReference,
            updatePolicy,
            cache
        )
        return RealmInterop.realm_set_insert(nativePointer, realmObjectToRealmValue)
    }
}

// TODO
public fun <E> realmSetOf(): RealmSet<E> = UnmanagedRealmSet()
