package io.realm

import io.realm.runtimeapi.RealmModel

// FIXME Realms, Results and Lists are queryable, but maybe not needed as an interface dependent on
//  how the final API is going to look like.
interface Queryable<T: RealmModel> {
    // FIXME EVALUTE Do we need explicit filter and sort methods that piles up arguments and
    //  triggers the actual query parse later?
    fun query(query: String = "TRUEPREDICATE", vararg args: Any ): RealmResults<T>
}
