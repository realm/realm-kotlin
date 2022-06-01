package io.realm.kotlin.types

/**
 * Marker interface to define an embedded model.
 *
 * Embedded objects have a slightly different behavior than normal objects:
 * - They must have exactly 1 parent linking to them when the embedded object is added to
 *   the Realm. Embedded objects can be the parent of other embedded objects. The parent
 *   cannot be changed later, except by copying the object.
 * - They cannot have fields annotated with `@PrimaryKey`.
 * - When a parent object is deleted, all embedded objects are also deleted.
 */
public interface EmbeddedRealmObject : BaseRealmObject
