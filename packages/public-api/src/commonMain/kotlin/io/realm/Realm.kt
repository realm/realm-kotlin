package io.realm

import io.realm.annotations.PrimaryKey
import io.realm.base.BaseRealm
import io.realm.base.BaseRealmModel
import io.realm.schema.RealmSchema
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

// Top level class
// This is Thread safe Realm, see https://docs.google.com/document/d/1EA3CiACX4oNrx8jWYUMI_xPeDr-9RDMTGxrws-Uwgrc/edit
// for how this can be implemented
open class Realm: BaseRealm() {

    interface Transaction {
        fun execute(realm: MutableRealm)
    }

    val configuration: RealmConfiguration = TODO()
    val schema: RealmSchema = TODO()
    val path: String = TODO("configuration.path")
    val version: Long = TODO("Check what this is")

    // Support for manual changelisteners. These are not not considered the primary
    // way of interacting with changes. Flows should be used for that.
    // Notifications for these will be delivered from the Notifier thread, which
    // means that if they drive the UI they must manually be posted back to the UI handler
    // API still undecided. See https://github.com/realm/realm-kotlin/pull/107#issue-541008514
    fun addChangeListener(listener: RealmChangeListener<Realm>) { TODO() }
    fun removeChangeListener(listener: RealmChangeListener<Realm>)  { TODO() }
    fun removeAllListeners() { TODO() }

    // Changes are available as Flows
    suspend fun observe(): Flow<Realm> { TODO() }

    // The standard transaction wrapper now only comes as a coroutine variant
    suspend fun <E, R> executeTransaction(frozenArg: E, function: (realm: MutableRealm, liveArg: E?) -> R): R { TODO() }
    suspend fun <R> executeTransaction(function: (realm: MutableRealm) -> R): R { TODO() }

    // TODO: We need non-coroutine writes for Java support. But directly or through some extension?
    fun <E, R> write(frozenArg: E, function: (MutableRealm, liveArg: E?) -> R): R { TODO() }
    fun <R> write(function: (MutableRealm) -> R): R { TODO() }

    // Pin Realm to a specific version.
    fun <R> pin(function: (realm: Realm) -> R) { TODO() }

    // Unfortunately Closable is not an interface in Kotlin Common, so we cannot implement the Closable interface
    // Unsure what impact his have?
    fun close() { TODO() }
    fun isClosed() { TODO() }

    // Queries
    // Shortcut for looking up objects using primary key is only available with coroutines
    // Every other use case must go through the RealmQuery class. This is in order to keep
    // the number of methods down, not to include name clashes
    // The naming of these two methods differ a lot between SDK's. Right now I settled on
    // `find` which is used in .NET and because `object` used elsewhere is a keyword and `filter`
    // because that is the standard method name for this in Kotlin collections.
    suspend fun <E : BaseRealmModel> find(clazz: KClass<E>, primaryKey: Any): E? { TODO() }
    fun <E : BaseRealmModel> filter(clazz: KClass<E>, filter: String = ""): RealmQuery<E> { TODO() }
    fun isEmpty(): Boolean { TODO() }

    // Backups
    fun writeCopyTo(destination: RealmFile, encryptionKey: ByteArray? = null) { TODO() }

    companion object {
        // How to do init()? Realm.init()? Seems hard to achieve. See Android source set for extension method

        // Managing default setup
        fun setDefaultConfiguration(configuration: RealmConfiguration) { TODO() }
        fun getDefaultConfiguration(): RealmConfiguration? { TODO() }
        fun removeDefaultConfiguration() { TODO("Is this really needed?") }
        fun getDefaultSchema(): Any { TODO("Returns the default schema module. Not sure how that is going to look, nor if it is needed?")}

        // Open method, renamed from getInstance(). See https://github.com/realm/realm-java/issues/5372#issuecomment-334686564
        fun open(configuration: RealmConfiguration = getDefaultConfiguration() ?: throw RuntimeException("No default configuration found.")): Realm { TODO() }
        // Should this be openAsync instead. In any case, this is going to break the pattern we have everywhere else
        // where the "standard" method is the suspend function. In this case though, this way of opening will probably
        // be rare .. or will it?
        suspend fun openInBackground(configuration: RealmConfiguration) { TODO() }

        fun compactRealm(config: RealmConfiguration): Boolean { TODO() }
        fun deleteRealm(config: RealmConfiguration): Boolean { TODO() }
        fun migrateRealm(config: RealmConfiguration): Boolean { TODO() }
    }
}