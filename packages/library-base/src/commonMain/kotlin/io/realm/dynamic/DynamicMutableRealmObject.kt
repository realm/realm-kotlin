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

package io.realm.dynamic

import io.realm.MutableRealm
import io.realm.RealmList
import io.realm.internal.dynamic.DynamicUnmanagedRealmObject
import io.realm.schema.RealmStorageType

/**
 * A **dynamic mutable realm object** gives access and possibility to update the data of the realm
 * objects through a generic string based API instead of the conventional [Realm] API that only
 * allows access and updates through the properties of the corresponding schema classes supplied in the configuration.
 */
public interface DynamicMutableRealmObject : DynamicRealmObject {

    override fun getObject(propertyName: String): DynamicMutableRealmObject?

    override fun getObjectList(propertyName: String): RealmList<DynamicMutableRealmObject>

    /**
     * Sets the value for the given field.
     *
     * If value is an unmanaged [RealmObject] it will copied into the realm, just as for normal
     * assignments through the object setters of the typed API and [MutableRealm.copyToRealm].
     *
     * @param propertyName the name of the property to update.
     * @param value the new value of the property.
     * @param T the type of the value.
     * @return this object.
     * @throws IllegalArgummentException if the class doesn't contain a field with the specific
     * name, or if the value doesn't match the [RealmStorageType.kClass] type of the property.
     */
    public fun <T> set(propertyName: String, value: T): DynamicMutableRealmObject
    public fun set(vararg pairs: Pair<String, Any?>): DynamicMutableRealmObject {
        pairs.forEach { set(it.first, it.second) }
        // FIXME Should we abort the builder pattern now that we can add multiple properties in one go?
        return this
    }

    public companion object {
        /**
         * Create an unmanaged dynamic object.
         *
         * The type and properties are only checked when the object is imported through [DynamicMutableRealm.copyToRealm].
         *
         * @param type the class name of the object.
         * @param properties properties of the object.
         *
         * @see DynamicMutableRealm.copyToRealm
         */
        public fun create(type: String, vararg properties: Pair<String, Any?>): DynamicMutableRealmObject {
            return DynamicUnmanagedRealmObject(type, *properties)
        }

        /**
         * Create an unmanaged dynamic object.
         *
         * The type and properties are only checked when the object is imported through [DynamicMutableRealm.copyToRealm].
         *
         * @param type the class name of the object.
         * @param properties properties of the object.
         *
         * @see DynamicMutableRealm.copyToRealm
         */
        public fun create(type: String, properties: Map<String, Any?> = emptyMap()): DynamicMutableRealmObject {
            return DynamicUnmanagedRealmObject(type, properties)
        }
    }
}
