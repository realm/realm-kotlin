package io.realm

import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class JvmRealmTests {

    val realm = Realm.open()

    // API
    RealmConfiguration.Builder.backupOnUpgrade(fileSuffix: String, backupRention: Long, TimeUnit.DAYS)
    RealmConfiguration.Builder.restoreFromBackupIfAvailable(fileSuffix: String)

    // Example
    val config = RealmConfiguration.Builder()
            .name("myrealm.realm")
            .schemaVersion(4)
            .migration(MyMigration())
            .backupOnUpgrade("backup", 30, TimeUnit.DAYS)
            .restoreFromBackupIfAvailable("backup")
            .build()


    // Full API
    val config = RealmConfiguration.Builder()
            .name("myrealm.realm")
            .schemaVersion(4)
            .migration(MyMigration())
            .backupOnUpgrade("backup", TimeUnit.Days(30))
            .restoreFromBackupIfAvailable("backup")
            .build()



    // Full API
    val config = RealmConfiguration.Builder()
            .name("myrealm.realm")
            .enableFileVersioning(
                    currentVersion = "v20",
                    backupVersions = arrayOf("v10", "v9"),
                    retentionPolicy = DefaultFileRetentionPolicy()
            )
            .schemaVersion(4)
            .migration(MyMigration())
            .build()

    // Use default retention policy
    val config = RealmConfiguration.Builder()
            .name("myrealm.realm")
            .enableFileVersioning(currentVersion = "v20", backupVersions = arrayOf("v10", "v9"))
            .schemaVersion(4)
            .migration(MyMigration())
            .build()

    // No backups configured yet
    val config = RealmConfiguration.Builder()
            .name("myrealm.realm")
            .enableFileVersioning(currentVersion = "v20", backupVersions = arrayOf())
            .schemaVersion(4)
            .migration(MyMigration())
            .build()


//
//
//    fun crud() {
//        val config = RealmConfiguration.Builder().build()
//        val realm = Realm.open(config)
//        realm.use {
//
//        }

    }

    fun coroutines() {
//        // Only available on JVM: https://github.com/Kotlin/kotlinx.coroutines/issues/195
//        realm.use {
//            runBlocking {
//                it.observe() // This will never complete
//            }
//        }
    }

    fun results() {
        val results = RealmResults<Person>()
        results.first()
    }
}

class MyMigration {

}
