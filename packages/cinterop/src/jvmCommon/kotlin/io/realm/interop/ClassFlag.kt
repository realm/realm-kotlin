package io.realm.interop

// FIXME API-INTERNAL Compiler does not pick up the actual if not in a separate file, so not
//  following RealmEnums.kt structure, but might have to move anyway, so keeping the structure
//  unaligned for now.
actual enum class ClassFlag(override val nativeValue: Int) : NativeEnumerated {
    RLM_CLASS_NORMAL(realm_class_flags_e.RLM_CLASS_NORMAL),
    RLM_CLASS_EMBEDDED(realm_class_flags_e.RLM_CLASS_EMBEDDED),
}
