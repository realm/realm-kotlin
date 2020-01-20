package sample

import io.realm.RealmInitProvider

actual fun path(): String =
    RealmInitProvider.applicationContext.filesDir.absolutePath