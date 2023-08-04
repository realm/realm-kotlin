package io.realm.kotlinx.dataframe.internal

import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.schema.ListPropertyType
import io.realm.kotlin.schema.MapPropertyType
import io.realm.kotlin.schema.RealmClass
import io.realm.kotlin.schema.RealmProperty
import io.realm.kotlin.schema.RealmSchema
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.schema.SetPropertyType
import io.realm.kotlin.schema.ValuePropertyType
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmSet
import io.realm.kotlinx.dataframe.internal.RealmDataFrameBuilder.ColumnType
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataColumn
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.explode
import kotlin.reflect.KType
import kotlin.reflect.full.createType

/**
 * The [org.jetbrains.kotlinx.dataframe.api.DataFrameBuilder] class is not suited for gradually
 * building up the data structure. So we use our own variant that is more suited to how can traverse
 * a Realm object graph.
 *
 * A builder should represent one Dataframe, i.e. if other dataframes are referenced, they
 * should be build by themselves or using another [RealmDataFrameBuilder] and then added using
 * [addValue] to a column of the type [ColumnType.DATAFRAME] or [ColumnType.COLUMN_GROUP].
 */
internal class RealmDataFrameBuilder(classSchema: RealmClass) {

    private val columnType = mutableListOf<ColumnType>()
    private val kotlinType = mutableListOf<KType>()
    private val header = mutableListOf<String>()
    private val values = mutableListOf<MutableList<Any?>>()
    // Fast way to map between a property name and their index in the above data structures.
    private val headerIndexLookup = mutableMapOf<String, Int>()

    init {
        classSchema.properties.forEach {
            when (it.type) {
                is ListPropertyType -> addColumn(it.name, ColumnType.DATAFRAME, it.type.storageType.kClass.createType())
                is SetPropertyType -> addColumn(it.name, ColumnType.DATAFRAME, it.type.storageType.kClass.createType())
                is MapPropertyType -> addColumn(it.name, ColumnType.DATAFRAME, it.type.storageType.kClass.createType())
                is ValuePropertyType -> {
                    if (it.type.storageType == RealmStorageType.OBJECT) {
                        addColumn(it.name, ColumnType.COLUMN_GROUP, it.type.storageType.kClass.createType())
                    } else {
                        addColumn(it.name, ColumnType.VALUE, it.type.storageType.kClass.createType())
                    }
                }
            }
        }
    }

    enum class ColumnType {
        VALUE,
        DATAFRAME,
        COLUMN_GROUP
    }

    private fun addColumn(name: String, column: ColumnType, type: KType) {
        header.add(name)
        columnType.add(column)
        kotlinType.add(type)
        values.add(mutableListOf())
        headerIndexLookup[name] = header.size - 1
    }

    fun build(): AnyFrame {
        val columns: List<DataColumn<Any?>> = header.mapIndexed { i, header ->
            when (columnType[i]) {
                ColumnType.VALUE -> {
                    DataColumn.createValueColumn(header, values[i], kotlinType[i])
                }
                ColumnType.DATAFRAME -> {
                    @Suppress("UNCHECKED_CAST")
                    DataColumn.createFrameColumn(header, values[i] as List<DataFrame<*>>)
                }
                ColumnType.COLUMN_GROUP -> {
                    // It is unclear how to create a ColumnGroup here, so we do it later flattening
                    // these. For now, just convert to dataframes.
                    @Suppress("UNCHECKED_CAST")
                    DataColumn.createFrameColumn(header, values[i] as List<DataFrame<*>>)
                }
            }
        }
        // Fix dataframes we really want to expose as column groups.
        val headersToMerge: List<String> = header.mapIndexed { i, header ->
            if (columnType[i] == ColumnType.COLUMN_GROUP) {
                header
            } else {
                null
            }
        }.filterNotNull()
        @Suppress("SpreadOperator")
        return dataFrameOf(columns).explode(*headersToMerge.toTypedArray())
    }

    fun addValue(propertyName: String, value: Any?) {
        val index = headerIndexLookup[propertyName] ?: throw IllegalStateException("Unknown property: $propertyName")
        val values = this.values[index]
        values.add(value)
    }
}

/**
 * Create a dataframe builder for a given Realm model class
 */
internal fun createBuilderForType(modelClass: RealmClass): RealmDataFrameBuilder {
    return RealmDataFrameBuilder(modelClass)
}

/**
 * Create a dataframe representing all for all objects of a given model class.
 */
internal fun createDataFrameForCollection(schema: RealmSchema, classSchema: RealmClass, collection: Iterable<out DynamicRealmObject>): AnyFrame {
    val builder = createBuilderForType(classSchema)
    collection.forEach { el: DynamicRealmObject ->
        addObjectToFrame(schema, classSchema, builder, el)
    }
    return builder.build()
}

/**
 * Create a dataframe representing a single object. Non-existing objects are represented  by
 * the empty frame since that is required to render it correctly in Jupyter.
 */
internal fun createDataFrameForObject(schema: RealmSchema, obj: DynamicRealmObject?): AnyFrame {
    return if (obj == null) {
        DataFrame.Empty
    } else {
        val classSchema: RealmClass = schema[obj.type]!!
        val builder = createBuilderForType(classSchema)
        addObjectToFrame(schema, classSchema, builder, obj)
        return builder.build()
    }
}

private fun addObjectToFrame(schema: RealmSchema, classSchema: RealmClass, builder: RealmDataFrameBuilder, obj: DynamicRealmObject) {
    classSchema.properties.map { prop: RealmProperty ->
        when (val type = prop.type) {
            is ListPropertyType -> addListPropertyToFrame(schema, type, obj, prop, builder)
            is SetPropertyType -> addSetPropertyToFrame(schema, type, obj, prop, builder)
            is MapPropertyType -> addMapPropertyToFrame(schema, type, obj, prop, builder)
            is ValuePropertyType -> {
                if (prop.type.storageType == RealmStorageType.OBJECT) {
                    val df: AnyFrame = createDataFrameForObject(schema, obj.getObject(prop.name))
                    builder.addValue(prop.name, df)
                } else {
                    if (prop.isNullable) {
                        val value: Any? = obj.getNullableValue(prop.name, prop.type.storageType.kClass)
                        builder.addValue(prop.name, value)
                    } else {
                        val value: Any = obj.getValue(prop.name, prop.type.storageType.kClass)
                        builder.addValue(prop.name, value)
                    }
                }
            }
        }
    }
}

private fun addListPropertyToFrame(
    schema: RealmSchema,
    listType: ListPropertyType,
    obj: DynamicRealmObject,
    prop: RealmProperty,
    builder: RealmDataFrameBuilder
) = when (listType.storageType) {
    RealmStorageType.OBJECT -> {
        val list: RealmList<out DynamicRealmObject> = obj.getObjectList(prop.name)
        if (list.isEmpty()) {
            // RealmSchema cannot tell the type of list items, so for now just return an empty dataframe.
            builder.addValue(prop.name, DataFrame.Empty)
        } else {
            val className: String = list.first().type
            val classSchema: RealmClass = schema[className]!!
            val listBuilder = createBuilderForType(classSchema)
            list.forEach { obj: DynamicRealmObject ->
                addObjectToFrame(schema, classSchema, listBuilder, obj)
            }
            builder.addValue(prop.name, listBuilder.build())
        }
    }
    else -> {
        val list = if (listType.isNullable) {
            obj.getNullableValueList(prop.name, listType.storageType.kClass)
        } else {
            obj.getValueList(prop.name, listType.storageType.kClass)
        }
        val data = DataColumn.createValueColumn("value", list, listType.storageType.kClass.createType())
        builder.addValue(prop.name, dataFrameOf(data))
    }
}

private fun addSetPropertyToFrame(
    schema: RealmSchema,
    setType: SetPropertyType,
    obj: DynamicRealmObject,
    prop: RealmProperty,
    builder: RealmDataFrameBuilder
) = when (setType.storageType) {
    RealmStorageType.OBJECT -> {
        val set: RealmSet<out DynamicRealmObject> = obj.getObjectSet(prop.name)
        if (set.isEmpty()) {
            // TODO RealmSchema cannot tell the type of set items :/
            builder.addValue(prop.name, DataFrame.Empty)
        } else {
            val className: String = set.first().type
            val classSchema: RealmClass = schema[className]!!
            val setBuilder = createBuilderForType(classSchema)
            set.forEach { obj: DynamicRealmObject ->
                addObjectToFrame(schema, classSchema, setBuilder, obj)
            }
            val df: AnyFrame = setBuilder.build()
            builder.addValue(prop.name, df)
        }
    }
    else -> {
        val list = if (setType.isNullable) {
            obj.getNullableValueSet(prop.name, setType.storageType.kClass).toList()
        } else {
            obj.getValueSet(prop.name, setType.storageType.kClass).toList()
        }
        val data = DataColumn.createValueColumn("value", list, setType.storageType.kClass.createType())
        builder.addValue(prop.name, dataFrameOf(data))
    }
}

private fun addMapPropertyToFrame(
    schema: RealmSchema,
    mapType: MapPropertyType,
    obj: DynamicRealmObject,
    prop: RealmProperty,
    builder: RealmDataFrameBuilder
): Nothing = when (mapType.storageType) {
    else -> TODO()
//    RealmStorageType.OBJECT -> {
//        val map: RealmDictionary<out DynamicRealmObject?> = obj.getObjectDictionary(prop.name)
//        if (map.isEmpty()) {
//            // TODO RealmSchema cannot tell the type of set items :/
//            builder.addValue(prop.name, DataFrame.Empty)
//        } else {
//            val className: String = map.first().type
//            val classSchema: RealmClass = schema[className]!!
//            val setBuilder = createBuilderForType(classSchema)
//            map.forEach { obj: DynamicRealmObject ->
//                addObjectToFrame(schema, classSchema, setBuilder, obj)
//            }
//            val df: AnyFrame = setBuilder.build()
//            builder.addValue(prop.name, df)
//        }
//    }
//    else -> {
//        val list = if (setType.isNullable) {
//            obj.getNullableValueSet(prop.name, setType.storageType.kClass).toList()
//        } else {
//            obj.getValueSet(prop.name, setType.storageType.kClass).toList()
//        }
//        val data = DataColumn.createValueColumn("value", list, setType.storageType.kClass.createType())
//        builder.addValue(prop.name, dataFrameOf(data))
//    }
}
