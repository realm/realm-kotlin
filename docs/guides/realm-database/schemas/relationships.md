# Relationships - Kotlin SDK
This page describes how to define relationships between objects in your data
model. To learn about Realm objects and how to define them, refer to
Define a Realm Object Model - Kotlin SDK.

Unlike a relational database, Realm doesn't use bridge tables or explicit
joins to define relationships. Instead, Realm handles relationships through
embedded objects or reference properties to other Realm objects. You read from
and write to these properties directly. This makes querying relationships as
performant as querying against any other property.

## Relationship Types
There are two primary types of relationships between Realm objects:

- To-One Relationship
- To-Many Relationship

Realm *does not* inherently limit object references from other
objects within the same realm. This means that Realm relationships are
implicitly "many-to-one" or "many-to-many." The only way to restrict a
relationship to "one to one/one to many" instead of "many to one/many
to many" is to use an embedded object,
which is a nested object with exactly one parent.

## Define a Relationship
You can define relationships in your object schema by defining an object
property that references another Realm object. Learn more about using
Realm objects as properties.

You can define relationships using the following types:

- `RealmObject`
- `RealmList <? extends RealmObject>`
- `RealmSet <? extends RealmObject>`

You can also embed one Realm object directly within another to create
a nested data structure with an `EmbeddedRealmObject` type. However,
embedded objects have additional constraints. Refer to the
Define an Embedded Object section for more information.

### Define a To-One Relationship Property
A **to-one relationship** relationship maps one property to a single instance
of a `RealmObject` object type. Nothing prevents multiple parent
object instances from referencing the same child instance. If you want to
enforce a strict one-to-one relationship, use an embedded object instead.

> **NOTE:**
> Setting a to-one relationship field to `null` removes the connection
between the objects, but Realm does not delete the referenced object.
If you want to delete the referenced object when the parent object is
deleted, use an embedded object instead.
>

To define a to-one relationship between objects,
define an optional object property whose type is a `RealmObject` object
defined in your data model. This can be a different Realm object type
or the same Realm object type:

```kotlin
// Relationships of Realm objects must be of RealmObject type
class Frog : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var name: String = ""
    var age: Int = 0
    // Property of RealmObject type (MUST be null)
    var favoritePond: Pond? = null
    var bestFriend: Frog? = null
}

```

### Define a To-Many Relationship Property
A **to-many relationship** maps one property to zero or more
instances of a `RealmObject` object type. Nothing prevents multiple
parent object instances from referencing the same child instance. If you
want to enforce a strict one-to-many relationship, use an embedded object
instead.

In Realm, to-many relationships are collections (a RealmList or RealmSet) of
Realm objects. For more information on defining collections in your object
model, refer to Define Collection Properties.

To define a to-many relationship between objects, define an object property
whose type is a `RealmList<E>` or `RealmSet<E>`, where `<E>` is
a `RealmObject` object type defined in your data model. This can be
a different Realm object type or the same Realm object type:

```kotlin
// Relationships of RealmList<E> or RealmSet<E> must be of RealmObject type
class Forest : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var name: String = ""
    // Set of RealmObject type (CANNOT be null)
    var frogsThatLiveHere: RealmSet<Frog> = realmSetOf()
    // List of RealmObject type (CANNOT be null)
    var nearbyPonds: RealmList<Pond> = realmListOf()
}

```

### Define an Inverse Relationship
An **inverse relationship** is an automatic **backlink**
relationship between a child `RealmObject` object and any other parent
objects that refer to it. Inverse relationships can link back to
child objects in a many-to-one or many-to-many relationship. If you want to
enforce a strict one-to-one or one-to-many inverse relationship, use an
embedded object instead.

Relationship definitions are unidirectional, so you must explicitly
define a property in the object's model as an inverse relationship.
For example, the to-many relationship "User has many Posts" does not
automatically create the inverse relationship "Post belongs to User".
But you can add a User property on the Post that points back to the
post's owner. This lets you query on the inverse
relationship from post to user instead of running a separate query
to look up the user the post belongs to.
Because relationships are many-to-one or many-to-many, following
inverse relationships can result in zero, one, or many objects.

> **NOTE:**
> Realm automatically updates implicit relationships whenever an
object is added or removed from the specified relationship. You
cannot manually add or remove items from a backlinks collection.
>

To define an inverse relationship between objects, first define a
collection property in the parent object whose type is a
`RealmList<E>`, `RealmSet<E>`, or `RealmDictionary<E>`, where
`<E>` is a `RealmObject` object type defined in your data model.
This can be a different Realm object type or the same Realm object
type:

```kotlin
// Parent object must have RealmList<E>, RealmSet<E>, or
// RealmDictionary<K,V> property of child type
class User : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var name: String = ""
    // List of child RealmObject type (CANNOT be nullable)
    var posts: RealmList<Post> = realmListOf()
    // Set of child RealmObject type (CANNOT be nullable)
    var favoritePosts: RealmSet<Post> = realmSetOf()
    // Dictionary of child RealmObject type (value MUST be nullable)
    var postByYear: RealmDictionary<Post?> = realmDictionaryOf()
}

```

Then, define an immutable `backlinks`
property in the child object of `RealmResults<E>`, where `<E>`
is the parent object type:

```kotlin
// Backlink of RealmObject must be RealmResults<E> of parent object type
class Post : RealmObject {
    var title: String = ""
    var date: RealmInstant = RealmInstant.now()
    // Backlink to parent RealmObject type (CANNOT be null & MUST be val)
    val user: RealmResults<User> by backlinks(User::posts)
}

```

### Define an Embedded Object
An embedded object is a special type
of Realm object that you can use to nest data inside of a single,
specific parent object. Embedded objects can be in a one-to-one,
one-to-many, or inverse relationship similar to the relationships
described in the previous sections, except the relationship is
restricted to a one-to relationship between a single parent object and
an `EmbeddedRealmObject` type.

Because embedded objects *cannot* exist as independent Realm objects,
they have additional constraints to consider:

- An embedded object has strict ownership by its parent.
You cannot share embedded objects between parent objects.
- The embedded object inherits the lifecycle of its parent.
For example, deleting the parent object also deletes the embedded object.

Embedded objects are useful when there is a clear
containment or ownership relationship. For example, an
`Address` object might be embedded in a `User` object because it
is only meaningful in your application within the context of a user.

> **TIP:**
> You can use the same embedded object type in multiple parent object
types and inside other embedded object types.
>

#### One-to-One Relationship
To define an embedded object with a one-to-one relationship
with its parent, define an
object property whose type is an `EmbeddedRealmObject` already
defined in your data model:

```kotlin
// To-one embedded relationships must be of EmbeddedRealmObject type
class Contact : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var name: String = ""
    // Property of EmbeddedRealmObject type (MUST be null)
    var address: EmbeddedAddress? = null
}

class EmbeddedAddress : EmbeddedRealmObject {
    var propertyOwner: Contact? = null
    var street: String? = ""
    // Embed another EmbeddedRealmObject type
    var country: EmbeddedCountry? = null
}

class EmbeddedCountry : EmbeddedRealmObject {
    var name: String = ""
}

```

#### One-to-Many Relationship
To define an embedded object with a one-to-many embedded
relationship with its parent, define an
object property whose type is a `RealmList<E>`, where `<E>` is
an `EmbeddedRealmObject` object type defined in your data model:

```kotlin
// To-many embedded relationships must be a RealmList<E> or
// RealmDictionary<K, V> property of EmbeddedRealmObject type
class Business : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var name: String = ""
    // List of EmbeddedRealmObject type (CANNOT be null)
    var addresses: RealmList<EmbeddedAddress> = realmListOf()
    // Dictionary of EmbeddedRealmObject type (value MUST be nullable)
    var addressByYear: RealmDictionary<EmbeddedAddress?> = realmDictionaryOf()
}

```

You *cannot* define an embedded object with a `RealmSet<E>` type,
as `RealmSet<E>` does not support embedded objects.

#### Inverse Relationship
To define an embedded object with an inverse relationship with
its parent, first define a collection property in the parent
object whose type is a `RealmList<E>` or `RealmDictionary<E>`,
where `<E>` is an `EmbeddedRealmObject` object type defined
in your data model:

```kotlin
// Parent object must have RealmList<E> or RealmDictionary<K, V>
// property of child EmbeddedRealmObject type
class User : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var name: String = ""
    // List of child EmbeddedRealmObject type (CANNOT be nullable)
    var posts: RealmList<Post> = realmListOf()
    // Dictionary of child EmbeddedRealmObject type (value MUST be nullable)
    var postByYear: RealmDictionary<Post?> = realmDictionaryOf()
}

```

You *cannot* define an embedded object with a `RealmSet<E>` type,
as `RealmSet<E>` does not support embedded objects.

Then, define an immutable `backlinks`
property in the child object whose type is the parent object type:

```kotlin
// Backlink of EmbeddedRealmObject must be parent object type
class Post : EmbeddedRealmObject {
    var title: String = ""
    var date: RealmInstant = RealmInstant.now()
    // Backlink to parent RealmObject type (CANNOT be null & MUST be val)
    val user: User by backlinks(User::posts)
}

```
