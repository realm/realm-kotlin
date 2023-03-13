/*
 * Copyright 2022 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.kotlin.internal.dynamic

import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.internal.RealmObjectHelper
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmSet
import kotlin.reflect.KClass

internal class DynamicMutableRealmObjectImpl : DynamicMutableRealmObject, DynamicRealmObjectImpl() {

    override fun <T : Any> getValue(propertyName: String, clazz: KClass<T>): T {
        // dynamicGetSingle checks nullability of property, so null pointer check raises appropriate NPE
        return RealmObjectHelper.dynamicGet(
            this.`io_realm_kotlin_objectReference`!!,
            propertyName,
            clazz,
            nullable = false,
            issueDynamicMutableObject = true
        )!!
    }

    override fun <T : Any> getNullableValue(propertyName: String, clazz: KClass<T>): T? {
        return RealmObjectHelper.dynamicGet(
            `io_realm_kotlin_objectReference`!!,
            propertyName,
            clazz,
            nullable = true,
            issueDynamicMutableObject = true
        )
    }

    override fun getObject(propertyName: String): DynamicMutableRealmObject? {
        return getNullableValue(propertyName, DynamicMutableRealmObject::class)
    }

    override fun <T : Any> getValueList(propertyName: String, clazz: KClass<T>): RealmList<T> {
        return RealmObjectHelper.dynamicGetList(
            `io_realm_kotlin_objectReference`!!,
            propertyName,
            clazz,
            nullable = false,
            issueDynamicMutableObject = true
        ).let { it as RealmList<T> }
    }

    override fun <T : Any> getNullableValueList(propertyName: String, clazz: KClass<T>): RealmList<T?> {
        return RealmObjectHelper.dynamicGetList(
            `io_realm_kotlin_objectReference`!!,
            propertyName,
            clazz,
            nullable = true,
            issueDynamicMutableObject = true
        )
    }

    override fun getObjectList(propertyName: String): RealmList<DynamicMutableRealmObject> {
        return getValueList(propertyName, DynamicMutableRealmObject::class)
    }

    override fun <T : Any> getValueSet(propertyName: String, clazz: KClass<T>): RealmSet<T> {
        return RealmObjectHelper.dynamicGetSet(
            `io_realm_kotlin_objectReference`!!,
            propertyName,
            clazz,
            nullable = false,
            issueDynamicMutableObject = true
        ).let { it as RealmSet<T> }
    }

    override fun <T : Any> getNullableValueSet(propertyName: String, clazz: KClass<T>): RealmSet<T?> {
        return RealmObjectHelper.dynamicGetSet(
            `io_realm_kotlin_objectReference`!!,
            propertyName,
            clazz,
            nullable = true,
            issueDynamicMutableObject = true
        )
    }

    override fun getObjectSet(propertyName: String): RealmSet<DynamicMutableRealmObject> {
        return getValueSet(propertyName, DynamicMutableRealmObject::class)
    }

    override fun <T : Any> getValueDictionary(
        propertyName: String,
        clazz: KClass<T>
    ): RealmDictionary<T> {
        return RealmObjectHelper.dynamicGetDictionary(
            `io_realm_kotlin_objectReference`!!,
            propertyName,
            clazz,
            nullable = false,
            issueDynamicMutableObject = true
        ).let { it as RealmDictionary<T> }
    }

    override fun <T : Any> getNullableValueDictionary(
        propertyName: String,
        clazz: KClass<T>
    ): RealmDictionary<T?> {
        return RealmObjectHelper.dynamicGetDictionary(
            `io_realm_kotlin_objectReference`!!,
            propertyName,
            clazz,
            nullable = true,
            issueDynamicMutableObject = true
        )
    }

    override fun getObjectDictionary(propertyName: String): RealmDictionary<DynamicMutableRealmObject?> {
        return getNullableValueDictionary(propertyName, DynamicMutableRealmObject::class)
    }

    override fun <T> set(propertyName: String, value: T): DynamicMutableRealmObject {
        // `io_realm_kotlin_objectReference` is not null, as DynamicMutableRealmObject are always managed
        val reference = this.io_realm_kotlin_objectReference!!
        RealmObjectHelper.dynamicSetValue(reference, propertyName, value)
        return this
    }
}
