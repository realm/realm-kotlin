package io.realm.interop

actual enum class CollectionType(override val value: Int) : Enumerated {
    RLM_COLLECTION_TYPE_NONE(realm_collection_type_e.RLM_COLLECTION_TYPE_NONE),
    RLM_COLLECTION_TYPE_LIST(realm_collection_type_e.RLM_COLLECTION_TYPE_LIST),
    RLM_COLLECTION_TYPE_SET(realm_collection_type_e.RLM_COLLECTION_TYPE_SET),
    RLM_COLLECTION_TYPE_DICTIONARY(realm_collection_type_e.RLM_COLLECTION_TYPE_DICTIONARY),
}
