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
import kotlin.reflect.KType

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

    override fun <T> get(propertyName: String, type: KType): T =
        when (type.classifier) {
            // FIXME Generic parameter for collection constructors needs to be of the element type
            RealmList::class -> properties.getOrPut(propertyName) { realmListOf<T>() } as T
            RealmSet::class -> properties.getOrPut(propertyName) { realmSetOf<T>() } as T
            RealmDictionary::class -> properties.getOrPut(propertyName) { realmDictionaryOf<T>() } as T
            else -> properties[propertyName] as T
        }

    override fun getBacklinks(propertyName: String): RealmResults<out DynamicRealmObject> =
        throw IllegalStateException("Unmanaged dynamic realm objects do not support backlinks.")

    override fun <T> set(propertyName: String, value: T): DynamicMutableRealmObject {
        properties[propertyName] = value as Any
        return this
    }

    override var io_realm_kotlin_objectReference: RealmObjectReference<out BaseRealmObject>? = null
}
