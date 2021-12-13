/*
 * Copyright 2021 Realm Inc.
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

package io.realm.schema

/**
 * A [RealmProperty] describes the properties of a class property in the object model.
 */
interface RealmProperty {
    /**
     * Returns the name of the property in the object model.
     */
    val name: String

    /**
     * Returns the type of the property in the object model.
     */
    val type: RealmPropertyType

    /**
     * Returns whether or not the property is allowed to be null in the corresponding `RealmObject`
     *
     * For [ValuePropertyType] this will be the same as [RealmPropertyType.isNullable]. For all
     * other property types it will always be false.
     */
    val isNullable: Boolean
}
