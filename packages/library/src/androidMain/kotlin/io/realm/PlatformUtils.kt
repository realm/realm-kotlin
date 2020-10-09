package io.realm

import java.io.File

actual object PlatformUtils {
    actual fun getPathOrUseDefaultLocation(realmConfiguration: RealmConfiguration): String {
        val directory = realmConfiguration.path ?: RealmInitProvider.applicationContext.filesDir
        val realmName = realmConfiguration.name ?: "default"

        val tmpDir = File(RealmInitProvider.applicationContext.filesDir, ".realm.temp")
        tmpDir.mkdirs()
        return "$directory/$realmName.realm"
    }
}
