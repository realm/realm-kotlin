package io.realm.interop

// FIXME API-SCHEMA Should probably be somewhere else...maybe in runtime-api?
expect enum class SchemaMode {
    RLM_SCHEMA_MODE_AUTOMATIC,
    RLM_SCHEMA_MODE_IMMUTABLE,
    RLM_SCHEMA_MODE_READ_ONLY_ALTERNATIVE,
    RLM_SCHEMA_MODE_RESET_FILE,
    RLM_SCHEMA_MODE_ADDITIVE,
    RLM_SCHEMA_MODE_MANUAL,
}

expect enum class ClassFlag {
    RLM_CLASS_NORMAL,
    RLM_CLASS_EMBEDDED,
}

expect enum class PropertyType {
    RLM_PROPERTY_TYPE_INT,
    RLM_PROPERTY_TYPE_BOOL,
    RLM_PROPERTY_TYPE_STRING,
    RLM_PROPERTY_TYPE_OBJECT,
    ;

    // Consider adding property methods to make it easier to do generic code on all types. Or is this exactly what collection type is about
    // fun isList()
    // fun isReference()
}

expect enum class CollectionType {
    RLM_COLLECTION_TYPE_NONE,
    RLM_COLLECTION_TYPE_LIST,
    RLM_COLLECTION_TYPE_SET,
    RLM_COLLECTION_TYPE_DICTIONARY,
}

expect enum class PropertyFlag {
    RLM_PROPERTY_NORMAL,
    RLM_PROPERTY_NULLABLE,
    RLM_PROPERTY_PRIMARY_KEY,
    RLM_PROPERTY_INDEXED,
}
