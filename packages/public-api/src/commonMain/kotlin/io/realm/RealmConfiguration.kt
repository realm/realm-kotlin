
package io.realm

import io.realm.base.BaseRealmModel
import io.realm.dynamic.DynamicRealm
import io.realm.migration.MigrationType
import io.realm.migration.RealmAutomaticMigration
import io.realm.migration.RealmManualMigration
import kotlin.reflect.KClass

// Should RealmConfiguration or Builder be a data class?
class RealmConfiguration private constructor() {

    // Expose properties
    val fileName: String? = TODO()
    val path: String = TODO()
    val encryptionKey: ByteArray? = TODO()
    val assetFilePath: String? = TODO()
    val schemaVersion: Long = TODO()
    val migrationType: MigrationType = TODO()
    val initialDataTransaction: Realm.Transaction? = TODO()
    val isReadOnly: Boolean = TODO()
    val compactOnLaunch: CompactOnLaunchCallback? = TODO()
    val maxNumberOfActiveVersions: Long = TODO()
    val allowWritesOnUiThread: Boolean = TODO()
    val allowQueriesOnUiThread: Boolean = TODO()
    val isSyncConfiguration: Boolean
        get() = false
    val realmObjectClasses: Set<BaseRealmModel>
        get() = TODO()

    fun hasAssetFile(): Boolean { TODO() }
    fun realmExists(): Boolean { TODO() }

    class Builder constructor() {
        fun name(filename: String): Builder { TODO() }
        fun directory(directory: RealmFile): Builder { TODO() }
        fun encryptionKey(key: ByteArray): Builder { TODO() }

        // How is migrations defined in Swift with automatic migration?
        // Do we really want to expose both types of Migrations?
        fun automaticMigration(schemaVersion: Long, migration: RealmAutomaticMigration) { TODO() }
        fun manualMigration(schemaVersion: Long, migration: RealmManualMigration) { TODO() }
        fun deleteRealmIfMigrationNeeded(): Builder { TODO() }

        fun inMemory(): Builder { TODO() }

        // Unsure how schemas are defined in the Kotlin SDK? This 3 methods are supported in Realm Java
        fun modules(baseModule: Any?, vararg additionalModules: Any?): Builder { TODO() }
        fun addModule(module: Any): Builder { TODO() }
        fun schema(vararg schemaClasses: KClass<out BaseRealmModel>): Builder { TODO() }

        fun initialData(transaction: Realm.Transaction): Builder { TODO() }
        fun assetFile(assetFile: String): Builder { TODO() }
        fun readOnly(): Builder { TODO() }
        fun compactOnLaunch(compactOnLaunch: CompactOnLaunchCallback?): Builder { TODO() }
        fun maxNumberOfActiveVersions(number: Long): Builder { TODO() }
        fun allowWritesOnUiThread(allowWritesOnUiThread: Boolean): Builder { TODO () }
        fun allowQueriesOnUiThread(allowQueriesOnUiThread: Boolean): Builder { TODO() }
        fun build(): RealmConfiguration { TODO () }
    }
