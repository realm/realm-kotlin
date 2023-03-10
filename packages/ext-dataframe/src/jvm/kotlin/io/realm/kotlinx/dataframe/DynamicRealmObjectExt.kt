package io.realm.kotlinx.dataframe

import io.realm.kotlin.dynamic.DynamicRealm
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlinx.dataframe.internal.createDataFrameForObject
import org.jetbrains.kotlinx.dataframe.AnyFrame

/**
 * Convert a [DynamicRealmObject] to a dataframe.
 *
 * @param realm Realm from which the [DynamicRealmObject] were created. This is a temporary
 * work-around for [DynamicRealmObject] not exposing their owner Realm.
 * @return a dataframe representing the object.
 */
public fun DynamicRealmObject.toDataFrame(realm: DynamicRealm): AnyFrame {
    val type: String = this.type
    val schema = realm.schema()
    if (schema[type] == null) {
        throw IllegalArgumentException("DynamicRealmObject contained unknown type: $type")
    }
    return createDataFrameForObject(schema, this)
}