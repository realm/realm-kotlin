package io.realm

import io.realm.internal.RealmModelInternal
import io.realm.internal.unmanage
import io.realm.internal.worker.LiveRealm
import io.realm.interop.RealmInterop
import kotlin.reflect.KClass

/**
 * Realm instance that allow write access to the underlying Realm file.
 *
 * This Realm instance is thread confined and objects fetched from this instance are all live.
 * Which means they will auto-update.
 *
 * Instances of this Realm are only available inside write transactions and migrations.
 */
class MutableRealm internal constructor(configuration: RealmConfiguration): LiveRealm(configuration) {

    public var version: Realm.VersionId = Realm.VersionId(0.toULong(), 0.toULong())

    init {
        version = Realm.VersionId.fromRealm(dbPointer!!)
    }

    /**
     * Creates a copy of an object in the Realm.
     *
     * This will create a copy of an object and all it's children. Any already managed objects will
     * not be copied, including the root `instance`. So invoking this with an already managed
     * object is a no-operation.
     *
     * @param instance The object to create a copy from.
     * @return The managed version of the `instance`.
     */
    fun <T : RealmObject<T>> copyToRealm(obj: T): T {
        return io.realm.internal.copyToRealm(configuration.schema, this, obj)
    }

    /**
     * FIXME
     */
    fun <T : RealmObject<T>> copyToRealm(objects: Collection<T>): List<T> {
        return objects.map { copyToRealm(it) }
    }

    // FIXME We should only expose `add/copyToRealm`
    private fun <T : RealmObject<T>> create(type: KClass<T>): T {
        return io.realm.internal.create(configuration.schema, this, type)
    }

    // Convenience inline method for the above to skip KClass argument
    private inline fun <reified T : RealmObject<T>> create(): T { return create(T::class) }

    // FIXME EVALUATE Should this be on RealmModel instead?
    fun <T : RealmObject<T>> delete(obj: T) {
        val internalObject = obj as RealmModelInternal
        internalObject.`$realm$ObjectPointer`?.let { RealmInterop.realm_object_delete(it) }
            ?: throw IllegalArgumentException("Cannot delete unmanaged object")
        internalObject.unmanage()
    }

    /**
     * FIXME: Refactor to RealmQuery Class
     * But also, should we attempt to find a way to share methods between MutableRealm and
     * PublicRealm? There is a little bit of overlap, but not sure if it makes sense.
     * We also need to factor in how DynamicRealm and MutableDynamicRealm is going to be exposed
     */
    fun <T : RealmObject<T>> objects(clazz: KClass<T>): RealmResults<T> {
        return RealmResults.fromQuery(
            this,
            @Suppress("SpreadOperator") // TODO PERFORMANCE Spread operator triggers detekt
            { RealmInterop.realm_query_parse(dbPointer!!, clazz.simpleName!!, "TRUEPREDICATE") },
            clazz,
            configuration.schema
        )
    }

    /**
     * Cancels the current write transaction.
     */
    internal fun cancelWrite() {
        RealmInterop.realm_rollback(dbPointer!!)
    }

    /**
     * Begin a write transaction
     */
    internal fun beginTransaction() {
        RealmInterop.realm_begin_write(dbPointer!!)
    }

    /**
     * Commit a write transaction
     */
    internal fun commitTransaction() {
        RealmInterop.realm_commit(dbPointer!!)
    }

    /**
     * Closes this Realm instance
     */
    override fun close() {
        RealmInterop.realm_close(dbPointer!!)
        dbPointer = null
    }
}