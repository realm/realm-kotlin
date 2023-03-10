package io.realm.kotlinx.dataframe

import io.realm.kotlin.dynamic.DynamicRealm
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.schema.RealmClass
import io.realm.kotlinx.dataframe.internal.createDataFrameForCollection
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataColumn
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.columns.ValueColumn

/**
 * Convert an entire [DynamicRealm] to a dataframe. The top-level dataframe will contain a list
 * of classes and a reference to their data.
 *
 * @return a dataframe representing the entire Realm file.
 */
public fun DynamicRealm.toDataFrame(): AnyFrame {
    val schema = this.schema()
    val classNames: List<String> = schema.classes.map { clazz -> clazz.name }
    val classes: ValueColumn<String> = DataColumn.createValueColumn("class", classNames)
    val data: List<AnyFrame> = schema.classes.map { classSchema: RealmClass ->
        val results: RealmResults<out DynamicRealmObject> = query(classSchema.name).find()
        createDataFrameForCollection(schema, classSchema, results)
    }
    return dataFrameOf(classes, DataColumn.createFrameColumn("data", data))
}