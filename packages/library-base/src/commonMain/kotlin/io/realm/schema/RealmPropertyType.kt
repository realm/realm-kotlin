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

import kotlin.reflect.KClass

/**
 * A [RealmPropertyType] describes the type of a specific property in the object model.
 */
sealed interface RealmPropertyType {
    /**
     * The type that is used when storing the property values in the realm.
     */
    val storageType: RealmStorageType

    /**
     * Indicates whether the storage element can be `null`.
     */
    val isNullable: Boolean

    companion object {
        // TODO Not as good as RealmPropertyType::class.sealedClasses as this has to be manually
        //  adjusted, but since KClass<T>.sealedClasses is only available for JVM this is the next
        //  best thing (at least uncovered until now ... without writing a compiler plugin) that
        //  allows to define the options centrally and use it to verify exhaustiveness in tests.
        //  JUST DON'T FORGET TO UPDATE ON WHEN ADDING NEW SUBCLASSES :see_no_evil:
        //  We could do a JVM test that verifies that it is exhaustive :thinking:
        internal val subTypes: Set<KClass<out RealmPropertyType>> = setOf(ValuePropertyType::class, ListPropertyType::class)
    }
}

/**
 * A [ValuePropertyType] describes singular value properties.
 */
data class ValuePropertyType(
    override val storageType: RealmStorageType,
    override val isNullable: Boolean,
    /**
     * Indicates whether this property is the primary key of the class in the object model.
     */
    val isPrimaryKey: Boolean,
    /**
     * Indicates whether there is an index associated with this property.
     */
    val isIndexed: Boolean
) : RealmPropertyType

data class ListPropertyType(
    override val storageType: RealmStorageType,
    override val isNullable: Boolean = false
) : RealmPropertyType
