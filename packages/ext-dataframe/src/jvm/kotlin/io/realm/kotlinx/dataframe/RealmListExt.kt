package io.realm.kotlinx.dataframe

import io.realm.kotlin.dynamic.DynamicRealm
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmList
import io.realm.kotlinx.dataframe.internal.createDataFrameForCollection
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame

/**
 * Convert a [RealmList] to a dataframe.
 *
 * @param realm Realm from which the RealmResults were created. This is a temporary work-around for
 * [RealmResults] not exposing their owner Realm.
 * @return a dataframe representing all query results. If the results is empty, an empty dataframe
 * is returned.
 */
public fun RealmList<out DynamicRealmObject>.toDataFrame(realm: DynamicRealm): AnyFrame {
    return if (this.isEmpty()) {
        DataFrame.Empty
    } else {
        val type = first().type
        val classSchema = realm.schema()[type] ?: throw IllegalArgumentException("RealmList contained unknown type: $type")
        createDataFrameForCollection(realm.schema(), classSchema, this)
    }
}
