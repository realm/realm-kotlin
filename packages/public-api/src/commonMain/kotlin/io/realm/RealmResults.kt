package io.realm

import io.realm.base.BaseRealmModel
import io.realm.moonshoot.ProjectViewData
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.reflect.KClass

// Implement List instead of MutableList, because you cannot modify this list directly.
class RealmResults<E: BaseRealmModel> : List<E> {

    // Further filter the result
    fun filter(filter: String): RealmResults<E> { TODO() }
    fun sort(): RealmResults<E> { TODO() }
    fun distinct(): RealmResults<E> { TODO("This one doesn't exist in Java. Oversight?") }

    // rename from first(val)/last(val) in Java to firstOrDefault to match firstOrNull collection methods in Kotlin
    fun firstOrDefault(default: E): E { TODO() }
    fun lastOrDefault(default: E): E { TODO() }

    // Aggregate methods. Exposing as Flows instead of synchronous methods
    suspend fun minDate(property: String): Flow<LocalDateTime> { TODO() }
    suspend fun maxDate(property: String): Flow<LocalDateTime> { TODO() }
    suspend fun min(property: String): Flow<Number> { TODO() }
    suspend fun max(property: String): Flow<Number> { TODO() }
    suspend fun average(property: String): Flow<Double> { TODO() }
    suspend fun sum(property: String): Flow<Number> { TODO() }

    // Listen to changes
    // Don't support addChangeListener/removeChangeListener/removeAllListeners just yet
    suspend fun observe(): Flow<RealmResults<E>> { TODO() }
//    suspend fun observeChangeSet(): Flow<CollectionChange<OrderedCollectionChangeSet, RealmResults<E>>>

    // Deletions: Copy methods from Java
    fun deleteAllFromRealm() { TODO() }
    fun deleteFirstFromRealm() { TODO() }
    fun deleteLastFromRealm() { TODO() }
    fun deleteFromRealm(location: Int) { TODO() }

    // Bulk updates
    fun createSnapshot(): RealmResults<E> { TODO() }

    fun setBlob(property: String, value: ByteArray) { TODO() }
    fun setBoolean(property: String, value: Boolean) { TODO() }
    fun setByte(property: String, value: Byte) { TODO() }
    fun setDate(property: String, value: LocalDateTime) { TODO() }
    // fun setDecimal128(property: String, value: Decimal128) { TODO() }
    fun setDouble(property: String, value: Double) { TODO() }
    fun setFloat(property: String, value: Float) { TODO() }
    fun setInt(property: String, value: Int) { TODO() }
    // <T> void	setList(String fieldName, RealmList<T> list)
    fun setLong(property: String, value: Long) { TODO() }
    fun setNull(property: String) { TODO() }
    fun setObject(property: String, realmObject: BaseRealmModel) { TODO() }
    // void	setObjectId(String fieldName, ObjectId value)
    fun setShort(property: String, value: Short) { TODO() }
    fun setString(property: String, value: String) { TODO() }
    fun setValue(property: String, value: Any) { TODO() }

    // Utility methods
    val realm: Realm = TODO()
    fun asJSON(): String { TODO() }
    fun freeze(): RealmResults<E> { TODO() }
    fun isFrozen(): Boolean { TODO() }
    fun isManaged(): Boolean { TODO() }
    fun isValid(): Boolean { TODO() }

    // Interface methods
    override val size: Int get() = TODO("Not yet implemented")
    override fun contains(element: E): Boolean { TODO("Not yet implemented") }
    override fun containsAll(elements: Collection<E>): Boolean { TODO("Not yet implemented") }
    override fun get(index: Int): E { TODO("Not yet implemented") }
    override fun indexOf(element: E): Int { TODO("Not yet implemented") }
    override fun isEmpty(): Boolean { TODO("Not yet implemented") }
    override fun iterator(): Iterator<E> { TODO("Not yet implemented") }
    override fun lastIndexOf(element: E): Int { TODO("Not yet implemented") }
    override fun listIterator(): ListIterator<E> { TODO("Not yet implemented") }
    override fun listIterator(index: Int): ListIterator<E> { TODO("Not yet implemented") }
    override fun subList(fromIndex: Int, toIndex: Int): List<E> { TODO("Not yet implemented") }
}