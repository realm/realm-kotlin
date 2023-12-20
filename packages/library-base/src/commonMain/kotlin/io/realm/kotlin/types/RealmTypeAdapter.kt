package io.realm.kotlin.types

/**
 * TODO
 *
 * @param S storage type.
 * @param U user type.
 */
public interface RealmTypeAdapter<S, U> { // where S is a supported realm type

    public fun fromRealm(realmValue: S): U

    public fun toRealm(value: U): S
}
