package io.realm.interop

actual enum class PropertyFlag(override val nativeValue: Int) : NativeEnumerated {
    RLM_PROPERTY_NORMAL(realm_property_flags_e.RLM_PROPERTY_NORMAL),
    RLM_PROPERTY_NULLABLE(realm_property_flags_e.RLM_PROPERTY_NULLABLE),
    RLM_PROPERTY_PRIMARY_KEY(realm_property_flags_e.RLM_PROPERTY_PRIMARY_KEY),
    RLM_PROPERTY_INDEXED(realm_property_flags_e.RLM_PROPERTY_INDEXED),
}
