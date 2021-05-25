package io.realm.internal

import io.realm.BaseRealm
import io.realm.interop.NativePointer

/**
 * A RealmId uniquely identifies a specific version of the Realm file. Each Result, List or Object available to the user
 * is assigned to a specific transaction version. As there only exist on public `Realm` instance with its own `dbPointer`,
 * we cannot use that as a reference for objects since the Realms own dbPointer might update.
 *
 * Instead derived objects use the `RealmId` as their primary way of remembering their exact origin.
 *
 * TODO: Figure out exactly how to name this and consider if this is the best abstraction
 */
data class RealmId(val ref: BaseRealm, val dbPointer: NativePointer)