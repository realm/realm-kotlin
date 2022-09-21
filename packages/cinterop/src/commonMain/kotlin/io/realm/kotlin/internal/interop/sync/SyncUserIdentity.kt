package io.realm.kotlin.internal.interop.sync

/**
 * Wrapper for `realm_user_identity`.
 * @see https://github.com/realm/realm-core/blob/master/src/realm.h
 */
data class SyncUserIdentity(val id: String, val provider: AuthProvider)
