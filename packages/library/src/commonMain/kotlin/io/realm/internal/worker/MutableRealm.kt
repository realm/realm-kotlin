package io.realm.internal.worker

import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.internal.RealmModelInternal
import io.realm.internal.unmanage
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlin.jvm.Volatile
import kotlin.reflect.KClass

/**
 * Realm instance that allow write access to the underlying Realm file.
 *
 * This Realm instance is thread confined and objects fetched from this instance are all live.
 * Which means they will auto-update.
 *
 * Instances of this Realm are only available inside write transactions and migrations.
 */
class MutableRealm internal constructor(public val configuration: RealmConfiguration) {

    @Volatile
    internal var dbPointer: NativePointer?
    public var version: PublicRealm.VersionId = PublicRealm.VersionId(0.toULong(), 0.toULong())

    init {
        val liveDbPointer = RealmInterop.realm_open(configuration.nativeConfig)
        dbPointer = liveDbPointer
        version = PublicRealm.VersionId.fromRealm(liveDbPointer)
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
    fun <T : RealmObject> copyToRealm(obj: T): T {
        return io.realm.internal.copyToRealm(configuration.schema, dbPointer!!, obj)
    }

    /**
     * FIXME
     */
    fun <T : RealmObject> copyToRealm(objects: Collection<T>): List<T> {
        return objects.map { copyToRealm(it) }
    }

    // FIXME We should only expose `add/copyToRealm`
    private fun <T : RealmObject> create(type: KClass<T>): T {
        return io.realm.internal.create(configuration.schema, dbPointer!!, type)
    }

    // Convenience inline method for the above to skip KClass argument
    private inline fun <reified T : RealmObject> create(): T { return create(T::class) }

    // FIXME EVALUATE Should this be on RealmModel instead?
    fun <T : RealmObject> delete(obj: T) {
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
    fun <T : RealmObject> objects(clazz: KClass<T>): RealmResults<T> {
        return RealmResults(
            dbPointer!!,
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
    internal fun close() {
        RealmInterop.realm_close(dbPointer!!)
        dbPointer = null
    }
}