package io.realm.kotlin.types

/**
 * TODO
 */
public interface RealmProjection<O: TypedRealmObject, T: Any> {
	public fun projectInto(): T
}

/**
 * TODO
 */
public interface RealmProjectionFactory<O: TypedRealmObject, T: Any> {
	public fun createProjection(origin: O): T
}

