package io.realm.internal

import io.realm.log.RealmLogger

expect object TypeFactory {
    fun createDefaultSystemLogger(tag: String): RealmLogger
}
