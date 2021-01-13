package io.realm

// SAM interface for changelisteners similar to Realm Java
interface RealmChangeListener<T> {
    fun onChange(t: T)
}

