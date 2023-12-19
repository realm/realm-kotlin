package io.realm.kotlin.types

/**
 * TODO
 *
 * @param S storage type.
 * @param U user type.
 */
// TODO Perform some validation on supported R realm-types
public interface RealmTypeAdapter<S, U> { // where S is a supported realm type

    public fun fromRealm(realmValue: S): U

    public fun toRealm(value: U): S
}
