@file:Suppress("UNCHECKED_CAST")

package io.realm.kotlin.internal.dynamic

import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.internal.RealmObjectInternal
import io.realm.kotlin.internal.RealmObjectReference
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmSet
import kotlin.reflect.KClass

internal class DynamicUnmanagedRealmObject(
    override val type: String,
    properties: Map<String, Any?>
) : DynamicMutableRealmObject, RealmObjectInternal {

    @Suppress("SpreadOperator")
    constructor(type: String, vararg properties: Pair<String, Any?>) : this(
        type,
        mapOf(*properties)
    )

    val properties: MutableMap<String, Any?> = properties.toMutableMap()

    override fun <T : Any> getValue(propertyName: String, clazz: KClass<T>): T =
        properties[propertyName] as T

    override fun <T : Any> getNullableValue(propertyName: String, clazz: KClass<T>): T? =
        properties[propertyName] as T?

    override fun getObject(propertyName: String): DynamicMutableRealmObject? =
        properties[propertyName] as DynamicMutableRealmObject?

    override fun <T : Any> getValueList(propertyName: String, clazz: KClass<T>): RealmList<T> =
        properties.getOrPut(propertyName) { realmListOf<T>() } as RealmList<T>

    override fun <T : Any> getNullableValueList(
        propertyName: String,
        clazz: KClass<T>
    ): RealmList<T?> = properties.getOrPut(propertyName) { realmListOf<T?>() } as RealmList<T?>

    override fun getObjectList(propertyName: String): RealmList<DynamicMutableRealmObject> =
        properties.getOrPut(propertyName) { realmListOf<DynamicMutableRealmObject>() }
            as RealmList<DynamicMutableRealmObject>

    override fun <T : Any> getValueSet(propertyName: String, clazz: KClass<T>): RealmSet<T> =
        properties.getOrPut(propertyName) { realmSetOf<T>() } as RealmSet<T>

    override fun <T : Any> getNullableValueSet(
        propertyName: String,
        clazz: KClass<T>
    ): RealmSet<T?> = properties.getOrPut(propertyName) { realmSetOf<T?>() } as RealmSet<T?>

    override fun <T : Any> getValueDictionary(
        propertyName: String,
        clazz: KClass<T>
    ): RealmDictionary<T> =
        properties.getOrPut(propertyName) { realmDictionaryOf<T?>() } as RealmDictionary<T>

    override fun <T : Any> getNullableValueDictionary(
        propertyName: String,
        clazz: KClass<T>
    ): RealmDictionary<T?> =
        properties.getOrPut(propertyName) { realmDictionaryOf<T?>() } as RealmDictionary<T?>

    override fun getBacklinks(propertyName: String): RealmResults<out DynamicRealmObject> =
        throw IllegalStateException("Unmanaged dynamic realm objects do not support backlinks.")

    override fun getObjectSet(propertyName: String): RealmSet<DynamicMutableRealmObject> =
        properties.getOrPut(propertyName) { realmSetOf<DynamicMutableRealmObject>() }
            as RealmSet<DynamicMutableRealmObject>

    override fun getObjectDictionary(
        propertyName: String
    ): RealmDictionary<DynamicMutableRealmObject?> =
        properties.getOrPut(propertyName) { realmDictionaryOf<DynamicMutableRealmObject>() }
            as RealmDictionary<DynamicMutableRealmObject?>

    override fun <T> set(propertyName: String, value: T): DynamicMutableRealmObject {
        properties[propertyName] = value as Any
        return this
    }

    override var io_realm_kotlin_objectReference: RealmObjectReference<out BaseRealmObject>? = null
}
