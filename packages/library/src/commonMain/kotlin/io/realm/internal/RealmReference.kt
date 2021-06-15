package io.realm.internal

import io.realm.BaseRealm
import io.realm.VersionId
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop

/**
 * FIXME Update
 * A TransactionId makes it possible to track which public Realm instance an underlying C++ SharedRealm is associated
 * with (represented by the `dbPointer`). This is needed as each Results, List or Object need to know, both which
 * public Realm they belong to, but also what underlying SharedRealm they are part of. But since the public Realm
 * instance might update its own `dbPointer`, derived objects cannot just keep a pointer to the public Realm instance.
 *
 * For frozen Realms, the `dbPointer` will thus point to a specific version of a read transaction that is guaranteed
 * to not change.
 *
 * For live Realms, the `dbPointer` will point to a live SharedRealm that can advance its internal version.
 *
 * For both versions, the public Realm reference is allowed to change independently from the underlying
 * `dbPointer`, so care should be taken when accessing any methods on the public Realm.
 */
data class RealmReference(
    val owner: BaseRealm,
    val dbPointer: NativePointer
    // FIXME Should we keep a debug flag to assert that we have the right liveness state
) : RealmLifeCycle {

    override fun closed(): Boolean {
        return RealmInterop.realm_is_closed(dbPointer)
    }

    override fun version(): VersionId {
        checkClosed()
        return VersionId(RealmInterop.realm_get_version_id(dbPointer))
    }
}

interface RealmLifeCycle {
    fun closed(): Boolean
    fun version(): VersionId
}

// Inline this for a cleaner stack trace in case it throws.
@Suppress("MemberVisibilityCanBePrivate")
inline fun RealmLifeCycle.checkClosed() {
    if (closed()) {
        // FIXME Is this needed outside of RealmReference? Because we don't have access to the
        //  configuration in the interface
        throw IllegalStateException("Realm has been closed and is no longer accessible")
//        throw IllegalStateException("Realm has been closed and is no longer accessible: ${configuration.path}")
    }
}
