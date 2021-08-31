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

package io.realm

import io.realm.errors.RealmError
import io.realm.errors.RealmPrimaryKeyConstraintException
import io.realm.internal.Mediator
import io.realm.internal.RealmReference
import io.realm.internal.copyToRealm
import io.realm.interop.Link
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import io.realm.interop.ErrorType
import io.realm.interop.errors.RealmCoreException
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * RealmList is used to model one-to-many relationships in a [RealmObject].
 *
 * A RealmList has two modes: managed and unmanaged. In `managed` mode all objects are persisted
 * inside a Realm whereas in `unmanaged` mode it works as a normal [MutableList].
 *
 * Only Realm can create managed RealmLists. Managed RealmLists will automatically update their
 * content whenever the underlying Realm is updated. Said content can only be accessed using the
 * getter of a [RealmObject].
 *
 * Unmanaged RealmLists can be created by the user and can contain both managed and unmanaged
 * [RealmObject]s. This is useful when dealing with JSON deserializers like GSON or other frameworks
 * that inject values into a class. Unmanaged elements in a list can be added to a Realm using the
 * [MutableRealm.copyToRealm] method.
 */
class RealmList<E> private constructor(
    private val delegate: RealmListApi<E>
) : MutableList<E> by delegate {

    internal val listPtr: NativePointer
        get() = delegate.listPtr

    /**
     * Constructs a RealmList in unmanaged mode.
     */
    constructor() : this(UnmanagedListDelegate())

    /**
     * Constructs a RealmList in managed mode. For internal use only.
     */
    constructor(
        listPtr: NativePointer,
        metadata: OperatorMetadata
    ) : this(ManagedListDelegate(listPtr, metadata))

    /**
     * Observe changes to a RealmList. Follows the pattern of [Realm.addChangeListener].
     */
    fun observe(): Flow<RealmList<E>> = delegate.observe(this)

    internal fun freeze(realm: RealmReference): RealmList<E> = delegate.freeze(realm)
    internal fun thaw(realm: RealmReference): RealmList<E> = delegate.thaw(realm)

    @Suppress("TooGenericExceptionCaught")
    internal fun isValid(): Boolean {
        // FIXME workaround until https://github.com/realm/realm-core/issues/4843 is done
        try {
            size
        } catch (e: RuntimeException) {
            if (e.message?.lowercase()?.contains("access to invalidated list object") == true) {
                return false
            }
        }
        return true
    }

    /**
     * Metadata needed to correctly instantiate a list operator. For internal use only.
     */
    data class OperatorMetadata(
        val clazz: KClass<*>,
        val mediator: Mediator,
        val realm: RealmReference
    )

    /**
     * Facilitates conversion between Realm Core types and Kotlin types and other Realm-related
     * checks.
     */
    internal class Operator<E>(
        private val metadata: OperatorMetadata
    ) {

        /**
         * Converts the underlying Core type to the correct type expressed in the RealmList.
         */
        @Suppress("UNCHECKED_CAST")
        fun convert(value: Any?): E {
            if (value == null) {
                return null as E
            }
            return with(metadata) {
                when (clazz) {
                    Byte::class -> (value as Long).toByte()
                    Char::class -> (value as Long).toChar()
                    Short::class -> (value as Long).toShort()
                    Int::class -> (value as Long).toInt()
                    Long::class,
                    Boolean::class,
                    Float::class,
                    Double::class,
                    String::class -> value
                    else -> (value as Link).toRealmObject(
                        clazz as KClass<out RealmObject>,
                        mediator,
                        realm
                    )
                } as E
            }
        }
    }
}

/**
 * Delegate interface for Realm-specific methods.
 */
internal interface RealmListApi<E> : MutableList<E> {

    val listPtr: NativePointer

    fun observe(list: RealmList<E>): Flow<RealmList<E>>
    fun freeze(frozenRealm: RealmReference): RealmList<E>
    fun thaw(liveRealm: RealmReference): RealmList<E>
}

/**
 * Represents an unmanaged [RealmList] backed by a [MutableList] via class delegation.
 */
private class UnmanagedListDelegate<E>(
    list: MutableList<E> = mutableListOf()
) : MutableList<E> by list, RealmListApi<E> {

    override val listPtr: NativePointer
        get() = throw UnsupportedOperationException("Unmanaged lists don't have a native pointer.")

    override fun observe(list: RealmList<E>): Flow<RealmList<E>> =
        throw UnsupportedOperationException("Unmanaged lists cannot be observed.")

    override fun freeze(frozenRealm: RealmReference): RealmList<E> =
        throw UnsupportedOperationException("Unmanaged lists cannot be frozen.")

    override fun thaw(liveRealm: RealmReference): RealmList<E> =
        throw UnsupportedOperationException("Unmanaged lists cannot be thawed.")
}

/**
 * Represents a managed [RealmList]. Its data will be persisted in Realm.
 *
 * [AbstractMutableList] provides enough default implementations on which we can rely. It can also
 * be used as a delegate since it implements [MutableList].
 */
private class ManagedListDelegate<E>(
    override val listPtr: NativePointer,
    private val metadata: RealmList.OperatorMetadata
) : AbstractMutableList<E>(), RealmListApi<E> {

    private val operator = RealmList.Operator<E>(metadata)

    override val size: Int
        get() {
            metadata.realm.checkClosed()
            return RealmInterop.realm_list_size(listPtr).toInt()
        }

    override fun get(index: Int): E {
        metadata.realm.checkClosed()
        try {
            return operator.convert(RealmInterop.realm_list_get(listPtr, index.toLong()))
        } catch (exception: RealmCoreException) {
            throw catchCoreErrors("Cannot get list element at index $index", exception)
        }
    }

    override fun add(index: Int, element: E) {
        metadata.realm.checkClosed()
        try {
            RealmInterop.realm_list_add(
                listPtr,
                index.toLong(),
                copyToRealm(metadata.mediator, metadata.realm, element)
            )
        } catch (exception: RealmCoreException) {
            throw catchCoreErrors("Cannot add list element at index $index", exception)
        }
    }

    // FIXME bug in AbstractMutableList.addAll native implementation:
    //  https://youtrack.jetbrains.com/issue/KT-47211
    //  Remove this method once the native implementation has a check for valid index
    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        metadata.realm.checkClosed()
        rangeCheckForAdd(index)
        return super.addAll(index, elements)
    }

    override fun clear() {
        metadata.realm.checkClosed()
        RealmInterop.realm_list_clear(listPtr)
    }

    override fun removeAt(index: Int): E = get(index).also {
        metadata.realm.checkClosed()
        try {
            RealmInterop.realm_list_erase(listPtr, index.toLong())
        } catch (exception: RealmCoreException) {
            throw catchCoreErrors("Cannot remove list element at index $index", exception)
        }
    }

    override fun set(index: Int, element: E): E {
        metadata.realm.checkClosed()
        try {
            return operator.convert(
                RealmInterop.realm_list_set(
                    listPtr,
                    index.toLong(),
                    copyToRealm(metadata.mediator, metadata.realm, element)
                )
            )
        } catch (exception: RealmCoreException) {
            throw catchCoreErrors("Cannot set list element at index $index", exception)
        }
    }

    override fun observe(list: RealmList<E>): Flow<RealmList<E>> {
        metadata.realm.checkClosed()
        return metadata.realm.owner.registerListObserver(list)
    }

    override fun freeze(frozenRealm: RealmReference): RealmList<E> {
        val frozenList = RealmInterop.realm_list_freeze(listPtr, frozenRealm.dbPointer)
        return RealmList(frozenList, metadata.copy(realm = frozenRealm))
    }

    override fun thaw(liveRealm: RealmReference): RealmList<E> {
        val liveList = RealmInterop.realm_list_thaw(listPtr, liveRealm.dbPointer)
        return RealmList(liveList, metadata.copy(realm = liveRealm))
    }

    private fun rangeCheckForAdd(index: Int) {
        if (index < 0 || index > size) {
            throw IndexOutOfBoundsException("Index: '$index', Size: '$size'")
        }
    }
}

fun catchCoreErrors(message: String, cause: RealmCoreException): Throwable {
    return when (cause.type) {
        ErrorType.RLM_ERR_OUT_OF_MEMORY,
        ErrorType.RLM_ERR_MULTIPLE_SYNC_AGENTS,
        ErrorType.RLM_ERR_UNSUPPORTED_FILE_FORMAT_VERSION,
        ErrorType.RLM_ERR_ADDRESS_SPACE_EXHAUSTED,
        ErrorType.RLM_ERR_MAXIMUM_FILE_SIZE_EXCEEDED,
        ErrorType.RLM_ERR_OUT_OF_DISK_SPACE,
        ErrorType.RLM_ERR_INVALID_PATH_ERROR -> RealmError(message, cause)
        ErrorType.RLM_ERR_MISSING_PRIMARY_KEY,
        ErrorType.RLM_ERR_UNEXPECTED_PRIMARY_KEY,
        ErrorType.RLM_ERR_WRONG_PRIMARY_KEY_TYPE,
        ErrorType.RLM_ERR_MODIFY_PRIMARY_KEY,
        ErrorType.RLM_ERR_DUPLICATE_PRIMARY_KEY_VALUE -> RealmPrimaryKeyConstraintException(message, cause)
        ErrorType.RLM_ERR_INDEX_OUT_OF_BOUNDS -> IndexOutOfBoundsException(message)
        ErrorType.RLM_ERR_NOT_IN_A_TRANSACTION,
        ErrorType.RLM_ERR_LOGIC -> IllegalStateException(message, cause)
        ErrorType.RLM_ERR_INVALID_ARGUMENT,
        ErrorType.RLM_ERR_INVALID_QUERY_STRING,
        ErrorType.RLM_ERR_OTHER_EXCEPTION,
        ErrorType.RLM_ERR_INVALID_QUERY -> IllegalArgumentException(message, cause)
//        ErrorType.RLM_ERR_UNKNOWN ->
//        ErrorType.RLM_ERR_NOT_CLONABLE ->
//        ErrorType.RLM_ERR_WRONG_THREAD ->
//        ErrorType.RLM_ERR_INVALIDATED_OBJECT ->
//        ErrorType.RLM_ERR_INVALID_PROPERTY ->
//        ErrorType.RLM_ERR_MISSING_PROPERTY_VALUE ->
//        ErrorType.RLM_ERR_PROPERTY_TYPE_MISMATCH ->
//        ErrorType.RLM_ERR_READ_ONLY_PROPERTY ->
//        ErrorType.RLM_ERR_PROPERTY_NOT_NULLABLE ->
//        ErrorType.RLM_ERR_NO_SUCH_TABLE ->
//        ErrorType.RLM_ERR_NO_SUCH_OBJECT ->
//        ErrorType.RLM_ERR_CROSS_TABLE_LINK_TARGET ->
//        ErrorType.RLM_ERR_KEY_NOT_FOUND ->
//        ErrorType.RLM_ERR_COLUMN_NOT_FOUND ->
//        ErrorType.RLM_ERR_COLUMN_ALREADY_EXISTS ->
//        ErrorType.RLM_ERR_KEY_ALREADY_USED ->
//        ErrorType.RLM_ERR_SERIALIZATION_ERROR ->
//        ErrorType.RLM_ERR_CALLBACK ->
        else -> RuntimeException(message, cause)
    }
}
