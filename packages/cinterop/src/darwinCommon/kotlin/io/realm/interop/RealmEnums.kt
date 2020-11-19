package io.realm.interop

import realm_wrapper.realm_schema_mode_e

// Interface to hold C API enumerated constant reference by cinterop constant
interface NativeEnumerated {
    val nativeValue: UInt
}

// FIXME On JVM actuals cannot be combined in same file. Consider replicating that split here too,
//  but await final placement.

actual enum class SchemaMode(override val nativeValue: UInt) : NativeEnumerated {
    RLM_SCHEMA_MODE_AUTOMATIC(realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC.value),
    RLM_SCHEMA_MODE_IMMUTABLE(realm_schema_mode_e.RLM_SCHEMA_MODE_IMMUTABLE.value),
    RLM_SCHEMA_MODE_READ_ONLY_ALTERNATIVE(realm_schema_mode_e.RLM_SCHEMA_MODE_READ_ONLY_ALTERNATIVE.value),
    RLM_SCHEMA_MODE_RESET_FILE(realm_schema_mode_e.RLM_SCHEMA_MODE_RESET_FILE.value),
    RLM_SCHEMA_MODE_ADDITIVE(realm_schema_mode_e.RLM_SCHEMA_MODE_ADDITIVE.value),
    RLM_SCHEMA_MODE_MANUAL(realm_schema_mode_e.RLM_SCHEMA_MODE_MANUAL.value),
}

actual enum class ClassFlag(override val nativeValue: UInt) : NativeEnumerated {
    RLM_CLASS_NORMAL(realm_wrapper.RLM_CLASS_NORMAL),
    RLM_CLASS_EMBEDDED(realm_wrapper.RLM_CLASS_EMBEDDED),
}

actual enum class PropertyType(override val nativeValue: UInt) : NativeEnumerated {
    RLM_PROPERTY_TYPE_INT(realm_wrapper.RLM_PROPERTY_TYPE_INT),
    RLM_PROPERTY_TYPE_BOOL(realm_wrapper.RLM_PROPERTY_TYPE_BOOL),
    RLM_PROPERTY_TYPE_STRING(realm_wrapper.RLM_PROPERTY_TYPE_STRING),
    RLM_PROPERTY_TYPE_OBJECT(realm_wrapper.RLM_PROPERTY_TYPE_OBJECT),
}

actual enum class CollectionType(override val nativeValue: UInt) : NativeEnumerated {
    RLM_COLLECTION_TYPE_NONE(realm_wrapper.RLM_COLLECTION_TYPE_NONE),
    RLM_COLLECTION_TYPE_LIST(realm_wrapper.RLM_COLLECTION_TYPE_LIST),
    RLM_COLLECTION_TYPE_SET(realm_wrapper.RLM_COLLECTION_TYPE_SET),
    RLM_COLLECTION_TYPE_DICTIONARY(realm_wrapper.RLM_COLLECTION_TYPE_DICTIONARY),
}

actual enum class PropertyFlag(override val nativeValue: UInt) : NativeEnumerated {
    RLM_PROPERTY_NORMAL(realm_wrapper.RLM_PROPERTY_NORMAL),
    RLM_PROPERTY_NULLABLE(realm_wrapper.RLM_PROPERTY_NULLABLE),
    RLM_PROPERTY_PRIMARY_KEY(realm_wrapper.RLM_PROPERTY_PRIMARY_KEY),
    RLM_PROPERTY_INDEXED(realm_wrapper.RLM_PROPERTY_INDEXED),
}
