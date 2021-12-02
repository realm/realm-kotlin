/*
 * Copyright 2020 Realm Inc.
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

package io.realm.internal.interop

import realm_wrapper.realm_schema_mode
import realm_wrapper.realm_schema_mode_e

// Interfaces to hold C API enum from cinterop
interface NativeEnum<T : Enum<T>> {
    val nativeValue: Enum<T>
}

// Interfaces to hold C API enumerated constant from cinterop
interface NativeEnumerated {
    val nativeValue: UInt
}

// FIXME API-SCHEMA On JVM actuals cannot be combined in same file. Consider replicating that split here too,
//  but await final placement.

actual enum class SchemaMode(override val nativeValue: realm_schema_mode) : NativeEnum<realm_schema_mode> {
    RLM_SCHEMA_MODE_AUTOMATIC(realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC),
    RLM_SCHEMA_MODE_IMMUTABLE(realm_schema_mode_e.RLM_SCHEMA_MODE_IMMUTABLE),
    RLM_SCHEMA_MODE_READ_ONLY_ALTERNATIVE(realm_schema_mode_e.RLM_SCHEMA_MODE_READ_ONLY_ALTERNATIVE),
    RLM_SCHEMA_MODE_RESET_FILE(realm_schema_mode_e.RLM_SCHEMA_MODE_RESET_FILE),
    RLM_SCHEMA_MODE_ADDITIVE_DISCOVERED(realm_schema_mode_e.RLM_SCHEMA_MODE_ADDITIVE_DISCOVERED),
    RLM_SCHEMA_MODE_ADDITIVE_EXPLICIT(realm_schema_mode_e.RLM_SCHEMA_MODE_ADDITIVE_EXPLICIT),
    RLM_SCHEMA_MODE_MANUAL(realm_schema_mode_e.RLM_SCHEMA_MODE_MANUAL),
}

actual object ClassFlags {
    actual val RLM_CLASS_NORMAL = realm_wrapper.RLM_CLASS_NORMAL.toInt()
    actual val RLM_CLASS_EMBEDDED = realm_wrapper.RLM_CLASS_EMBEDDED.toInt()
}

actual enum class PropertyType(override val nativeValue: UInt) : NativeEnumerated {
    RLM_PROPERTY_TYPE_INT(realm_wrapper.RLM_PROPERTY_TYPE_INT),
    RLM_PROPERTY_TYPE_BOOL(realm_wrapper.RLM_PROPERTY_TYPE_BOOL),
    RLM_PROPERTY_TYPE_STRING(realm_wrapper.RLM_PROPERTY_TYPE_STRING),
    RLM_PROPERTY_TYPE_OBJECT(realm_wrapper.RLM_PROPERTY_TYPE_OBJECT),
    RLM_PROPERTY_TYPE_FLOAT(realm_wrapper.RLM_PROPERTY_TYPE_FLOAT),
    RLM_PROPERTY_TYPE_DOUBLE(realm_wrapper.RLM_PROPERTY_TYPE_DOUBLE);

    actual companion object {
        actual fun from(nativeValue: Int): PropertyType {
            return values().find { it.nativeValue == nativeValue.toUInt() } ?: error("Unknown type: $nativeValue")
        }
    }
}

actual enum class CollectionType(override val nativeValue: UInt) : NativeEnumerated {
    RLM_COLLECTION_TYPE_NONE(realm_wrapper.RLM_COLLECTION_TYPE_NONE),
    RLM_COLLECTION_TYPE_LIST(realm_wrapper.RLM_COLLECTION_TYPE_LIST),
    RLM_COLLECTION_TYPE_SET(realm_wrapper.RLM_COLLECTION_TYPE_SET),
    RLM_COLLECTION_TYPE_DICTIONARY(realm_wrapper.RLM_COLLECTION_TYPE_DICTIONARY);
    actual companion object {
        actual fun from(nativeValue: Int): CollectionType {
            return values().find { it.nativeValue == nativeValue.toUInt() } ?: error("Unknown type: $nativeValue")
        }
    }
}

actual object PropertyFlags {
    actual val RLM_PROPERTY_NORMAL: Int = realm_wrapper.RLM_PROPERTY_NORMAL.toInt()
    actual val RLM_PROPERTY_NULLABLE: Int = realm_wrapper.RLM_PROPERTY_NULLABLE.toInt()
    actual val RLM_PROPERTY_PRIMARY_KEY: Int = realm_wrapper.RLM_PROPERTY_PRIMARY_KEY.toInt()
    actual val RLM_PROPERTY_INDEXED: Int = realm_wrapper.RLM_PROPERTY_INDEXED.toInt()
}

actual enum class SchemaValidationMode(override val nativeValue: UInt) : NativeEnumerated {
    RLM_SCHEMA_VALIDATION_BASIC(realm_wrapper.RLM_SCHEMA_VALIDATION_BASIC),
    RLM_SCHEMA_VALIDATION_SYNC(realm_wrapper.RLM_SCHEMA_VALIDATION_SYNC),
    RLM_SCHEMA_VALIDATION_REJECT_EMBEDDED_ORPHANS(realm_wrapper.RLM_SCHEMA_VALIDATION_REJECT_EMBEDDED_ORPHANS),
}
