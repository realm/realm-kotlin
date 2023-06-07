package io.realm.kotlin.internal.schema

import io.realm.kotlin.internal.interop.CollectionType
import io.realm.kotlin.internal.interop.PropertyInfo
import io.realm.kotlin.internal.interop.PropertyType
import io.realm.kotlin.internal.realmObjectCompanionOrNull
import io.realm.kotlin.types.TypedRealmObject
import kotlin.reflect.KClass

/**
 * Helper method the compiler plugin can use to create PropertyInfo instances based on KClass
 * references rather than Strings. This needs to be placed in library-base, since the cinterop
 * module do not know about the public API classes.
 */
@Suppress("unused", "LongParameterList")
internal fun createPropertyInfo(
    name: String,
    publicName: String?,
    type: PropertyType,
    collectionType: CollectionType,
    linkTarget: KClass<TypedRealmObject>?,
    linkOriginPropertyName: String?,
    isNullable: Boolean,
    isPrimaryKey: Boolean,
    isIndexed: Boolean,
    isFullTextIndexed: Boolean
): PropertyInfo {

    // Locate the link target dynamically. We do this to work around incremental
    // compilation not triggering in some cases.
    // E.g. if you have a A -> B relationship, A will embed the name of B in its schema
    // definition, but a recompilation of A will not be triggered if @PersistedName on
    // B is changed. This will cause Realm to throw a schema mismatch error when the Realm
    // file is opened.
    //
    // Note, we do not need to do this for linkOriginPropertyName which is used by backlinks
    // since they are defined by `by backlinks(property)`, which will correctly cause both sides
    // of the relationship to be recompiled.
    val resolvedLinkTarget: String? = linkTarget?.let {
        it.realmObjectCompanionOrNull()?.io_realm_kotlin_className
            ?: throw IllegalStateException("Could not find RealmObjectCompanion for: ${linkTarget.qualifiedName}")
    }
    return PropertyInfo.create(
        name,
        publicName,
        type,
        collectionType,
        resolvedLinkTarget,
        linkOriginPropertyName,
        isNullable,
        isPrimaryKey,
        isIndexed,
        isFullTextIndexed
    )
}
