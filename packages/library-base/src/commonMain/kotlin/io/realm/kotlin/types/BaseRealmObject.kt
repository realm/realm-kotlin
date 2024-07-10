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

package io.realm.kotlin.types

import io.realm.kotlin.Deleteable
import io.realm.kotlin.internal.RealmObjectHelper
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.platform.realmObjectCompanionOrThrow
import io.realm.kotlin.internal.runIfManaged
import io.realm.kotlin.internal.runIfManagedOrThrow
import io.realm.kotlin.schema.RealmClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

private const val s = "Cannot access or add additional properties to unmanaged object"

/**
 * Base interface for all realm classes.
 */
public interface BaseRealmObject : Deleteable {
    // Dynamic API?
//    public fun hasProperty(name: String): Boolean

    // Need to be isolated from data model properties
    public val dataModelProperties: Set<String>
        get() = realmObjectCompanionOrThrow(this::class).io_realm_kotlin_fields.keys
    public val extraProperties: Set<String>
        get() {
            return this.runIfManaged {
                RealmInterop.realm_get_additional_properties(this.objectPointer).toSet()
            } ?: throw IllegalStateException(s)
        }

    public val allProperties: Set<String>
        get() = dataModelProperties + extraProperties
}

@PublishedApi
internal fun <T> BaseRealmObject.get(propertyName: String, type: KType): T {
    return this.runIfManagedOrThrow {
        RealmObjectHelper.dynamicGetFromKType(
            obj = this,
            propertyName = propertyName,
            type = type
        )
    }
}
public inline operator fun <reified T> BaseRealmObject.get(propertyName: String): T {
    return get(propertyName, typeOf<T>())
}
public fun BaseRealmObject.hasProperty(name: String): Boolean {
    return this.runIfManagedOrThrow {
        RealmInterop.realm_has_property(this.objectPointer, name)
    }
}

public val BaseRealmObject.schema: RealmClass
    get() = TODO()
