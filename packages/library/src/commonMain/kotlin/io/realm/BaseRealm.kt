package io.realm

import io.realm.internal.RealmResultsImpl
import kotlin.reflect.KClass

/**
 * Base class for all Realm instances ([Realm] and [MutableRealm]).
 */
interface BaseRealm {
    /**
     * Configuration used to configure this Realm instance.
     */
    val configuration: RealmConfiguration

    /**
     * The current version of the data in this realm.
     */
    // TODO Could be abstracted into base implementation of RealmLifeCycle!?
    var version: VersionId

    /**
     * Returns the results of querying for all objects of a specific type.
     *
     * For a [Realm] instance this reflects the state of the realm at the invocation time, thus
     * the results will not change on updates to the Realm. For a [MutableRealm] the result is live
     * and will in fact reflect updates to the [MutableRealm].
     *
     * @param clazz The class of the objects to query for.
     * @return The result of the query as of the time of invoking this method.
     */
    open fun <T : RealmObject> objects(clazz: KClass<T>): RealmResults<T>

    /**
     * Returns the current number of active versions in the Realm file. A large number of active versions can have
     * a negative impact on the Realm file size on disk.
     *
     * @see [RealmConfiguration.Builder.maxNumberOfActiveVersions]
     */
    fun getNumberOfActiveVersions(): Long

    /**
     * Check if this Realm has been closed or not. If the Realm has been closed, most methods
     * will throw [IllegalStateException] if called.
     *
     * @return `true` if the Realm has been closed. `false` if not.
     */
    fun isClosed(): Boolean
}

inline fun <reified T : RealmObject> BaseRealm.objects(): RealmResults<T> {
    return this.objects(T::class)
}
