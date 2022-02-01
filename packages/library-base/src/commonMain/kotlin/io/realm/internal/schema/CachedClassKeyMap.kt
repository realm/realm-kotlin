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
interface SchemaMetadata {
    operator fun get(className: String): ClassMetadata?
    fun getOrThrow(className: String): ClassMetadata = get(className)
        ?: throw IllegalArgumentException("Object of type '$className not found")
}

/**
 * Class metadata providing access class and property keys.
 */
interface ClassMetadata {
    val className: String
    operator fun get(propertyName: String): PropertyKey?
    fun getOrThrow(propertyName: String): PropertyKey = get(propertyName)
        ?: throw IllegalArgumentException("Object of type '$className doesn't have a property named '$propertyName'")
}

/**
 * Schema metadata implementation that postpones class key lookup until first access.
 *
 * The provided class metadata entries are `CachedClassMetadata` for which property keys are also
 * only looked up on first access.
 */
class CachedSchemaMetadata(private val dbPointer: NativePointer) : SchemaMetadata {
    val classMap: Map<String, CachedClassMetadata> by lazy {
        RealmInterop.realm_get_class_keys(dbPointer).map<ClassKey, Pair<String, CachedClassMetadata>> {
            val classInfo = RealmInterop.realm_get_class(dbPointer, it)
            println("Looking up class info for $this ${classInfo.name}")
            classInfo.name to CachedClassMetadata(dbPointer, classInfo.name, classInfo.key)
        }.toMap()
    }

    override fun get(className: String): CachedClassMetadata? = classMap[className]
}

/**
 * Class metadata implementation that provides a lazy loaded cache to property keys.
 */
class CachedClassMetadata(dbPointer: NativePointer, override val className: String, val classKey: ClassKey) : ClassMetadata {
    val propertyMap: Map<String, PropertyKey> by lazy {
        val classInfo = RealmInterop.realm_get_class(dbPointer, classKey)
        println("Looking up property info for ${classInfo.name}")
        RealmInterop.realm_get_class_properties(dbPointer, classInfo.key, classInfo.numProperties).map<PropertyInfo, Pair<String, PropertyKey>> { it.name to it.key }.toMap()
    }

    override fun get(propertyName: String): PropertyKey? = propertyMap[propertyName]
}
