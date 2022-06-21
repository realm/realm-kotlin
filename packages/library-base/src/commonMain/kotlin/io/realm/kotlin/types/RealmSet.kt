package io.realm.kotlin.types

public interface RealmSet<E>

public fun <E> realmSetOf(): RealmSet<E> = object : RealmSet<E> {}
