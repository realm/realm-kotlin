package io.realm.internal.dynamic

import io.realm.BaseRealmObject
import io.realm.RealmList
import io.realm.dynamic.DynamicMutableRealmObject
import io.realm.internal.RealmObjectInternal
import io.realm.internal.RealmObjectReference
import io.realm.realmListOf
import kotlin.reflect.KClass

internal class DynamicUnmanagedRealmObject(override val type: String,
    // FIXME Should we expose this as mutable? And then drop all the other getters/setters?
    properties: Map<String, Any?>
) : DynamicMutableRealmObject, RealmObjectInternal {

    public constructor(type: String, vararg properties: Pair<String, Any?> ) : this(type, mapOf(*properties))

    public val properties: MutableMap<String, Any?> = properties.toMutableMap()

    override fun <T : Any> getValue(propertyName: String, clazz: KClass<T>): T = properties[propertyName] as T
    override fun <T : Any> getNullableValue(propertyName: String, clazz: KClass<T>): T? = properties[propertyName] as T?
    override fun getObject(propertyName: String): DynamicMutableRealmObject? = properties[propertyName] as DynamicMutableRealmObject?
    override fun <T : Any> getValueList(propertyName: String, clazz: KClass<T>): RealmList<T> = properties.getOrPut(propertyName, { realmListOf<T>() }) as RealmList<T>
    override fun <T : Any> getNullableValueList(propertyName: String, clazz: KClass<T>): RealmList<T?> = properties.getOrPut(propertyName, { realmListOf<T?>() }) as RealmList<T?>
    override fun getObjectList(propertyName: String): RealmList<DynamicMutableRealmObject> = properties.getOrPut(propertyName, { realmListOf<DynamicMutableRealmObject>() }) as RealmList<DynamicMutableRealmObject>
    override fun <T> set(propertyName: String, value: T): DynamicMutableRealmObject {
        properties.put(propertyName, value as Any)
        return this
    }

    override var io_realm_kotlin_objectReference: RealmObjectReference<out BaseRealmObject>? = null
}
