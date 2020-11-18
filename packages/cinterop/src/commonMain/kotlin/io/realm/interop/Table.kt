package io.realm.interop

// FIXME API-SCHEMA Platform independent class definition. Maybe rework into utility method called in Realm
//  object's companion schema mechanism depending on how we relate this to the actual schema/runtime
//  realm_class_info_t.
class Table(
    val name: String,
    val primaryKey: String = "",
    val flags: Set<ClassFlag> = setOf(ClassFlag.RLM_CLASS_NORMAL),
    val properties: List<Property>
)
