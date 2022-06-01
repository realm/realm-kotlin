package io.realm.kotlin.query

/**
 * Queries that return scalar, nullable values. This type of query is used to more accurately
 * represent the results provided by some query operations, e.g. [RealmQuery.min] or
 * [RealmQuery.max].
 */
public interface RealmScalarNullableQuery<T> : RealmScalarQuery<T?>

/**
 * Similar to [RealmScalarNullableQuery.find] but it receives a [block] in which the scalar result
 * from the query is provided.
 *
 * @param T the type of the query
 * @param R the type returned by [block]
 * @return whatever [block] returns
 */
public fun <T, R> RealmScalarNullableQuery<T>.find(block: (T?) -> R): R = find().let(block)
