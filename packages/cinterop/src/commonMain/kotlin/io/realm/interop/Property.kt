package io.realm.interop

// FIXME API-SCHEMA Platform independent property definition. Maybe rework into utility method
//  called in Realm object's companion schema mechanism depending on how we relate this to the
//  actual schema/runtime realm_property_info_t.
@Suppress("LongParameterList")
class Property(
    val name: String,
    val publicName: String = "",
    val type: PropertyType,
    val collectionType: CollectionType = CollectionType.RLM_COLLECTION_TYPE_NONE,
    val linkTarget: String = "",
    val linkOriginPropertyName: String = "",
    val flags: Set<PropertyFlag> = setOf(PropertyFlag.RLM_PROPERTY_NORMAL)
)
