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

package io.realm.internal

import io.realm.DynamicRealmObject
import io.realm.RealmList
import io.realm.internal.interop.NativePointer
import io.realm.internal.schema.ClassMetadata
import kotlin.reflect.KClass

open class DynamicRealmObjectImpl : DynamicRealmObject, RealmObjectInternal {
    override val type: String
        get() =  this.`$realm$ClassName` ?: throw IllegalArgumentException("Cannot get class name of unmanaged dynamic object")
    override var `$realm$ObjectPointer`: NativePointer? = null
    override var `$realm$IsManaged`: Boolean = false
    override var `$realm$Owner`: RealmReference?  = null
    override var `$realm$ClassName`: String? = null
    override var `$realm$Mediator`: Mediator? = null
    override var `$realm$metadata`: ClassMetadata? = null

    override fun <T : Any> get(fieldName: String, clazz: KClass<T>): T {
        return RealmObjectHelper.dynamicGet(this, clazz, fieldName)!! // Is it reasonable to just throw language null pointer error?
    }

    override fun <T : Any> getNullable(fieldName: String, clazz: KClass<T>): T? {
        return RealmObjectHelper.dynamicGet(this, clazz, fieldName)
    }

    override fun <T : Any> getList(fieldName: String, clazz: KClass<T>): RealmList<T> {
        return RealmObjectHelper.getList(this, fieldName, clazz) as RealmList<T>
    }

    override fun <T : Any> getListOfNullable(fieldName: String, clazz: KClass<T>): RealmList<T?> {
        return RealmObjectHelper.getList(this, fieldName, clazz) as RealmList<T?>
    }

}
