package io.realm.kotlin.types

/**
 * TODO
 *
 * @param R realm type.
 * @param U user type.
 */
// TODO Perform some validation on supported R realm-types
public interface RealmTypeAdapter<R, U> { // where P is a supported realm type

    public fun fromRealm(realmValue: R): U

    public fun toRealm(value: U): R
}
