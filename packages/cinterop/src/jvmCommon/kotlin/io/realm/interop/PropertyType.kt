package io.realm.interop

// FIXME API-INTERNAL Compiler does not pick up the actual if not in a separate file, so not 
//  following RealmEnums.kt structure, but might have to move anyway, so keeping the structure 
//  unaligned for now.
actual enum class PropertyType(override val nativeValue: Int) : NativeEnumerated {
    RLM_PROPERTY_TYPE_BOOL(realm_property_type_e.RLM_PROPERTY_TYPE_BOOL),
    RLM_PROPERTY_TYPE_INT(realm_property_type_e.RLM_PROPERTY_TYPE_INT),
    RLM_PROPERTY_TYPE_STRING(realm_property_type_e.RLM_PROPERTY_TYPE_STRING),
    RLM_PROPERTY_TYPE_OBJECT(realm_property_type_e.RLM_PROPERTY_TYPE_OBJECT),
    ;

    // TODO OPTIMIZE
    companion object {
        fun of(i: Int): PropertyType {
            return values().find { it.nativeValue == i } ?: error("Unknown type: $i")
        }
    }
}
