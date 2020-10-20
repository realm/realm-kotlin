package io.realm

internal expect object PlatformUtils {
    fun getPathOrUseDefaultLocation(realmConfiguration: RealmConfiguration): String
}
