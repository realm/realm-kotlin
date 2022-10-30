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

import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.internal.RealmObjectHelper
import io.realm.kotlin.internal.RealmObjectInternal
import io.realm.kotlin.internal.RealmObjectReference
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmSet
import kotlin.reflect.KClass

public open class DynamicRealmObjectImpl : DynamicRealmObject, RealmObjectInternal {
    override val type: String
        get() = this.`io_realm_kotlin_objectReference`!!.className

    // This should never be null after initialization of a dynamic object, but we currently cannot
    // represent that in the type system as we one some code paths construct the Kotlin object
    // before having the realm object reference
    override var `io_realm_kotlin_objectReference`: RealmObjectReference<out BaseRealmObject>? = null

    override fun <T : Any> getValue(propertyName: String, clazz: KClass<T>): T {
        // dynamicGetSingle checks nullability of property, so null pointer check raises appropriate NPE
        return RealmObjectHelper.dynamicGet(this.`io_realm_kotlin_objectReference`!!, propertyName, clazz, false)!!
    }

    override fun <T : Any> getNullableValue(propertyName: String, clazz: KClass<T>): T? {
        return RealmObjectHelper.dynamicGet(`io_realm_kotlin_objectReference`!!, propertyName, clazz, true)
    }

    override fun getObject(propertyName: String): DynamicRealmObject? {
        return getNullableValue(propertyName, DynamicRealmObject::class)
    }

    override fun <T : Any> getValueList(propertyName: String, clazz: KClass<T>): RealmList<T> {
        return RealmObjectHelper.dynamicGetList(`io_realm_kotlin_objectReference`!!, propertyName, clazz, false)
            .let { it as RealmList<T> }
    }

    override fun <T : Any> getNullableValueList(propertyName: String, clazz: KClass<T>): RealmList<T?> {
        return RealmObjectHelper.dynamicGetList(`io_realm_kotlin_objectReference`!!, propertyName, clazz, true)
    }

    override fun getObjectList(propertyName: String): RealmList<out DynamicRealmObject> {
        return getValueList(propertyName, DynamicRealmObject::class)
    }

    override fun <T : Any> getValueSet(propertyName: String, clazz: KClass<T>): RealmSet<T> {
        return RealmObjectHelper.dynamicGetSet(`io_realm_kotlin_objectReference`!!, propertyName, clazz, false)
            .let { it as RealmSet<T> }
    }

    override fun <T : Any> getNullableValueSet(propertyName: String, clazz: KClass<T>): RealmSet<T?> {
        return RealmObjectHelper.dynamicGetSet(`io_realm_kotlin_objectReference`!!, propertyName, clazz, true)
    }

    override fun getObjectSet(propertyName: String): RealmSet<out DynamicRealmObject> {
        return getValueSet(propertyName, DynamicRealmObject::class)
    }

    override fun getLinkingObjects(propertyName: String): RealmResults<out DynamicRealmObject> {
        return RealmObjectHelper.dynamicGetLinkingObjects(`io_realm_kotlin_objectReference`!!, propertyName)
    }
}
