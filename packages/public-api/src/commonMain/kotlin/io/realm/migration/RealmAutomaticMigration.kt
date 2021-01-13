package io.realm.migration

interface RealmAutomaticMigration {
    fun migrate(migration: Migration, oldVersion: Long)
}