package io.realm.internal

import io.realm.log.RealmLogger

actual object TypeFactory {
    actual fun createDefaultSystemLogger(tag: String): RealmLogger = NSLogLogger(tag)
}