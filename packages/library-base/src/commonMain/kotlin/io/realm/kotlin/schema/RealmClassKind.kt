package io.realm.kotlin.schema

/**
 * Enum describing what kind of Realm object it is.
 */
public enum class RealmClassKind {
    /**
     * Standard Realm objects are the default kind of object in Realm, and they extend the
     * [io.realm.kotlin.types.RealmObject] interface.
     */
    STANDARD,
    /**
     * Embedded Realm objects extend the [io.realm.kotlin.types.EmbeddedRealmObject] interface.
     *
     * These kinds of classes can only have one parent object that is owning them, which means
     * they are deleted when the parent object is.
     *
     * See [io.realm.kotlin.types.EmbeddedRealmObject] for more details.
     */
    EMBEDDED,
    /**
     * Asymmetric Realm objects extend the [io.realm.kotlin.types.mongodb.AsymmetricRealmObject] interface.
     *
     * These kind of classes can only be used in a synced Realm and are "write-only", i.e. once
     * you written an asymmetric object to a Realm, it is no longer possible access or query them.
     *
     * See [io.realm.kotlin.types.mongodb.AsymmetricRealmObject] for more details.
     */
    ASYMMETRIC
}
