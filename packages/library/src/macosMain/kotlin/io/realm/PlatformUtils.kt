package io.realm

//import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
//import platform.Foundation.NSUserDomainMask

actual object PlatformUtils {
    actual fun getPathOrUseDefaultLocation(realmConfiguration: RealmConfiguration): String {
        val directory = realmConfiguration.path ?:
//        NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).first().toString()
        NSFileManager.defaultManager.currentDirectoryPath
        val realmName = realmConfiguration.name ?: "default"
        return "$directory/${realmName}.realm".removePrefix("file://") //TODO still needed ?
    }
}
