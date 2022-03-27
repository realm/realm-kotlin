package io.realm.mongodb.internal

import io.realm.mongodb.SyncSession

// Class containing all required fields and functionality that cannot be controlled purely through
// extension methods
internal class SyncedRealmBackingFieldsHolder {
    internal var session: SyncSession? = null
}