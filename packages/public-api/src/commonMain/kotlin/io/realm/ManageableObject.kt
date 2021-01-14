package io.realm

// Copy from Realm Java
interface ManageableObject {
    fun isManaged(): Boolean { TODO() }
    fun isValid(): Boolean { TODO() }
    fun isFrozen(): Boolean { TODO() }
}