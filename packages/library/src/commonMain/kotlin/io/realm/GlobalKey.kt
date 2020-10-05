package io.realm

/**
 * Class that represents a _Realm Global Key_. All Realm objects, even objects with a primary keys
 * are assigned a global key. The global key never changes.
 *
 * The global key is available through [RealmModel.globalKey] and objects can always be found
 * with [Realmfind(type, globalKey)].
 *
 * FIXME: How to expose this concept?
 */
class GlobalKey {
}