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

// FIXME API-SCHEMA Should probably be somewhere else...maybe in runtime-api?
expect enum class SchemaMode {
    RLM_SCHEMA_MODE_AUTOMATIC,
    RLM_SCHEMA_MODE_IMMUTABLE,
    RLM_SCHEMA_MODE_READ_ONLY,
    RLM_SCHEMA_MODE_SOFT_RESET_FILE,
    RLM_SCHEMA_MODE_HARD_RESET_FILE,
    RLM_SCHEMA_MODE_ADDITIVE_DISCOVERED,
    RLM_SCHEMA_MODE_ADDITIVE_EXPLICIT,
    RLM_SCHEMA_MODE_MANUAL,
}

expect object ClassFlags {
    val RLM_CLASS_NORMAL: Int
    val RLM_CLASS_EMBEDDED: Int
}

expect enum class PropertyType {
    RLM_PROPERTY_TYPE_INT,
    RLM_PROPERTY_TYPE_BOOL,
    RLM_PROPERTY_TYPE_STRING,
    RLM_PROPERTY_TYPE_BINARY,
    RLM_PROPERTY_TYPE_MIXED,
    RLM_PROPERTY_TYPE_TIMESTAMP,
    RLM_PROPERTY_TYPE_FLOAT,
    RLM_PROPERTY_TYPE_DOUBLE,
    RLM_PROPERTY_TYPE_OBJECT_ID,
    RLM_PROPERTY_TYPE_OBJECT,
    RLM_PROPERTY_TYPE_LINKING_OBJECTS,
    RLM_PROPERTY_TYPE_DECIMAL128,
    RLM_PROPERTY_TYPE_UUID;

    // Consider adding property methods to make it easier to do generic code on all types. Or is this exactly what collection type is about
    // fun isList()
    // fun isReference()
    companion object {
        fun from(nativeValue: Int): PropertyType
    }
}

expect enum class CollectionType {
    RLM_COLLECTION_TYPE_NONE,
    RLM_COLLECTION_TYPE_LIST,
    RLM_COLLECTION_TYPE_SET,
    RLM_COLLECTION_TYPE_DICTIONARY;
    companion object {
        fun from(nativeValue: Int): CollectionType
    }
}

expect object PropertyFlags {
    val RLM_PROPERTY_NORMAL: Int
    val RLM_PROPERTY_NULLABLE: Int
    val RLM_PROPERTY_PRIMARY_KEY: Int
    val RLM_PROPERTY_INDEXED: Int
    val RLM_PROPERTY_FULLTEXT_INDEXED: Int
}

expect enum class SchemaValidationMode {
    RLM_SCHEMA_VALIDATION_BASIC,
    RLM_SCHEMA_VALIDATION_SYNC_PBS,
    RLM_SCHEMA_VALIDATION_SYNC_FLX,
    RLM_SCHEMA_VALIDATION_REJECT_EMBEDDED_ORPHANS,
}

expect enum class ValueType {
    RLM_TYPE_NULL,
    RLM_TYPE_INT,
    RLM_TYPE_BOOL,
    RLM_TYPE_STRING,
    RLM_TYPE_BINARY,
    RLM_TYPE_TIMESTAMP,
    RLM_TYPE_FLOAT,
    RLM_TYPE_DOUBLE,
    RLM_TYPE_DECIMAL128,
    RLM_TYPE_OBJECT_ID,
    RLM_TYPE_LINK,
    RLM_TYPE_UUID,
    RLM_TYPE_LIST,
    RLM_TYPE_DICTIONARY,
}
