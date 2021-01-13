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
class Realm: BaseRealm() {

    interface Transaction {
        fun execute(realm: Realm)
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

    // Standard transaction methods
    fun beginTransaction() { TODO() }
    fun cancelTransaction() { TODO() }
    fun commitTransaction() { TODO() }
    fun isInTransaction() { TODO() }

    // The standard transaction wrapper now only comes as a coroutine variant
    suspend fun executeTransaction(transaction: Transaction) { TODO() }

    // Freezing Realms
    fun freeze(): Realm { TODO() }
    fun isFrozen(): Boolean { TODO() }

    // Managing refresh
    fun refresh() { TODO() }
    fun waitForChange() { TODO("Remove from public API?") }
    fun stopWaitForChange() { TODO("Remove from public API?") }
    fun setAutoRefresh(enabled: Boolean) { TODO("Remove from public API?") }
    fun isAutoRefresh(): Boolean { TODO("Remove from public API?") }

    // Unfortunately Closable is not an interface in Kotlin Common, so we cannot implement the Closable interface
    // Unsure what impact his have?
    fun close() { TODO() }
    fun isClosed() { TODO() }

    // Creating objects or getting them into Realm
    // How does these look in Cocoa, JS and .NET
    //~//    fun <E : RealmObject> add(obj: E, updateMode ): E
    //    void insert(Collection<? extends RealmModel> objects)
    //    void insert(RealmModel object)
    //    void insertOrUpdate(Collection<? extends RealmModel> objects)
    //    void insertOrUpdate(RealmModel object)
    //    <E extends RealmModel> E createEmbeddedObject(Class<E> clazz, RealmModel parentObject, String parentProperty)
    //    <E extends RealmModel> E createObject(Class<E> clazz)
    //    <E extends RealmModel> E createObject(Class<E> clazz, Object primaryKeyValue)
//    fun <E: RealmObject> create(clazz: KClass<E>): E  { TODO() }
//    fun <E: EmbeddedObject> create(clazz: KClass<E>): E  { TODO() }
    fun <E: RealmObject> add(obj: E): E { TODO() } // What do Cocoa do
    fun <E: RealmObject> addOrUpdate(obj: E, overrideSameValues: Boolean = false) { TODO() }
    fun <E: EmbeddedObject, P: RealmObject> add(obj: E, parent: P, property: String) { TODO() }

    // Copying objects out of Realm again
    fun <E : RealmObject> copyFromRealm(realmObject: E, maxDepth: Long = Long.MAX_VALUE): E { TODO() }
    fun <E : RealmObject> copyFromRealm(realmObjects: Iterable<E>, maxDepth: Long = Long.MAX_VALUE): List<E> { TODO() }

    // Queries
    // What to call these methods: object is a keyword, filter doesn't really fit single object find
    // `objects/object`, `findAll/findFirst`, `filter/filterFirst`
    suspend fun <E : BaseRealmModel> find(clazz: KClass<E>, primaryKey: Any): E? { TODO() }
    //    fun <E : BaseRealmModel> filterFirst(clazz: KClass<E>, filter: String = ""): RealmOptional<E> { TODO() }
    fun <E : BaseRealmModel> filter(clazz: KClass<E>, filter: String = ""): RealmResults<E> { TODO() }
    fun isEmpty(): Boolean { TODO() }

    // Deletions
    fun <E: BaseRealmModel> delete(clazz: KClass<E>) { TODO() }
    fun deleteAll() { TODO() }

    // Backups
    fun writeCopyTo(destination: RealmFile) { TODO() }
//    void writeCopyTo(File destination)
//    void writeEncryptedCopyTo(File destination, byte[] key)





    // Extension functions added in a future update
    // These are only available on JVM as InputStream et. al are Java API's
    // Open Question: What kind of JSON support is required for Kotlin Multiplatform?
//    fun <E : RealmObject> createAllFromJson(KClass<E> clazz, stream: InputStream)
//    <E extends RealmModel> void createAllFromJson(Class<E> clazz, InputStream inputStream)
//    <E extends RealmModel> void	createAllFromJson(Class<E> clazz, org.json.JSONArray json)
//    <E extends RealmModel> void	createAllFromJson(Class<E> clazz, String json)
//    <E extends RealmModel> E createObjectFromJson(Class<E> clazz, InputStream inputStream)
//    <E extends RealmModel> E createObjectFromJson(Class<E> clazz, org.json.JSONObject json)
//    <E extends RealmModel> E createObjectFromJson(Class<E> clazz, String json)
//    <E extends RealmModel> void	createOrUpdateAllFromJson(Class<E> clazz, InputStream in)
//    <E extends RealmModel> void	createOrUpdateAllFromJson(Class<E> clazz, org.json.JSONArray json)
//    <E extends RealmModel> void	createOrUpdateAllFromJson(Class<E> clazz, String json)
//    <E extends RealmModel> E createOrUpdateObjectFromJson(Class<E> clazz, InputStream in)
//    <E extends RealmModel> E createOrUpdateObjectFromJson(Class<E> clazz, org.json.JSONObject json)
//    <E extends RealmModel> E createOrUpdateObjectFromJson(Class<E> clazz, String json)
//    <E extends RealmModel> void createAllFromJson(Class<E> clazz, InputStream inputStream)
//    <E extends RealmModel> void	createAllFromJson(Class<E> clazz, org.json.JSONArray json)
//    <E extends RealmModel> void	createAllFromJson(Class<E> clazz, String json)


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

        fun getGlobalInstanceCount(config: RealmConfiguration) { TODO() }
        fun getLocalInstanceCount(config: RealmConfiguration) { TODO() }
    }
}