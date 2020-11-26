package io.realm

import io.realm.runtimeapi.RealmModel

// FIXME QUERY-API
//  - Realms, Results and Lists are queryable, but maybe not needed as an interface
//    dependent on how the final API is going to look like.
//  - Query could alternatively be separated into builder to await constructing new results until
//    actually executing the query
interface Queryable<T: RealmModel> {
    fun query(query: String = "TRUEPREDICATE", vararg args: Any ): RealmResults<T>
}
