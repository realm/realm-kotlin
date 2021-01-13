package io.realm.migration

import io.realm.dynamic.DynamicRealm

interface RealmManualMigration {
    fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long)
}