package io.realm.mongodb

import io.realm.Realm
import io.realm.internal.RealmImpl
import io.realm.internal.interop.RealmInterop
import io.realm.mongodb.internal.SyncSessionImpl
import io.realm.mongodb.internal.SyncedRealmBackingFieldsHolder

/**
 * This class contains extension methods that are available when using synced realms.
 *
 * They will also be available on local realms created using a [io.realm.RealmConfiguration], but
 * will throw an [IllegalStateException] if called in this case.
 */
public val Realm.syncSession: SyncSession
    get() {
        val r = (this as RealmImpl)
        val sessionPointer = RealmInterop.realm_sync_session_get(r.realmReference.dbPointer)
        return SyncSessionImpl(sessionPointer)
//
//        val config = this.configuration
//        if (config is SyncConfiguration) {
//            if (this is RealmImpl) {
//                initHolderIfNeeded(this)
//                return io.realm.mongodb.internal.SyncSessionImpl(0L)
//            } else {
//                throw IllegalState("This method is not available on objects of type: $this")
//            }
//        } else {
//            throw IllegalStateException("This method is only available on synchronized realms.")
//        }
    }
//
//private fun initHolderIfNeeded(realm: RealmImpl) {
//    this.syncFields = SyncedRealmBackingFieldsHolder().apply {
//    }
//}
