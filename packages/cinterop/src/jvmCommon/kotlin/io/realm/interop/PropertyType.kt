package io.realm.interop

// TODO Should probably be somewhere else...maybe in runtime-api?
actual enum class PropertyType(override val value: Int) : Enumerated {
    RLM_PROPERTY_TYPE_BOOL(realm_property_type_e.RLM_PROPERTY_TYPE_BOOL),
    RLM_PROPERTY_TYPE_INT(realm_property_type_e.RLM_PROPERTY_TYPE_INT),
    RLM_PROPERTY_TYPE_STRING(realm_property_type_e.RLM_PROPERTY_TYPE_STRING),
    RLM_PROPERTY_TYPE_OBJECT(realm_property_type_e.RLM_PROPERTY_TYPE_OBJECT),
}
