package io.realm.internal

import io.realm.RealmObject
import io.realm.internal.interop.NativePointer
import kotlin.reflect.KClass

/**
 * An InternalRealmConfiguration abstracts access to internal properties from a
 * [RealmConfiguration]. This is needed to make "agnostic" configurations from the base-sync point
 * of view.
 */
interface InternalRealmConfiguration {
    val mapOfKClassWithCompanion: Map<KClass<out RealmObject>, RealmObjectCompanion>
    val mediator: Mediator
    val nativeConfig: NativePointer
}
