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

package io.realm.kotlin.dynamic

import io.realm.kotlin.Realm
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmObject
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * A **dynamic realm object** gives access to the data of the realm objects through a generic string
 * based API instead of the conventional [Realm] API that only allows access through the properties
 * of the corresponding schema classes supplied in the configuration.
 */
public interface DynamicRealmObject : BaseRealmObject {
    /**
     * The type of the object.
     *
     * This will normally correspond to the name of a model class that is extending
     * [RealmObject] or [EmbeddedRealmObject].
     */
    public val type: String

    public fun <T> get(propertyName: String, type: KType): T

    /**
     * Returns a backlinks collection referenced by the property name as a [RealmResults].
     *
     * @param propertyName the name of the backlinks property to retrieve for.
     * @return the referencing objects as a [RealmResults].
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non backlinks properties.
     */
    public fun getBacklinks(propertyName: String): RealmResults<out DynamicRealmObject>
}

public inline fun <reified T> DynamicRealmObject.get(propertyName: String): T {
    return get(propertyName, typeOf<T>())
}

public inline fun <reified T> DynamicRealmObject.get(propertyName: String, clazz: KClass<T & Any>): T {
    return get(propertyName, typeOf<T>())
}
