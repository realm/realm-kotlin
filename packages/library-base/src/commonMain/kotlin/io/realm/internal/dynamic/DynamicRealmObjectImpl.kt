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

package io.realm.internal.dynamic

import io.realm.RealmList
import io.realm.dynamic.DynamicRealmObject
import io.realm.internal.Mediator
import io.realm.internal.RealmObjectHelper
import io.realm.internal.RealmObjectInternal
import io.realm.internal.RealmReference
import io.realm.internal.interop.NativePointer
import io.realm.internal.schema.ClassMetadata
import kotlin.reflect.KClass

public open class DynamicRealmObjectImpl : DynamicRealmObject, RealmObjectInternal {
    override val type: String
        get() = this.`$realm$ClassName` ?: throw IllegalArgumentException("Cannot get class name of unmanaged dynamic object")
    override var `$realm$ObjectPointer`: NativePointer? = null
    override var `$realm$IsManaged`: Boolean = false
    override var `$realm$Owner`: RealmReference? = null
    override var `$realm$ClassName`: String? = null
    override var `$realm$Mediator`: Mediator? = null
    override var `$realm$metadata`: ClassMetadata? = null

    override fun <T : Any> getValue(propertyName: String, clazz: KClass<T>): T {
        // dynamicGetSingle checks nullability of property, so null pointer check raises appropriate NPE
        return RealmObjectHelper.dynamicGet(this, propertyName, clazz, false)!!
    }

    override fun <T : Any> getNullableValue(propertyName: String, clazz: KClass<T>): T? {
        return RealmObjectHelper.dynamicGet(this, propertyName, clazz, true)
    }

    override fun getObject(propertyName: String): DynamicRealmObject? {
        return getNullableValue(propertyName, DynamicRealmObject::class)
    }

    override fun <T : Any> getValueList(propertyName: String, clazz: KClass<T>): RealmList<T> {
        return RealmObjectHelper.dynamicGetList(this, propertyName, clazz, false) as RealmList<T>
    }

    override fun <T : Any> getNullableValueList(propertyName: String, clazz: KClass<T>): RealmList<T?> {
        return RealmObjectHelper.dynamicGetList(this, propertyName, clazz, true)
    }

    override fun getObjectList(propertyName: String): RealmList<out DynamicRealmObject> {
        return getValueList(propertyName, DynamicRealmObject::class)
    }
}
