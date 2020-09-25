package io.realm

expect object PlatformUtils {
    fun getPathOrUseDefaultLocation(realmConfiguration: RealmConfiguration): String
}