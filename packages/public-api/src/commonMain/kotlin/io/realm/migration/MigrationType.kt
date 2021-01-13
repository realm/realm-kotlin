package io.realm.migration

enum class MigrationType {
    NONE, // No migration is performed an exception is thrown is one is needed
    DELETE, // Existing Realm is deleted if migration is required
    AUTOMATIC, // Automatic migration is triggered
    MANUAL // Manual migration is triggered
}