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

package io.realm.kotlin.internal.interop

import realm_wrapper.realm_schema_mode
import realm_wrapper.realm_schema_mode_e
import realm_wrapper.realm_value_type
import realm_wrapper.realm_value_type_e

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
    RLM_SCHEMA_MODE_READ_ONLY(realm_schema_mode_e.RLM_SCHEMA_MODE_READ_ONLY),
    RLM_SCHEMA_MODE_SOFT_RESET_FILE(realm_schema_mode_e.RLM_SCHEMA_MODE_SOFT_RESET_FILE),
    RLM_SCHEMA_MODE_HARD_RESET_FILE(realm_schema_mode_e.RLM_SCHEMA_MODE_HARD_RESET_FILE),
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
    RLM_PROPERTY_TYPE_BINARY(realm_wrapper.RLM_PROPERTY_TYPE_BINARY),
    RLM_PROPERTY_TYPE_MIXED(realm_wrapper.RLM_PROPERTY_TYPE_MIXED),
    RLM_PROPERTY_TYPE_TIMESTAMP(realm_wrapper.RLM_PROPERTY_TYPE_TIMESTAMP),
    RLM_PROPERTY_TYPE_FLOAT(realm_wrapper.RLM_PROPERTY_TYPE_FLOAT),
    RLM_PROPERTY_TYPE_DOUBLE(realm_wrapper.RLM_PROPERTY_TYPE_DOUBLE),
    RLM_PROPERTY_TYPE_OBJECT(realm_wrapper.RLM_PROPERTY_TYPE_OBJECT),
    RLM_PROPERTY_TYPE_LINKING_OBJECTS(realm_wrapper.RLM_PROPERTY_TYPE_LINKING_OBJECTS),
    RLM_PROPERTY_TYPE_DECIMAL128(realm_wrapper.RLM_PROPERTY_TYPE_DECIMAL128),
    RLM_PROPERTY_TYPE_OBJECT_ID(realm_wrapper.RLM_PROPERTY_TYPE_OBJECT_ID),
    RLM_PROPERTY_TYPE_UUID(realm_wrapper.RLM_PROPERTY_TYPE_UUID)
    ;

    actual companion object {
        actual fun from(nativeValue: Int): PropertyType {
            return values().find { it.nativeValue == nativeValue.toUInt() } ?: error("Unknown property type: $nativeValue")
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
            return values().find { it.nativeValue == nativeValue.toUInt() } ?: error("Unknown collection type: $nativeValue")
        }
    }
}

actual object PropertyFlags {
    actual val RLM_PROPERTY_NORMAL: Int = realm_wrapper.RLM_PROPERTY_NORMAL.toInt()
    actual val RLM_PROPERTY_NULLABLE: Int = realm_wrapper.RLM_PROPERTY_NULLABLE.toInt()
    actual val RLM_PROPERTY_PRIMARY_KEY: Int = realm_wrapper.RLM_PROPERTY_PRIMARY_KEY.toInt()
    actual val RLM_PROPERTY_INDEXED: Int = realm_wrapper.RLM_PROPERTY_INDEXED.toInt()
    actual val RLM_PROPERTY_FULLTEXT_INDEXED: Int = realm_wrapper.RLM_PROPERTY_FULLTEXT_INDEXED.toInt()
}

actual enum class SchemaValidationMode(override val nativeValue: UInt) : NativeEnumerated {
    RLM_SCHEMA_VALIDATION_BASIC(realm_wrapper.RLM_SCHEMA_VALIDATION_BASIC),
    RLM_SCHEMA_VALIDATION_SYNC_FLX(realm_wrapper.RLM_SCHEMA_VALIDATION_SYNC_FLX),
    RLM_SCHEMA_VALIDATION_SYNC_PBS(realm_wrapper.RLM_SCHEMA_VALIDATION_SYNC_PBS),
    RLM_SCHEMA_VALIDATION_REJECT_EMBEDDED_ORPHANS(realm_wrapper.RLM_SCHEMA_VALIDATION_REJECT_EMBEDDED_ORPHANS),
}

actual enum class ValueType(
    override val nativeValue: realm_value_type
) : NativeEnum<realm_value_type_e> {
    RLM_TYPE_NULL(realm_value_type_e.RLM_TYPE_NULL),
    RLM_TYPE_INT(realm_value_type_e.RLM_TYPE_INT),
    RLM_TYPE_BOOL(realm_value_type_e.RLM_TYPE_BOOL),
    RLM_TYPE_STRING(realm_value_type_e.RLM_TYPE_STRING),
    RLM_TYPE_BINARY(realm_value_type_e.RLM_TYPE_BINARY),
    RLM_TYPE_TIMESTAMP(realm_value_type_e.RLM_TYPE_TIMESTAMP),
    RLM_TYPE_FLOAT(realm_value_type_e.RLM_TYPE_FLOAT),
    RLM_TYPE_DOUBLE(realm_value_type_e.RLM_TYPE_DOUBLE),
    RLM_TYPE_DECIMAL128(realm_value_type_e.RLM_TYPE_DECIMAL128),
    RLM_TYPE_OBJECT_ID(realm_value_type_e.RLM_TYPE_OBJECT_ID),
    RLM_TYPE_LINK(realm_value_type_e.RLM_TYPE_LINK),
    RLM_TYPE_UUID(realm_value_type_e.RLM_TYPE_UUID),
    RLM_TYPE_LIST(realm_value_type_e.RLM_TYPE_LIST),
    RLM_TYPE_DICTIONARY(realm_value_type_e.RLM_TYPE_DICTIONARY),
    ;

    companion object {
        fun from(nativeValue: realm_value_type): ValueType = values().find {
            it.nativeValue == nativeValue
        } ?: error("Unknown value type: $nativeValue")
    }
}
