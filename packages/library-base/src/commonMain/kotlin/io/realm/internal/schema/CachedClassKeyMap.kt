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

package io.realm.internal.schema

import io.realm.internal.interop.ClassKey
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.PropertyInfo
import io.realm.internal.interop.PropertyKey
import io.realm.internal.interop.RealmInterop

/**
 * Schema metadata providing access to class metadata for the schema.
 */
public interface SchemaMetadata {
    public operator fun get(className: String): ClassMetadata?
    public fun getOrThrow(className: String): ClassMetadata = get(className)
        ?: throw IllegalArgumentException("Schema does not contain a class named '$className'")
}

/**
 * Class metadata providing access class and property keys.
 */
public interface ClassMetadata {
    public val className: String
    public operator fun get(propertyName: String): PropertyKey?
    public fun getOrThrow(propertyName: String): PropertyKey = get(propertyName)
        ?: throw IllegalArgumentException("Schema for type '$className doesn't contain a property named '$propertyName'")
    public operator fun get(propertyKey: PropertyKey): PropertyInfo?
}

/**
 * Schema metadata implementation that postpones class key lookup until first access.
 *
 * The provided class metadata entries are `CachedClassMetadata` for which property keys are also
 * only looked up on first access.
 */
public class CachedSchemaMetadata(private val dbPointer: NativePointer) : SchemaMetadata {
    // TODO OPTIMIZE We should theoretically be able to lazy load these, but it requires locking
    //  and 'by lazy' initializers can throw
    //  kotlin.native.concurrent.InvalidMutabilityException: Frozen during lazy computation
    public val classMap: Map<String, CachedClassMetadata>

    init {
        classMap = RealmInterop.realm_get_class_keys(dbPointer).map<ClassKey, Pair<String, CachedClassMetadata>> {
            val classInfo = RealmInterop.realm_get_class(dbPointer, it)
            classInfo.name to CachedClassMetadata(dbPointer, classInfo.name, classInfo.key)
        }.toMap()
    }

    override fun get(className: String): CachedClassMetadata? = classMap[className]
}

/**
 * Class metadata implementation that provides a lazy loaded cache to property keys.
 */
public class CachedClassMetadata(dbPointer: NativePointer, override val className: String, public val classKey: ClassKey) : ClassMetadata {
    // TODO OPTIMIZE We should theoretically be able to lazy load these, but it requires locking
    //  and 'by lazy' initializers can throw
    //  kotlin.native.concurrent.InvalidMutabilityException: Frozen during lazy computation
    public val propertyNameToKeyMap: Map<String, PropertyKey>
    public val propertyKeyToInfoMap: Map<PropertyKey, PropertyInfo>

    init {
        val classInfo = RealmInterop.realm_get_class(dbPointer, classKey)
        RealmInterop.realm_get_class_properties(dbPointer, classInfo.key, classInfo.numProperties).apply {
            propertyNameToKeyMap = this.map<PropertyInfo, Pair<String, PropertyKey>> { it.name to it.key }.toMap()
            propertyKeyToInfoMap = this.map<PropertyInfo, Pair<PropertyKey, PropertyInfo>> { it.key to it }.toMap()
        }
    }

    override fun get(propertyName: String): PropertyKey? = propertyNameToKeyMap[propertyName]
    override fun get(propertyKey: PropertyKey): PropertyInfo? = propertyKeyToInfoMap[propertyKey]
}
