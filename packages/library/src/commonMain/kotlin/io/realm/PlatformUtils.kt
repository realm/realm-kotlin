package io.realm

expect internal object PlatformUtils {
    fun getPathOrUseDefaultLocation(realmConfiguration: RealmConfiguration): String
}
