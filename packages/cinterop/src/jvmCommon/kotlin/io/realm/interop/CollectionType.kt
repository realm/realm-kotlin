package io.realm.interop

// FIXME API-INTERNAL Compiler does not pick up the actual if not in a separate file, so not
//  following RealmEnums.kt structure, but might have to move anyway, so keeping the structure
//  unaligned for now.
actual enum class CollectionType(override val nativeValue: Int) : NativeEnumerated {
    RLM_COLLECTION_TYPE_NONE(realm_collection_type_e.RLM_COLLECTION_TYPE_NONE),
    RLM_COLLECTION_TYPE_LIST(realm_collection_type_e.RLM_COLLECTION_TYPE_LIST),
    RLM_COLLECTION_TYPE_SET(realm_collection_type_e.RLM_COLLECTION_TYPE_SET),
    RLM_COLLECTION_TYPE_DICTIONARY(realm_collection_type_e.RLM_COLLECTION_TYPE_DICTIONARY),
}
