# Define a Realm Object Model - Kotlin SDK
This page describes Realm object types and how to define Realm objects as part of
your application's data model. After you define your object model, you can
open a realm with a schema that includes your
defined objects and work with them in the realm.

The Kotlin SDK memory maps Realm objects directly to native Kotlin
objects, so there's no need to use a special data access library.
You define your application's data model via regular Kotlin classes declared
in your application code object.

To learn about how to make changes to your Realm objects after
defining your Realm object model, refer to
Change an Object Model.

## Realm Objects
**Realm objects** are uniquely named instances of Kotlin classes that you can work with
as you would any other class instance.

Each object class represents an **object type**. Objects of the same type share
an **object schema**, which defines the properties and relationships for objects
of that type. The SDK guarantees that all objects in a realm conform to the
schema for their object type and validates objects whenever they are created,
modified, or deleted.

However, note that Realm objects have the following constraints:

- Realm objects *must* inherit from the `RealmObject` class or its
subclasses: `EmbeddedRealmObject` or `AsymmetricRealmObject`.
The Kotlin SDK does *not* support inheritance from custom base classes.
- The Kotlin SDK requires that Realm objects have an empty constructor.
Refer to the workaround example
noted in the next section.
- Class names are limited to a maximum of 57 UTF-8 characters.

Additionally, the Kotlin SDK does *not* support using Kotlin
[data classes](https://kotlinlang.org/docs/data-classes.html) to model
data. This is because data classes are typically used for immutable data,
which goes against how the Realm Kotlin SDK models data.

## Define a New Object Type
To define a new object type, you must create a uniquely named Kotlin
class that implements either the `RealmObject`,
`EmbeddedRealmObject`, or `AsymmetricRealmObject` interface.

> **NOTE:**
> Class names are limited to a maximum of 57 UTF-8 characters.
>

Then, you specify your object's properties, including:

- The data type for each property.
The Kotlin SDK supports the following data types: [Kotlin primitive types](https://kotlinlang.org/docs/basic-types.html)a limited subset of [BSON](https://bsonspec.org/) typesRealm-specific types,
which you can use for unique identifiers, timestamps, counters, and
collections
- Any property annotations, which add
functionality to properties in your Realm objects. You can use annotations to: Designate a property as a primary keyMark a property as indexableIgnore a propertyMap a property or class name to another name
- Any relationships with other Realm objects.

After you've defined your Realm object model, you can pass the set of object
classes to the realm's configuration when you open a realm, and then work with those objects in the realm.

> **IMPORTANT:**
> The Realm Kotlin SDK does *not* support having a single primary constructor.
The SDK requires an empty constructor to create objects. As a workaround,
you can do something similar to the following:
>
> ```kotlin
> class Person(var name: String, var age: Int): RealmObject {
>    constructor(): this("", 0) // Empty constructor required by Realm
> }
> ```
>

### Define a Realm Object Type
To define a Realm object type, create a Kotlin class that implements the
`RealmObject`
interface:

```kotlin
// Implements the `RealmObject` interface
class Frog : RealmObject { // Empty constructor required by Realm
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var name: String = ""
    var age: Int = 0
    var species: String? = null
    var owner: String? = null
}

```

You can then use the object as a property
to define relationships with other Realm objects.

### Define an Embedded Object Type
An `EmbeddedRealmObject` is a special type of Realm object that models
complex data about a specific object. Realm treats each embedded object as
nested data inside of a single specific parent object.

Because of this, embedded objects have the following constraints:

- An embedded object requires a parent object and *cannot* exist as an
independent Realm object. If the parent object no longer references the
embedded object, the embedded object is automatically deleted.
- An embedded object inherits the lifecycle of its parent object. For
example, if you delete the parent object, the embedded object is also
deleted.
- Embedded objects have strict ownership with their parent object.
You *cannot* reassign an embedded object to a different parent object, or
share an embedded object between multiple parent objects.
- Embedded objects *cannot* have a primary key.

> **TIP:**
> If you need to manually manage the lifecycle of a referenced object or
want the referenced objects to persist after the deletion of the parent
object, use a regular Realm object with a to-one relationship instead.
For more information, refer to Relationships - Kotlin SDK.
>

To define an embedded object type, create a Kotlin class that implements the
`EmbeddedRealmObject`
interface:

```kotlin
// Implements `EmbeddedRealmObject` interface
class EmbeddedAddress : EmbeddedRealmObject {
    // CANNOT have primary key
    var street: String? = null
    var city: String? = null
    var state: String? = null
    var postalCode: String? = null
    var propertyOwner: Contact? = null
}

```

Embedded object types are reusable and composable. You can use the
same embedded object type in multiple parent object types inside
other embedded object types.

After you define your embedded object type, you must define a relationship
with a parent object in your data model. To learn how, refer to
Define an Embedded Object.

## Define Collection Properties
A collection is an object that contains zero or more instances of a supported
data type. Realm collections are homogenous (all objects in a collection are
of the same type) and are backed by their corresponding built-in Kotlin classes.
For more information on the collection types used in the Kotlin SDK and their
supported data types, refer to Collection Types.

The Kotlin SDK offers several collection types that you can use as
properties in your data model: `RealmList`, `RealmSet`, and
`RealmDictionary`.

Collections also let you define to-many relationships between Realm objects.
Refer to Relationships - Kotlin SDK for more information.

> **IMPORTANT:**
> Collection types are non-null. When you define a collection property,
you *must* initialize it.
>

### Define a RealmList
To define a property as a RealmList, specify its
type within the object schema as `RealmList<E>` and initialize the
default value using `realmListOf()`:

```kotlin
// RealmList<E> can be any supported primitive
// or BSON type, a RealmObject, or an EmbeddedRealmObject
class Frog : RealmObject {
    var _id: ObjectId = ObjectId()
    var name: String = ""
    // List of RealmObject type (CANNOT be nullable)
    var favoritePonds: RealmList<Pond> = realmListOf()
    // List of EmbeddedRealmObject type (CANNOT be nullable)
    var favoriteForests: RealmList<EmbeddedForest> = realmListOf()
    // List of primitive type (can be nullable)
    var favoriteWeather: RealmList<String?> = realmListOf()
}

class Pond : RealmObject {
    var _id: ObjectId = ObjectId()
    var name: String = ""
}

```

### Define a RealmSet
To define a property as a RealmSet, specify its
type within the object schema as `RealmSet<E>` and initialize the
default value using `realmSetOf()`:

```kotlin
// RealmSet<E> can be any supported primitive or
// BSON type or a RealmObject
class Frog : RealmObject {
    var _id: ObjectId = ObjectId()
    var name: String = ""
    // Set of RealmObject type (CANNOT be nullable)
    var favoriteSnacks: RealmSet<Snack> = realmSetOf()
    // Set of primitive type (can be nullable)
    var favoriteWeather: RealmSet<String?> = realmSetOf()
}

class Snack : RealmObject {
    var _id: ObjectId = ObjectId()
    var name: String = ""
}

```

### Define a RealmDictionary/RealmMap
To define a property as a RealmDictionary, specify its
type within the object schema as a `RealmDictionary<K, V>` and initialize the
the default value using `realmDictionaryOf()`:

```kotlin
// RealmDictionary<K, V> can be any supported
// primitive or BSON types, a RealmObject, or
// an EmbeddedRealmObject
class Frog : RealmObject {
    var _id: ObjectId = ObjectId()
    var name: String = ""
    // Dictionary of RealmObject type (value MUST be nullable)
    var favoriteFriendsByPond: RealmDictionary<Frog?> = realmDictionaryOf()
    // Dictionary of EmbeddedRealmObject type (value MUST be nullable)
    var favoriteTreesInForest: RealmDictionary<EmbeddedForest?> = realmDictionaryOf()
    // Dictionary of primitive type (value can be nullable)
    var favoritePondsByForest: RealmDictionary<String?> = realmDictionaryOf()
}

```

Realm disallows the use of `.` or `$` characters in map keys.
You can use percent encoding and decoding to store a map key that contains
one of these disallowed characters.

```kotlin
// Percent encode . or $ characters to use them in map keys
val mapKey = "Hundred Acre Wood.Northeast"
val encodedMapKey = "Hundred Acre Wood%2ENortheast"

```

## Define Unstructured Data
> Version added: 2.0.0

Starting in Kotlin SDK version 2.0.0, you can store
collections of mixed data
within a  `RealmAny` property. You can use this feature to model complex data
structures, such as JSON, without having to define a
strict data model.

**Unstructured data** is data that doesn't easily conform to an expected
schema, making it difficult or impractical to model to individual
data classes. For example, your app might have highly variable data or dynamic
data whose structure is unknown at runtime.

Storing collections in a mixed property offers flexibility without sacrificing
functionality. And
you can work with them the same way you would a non-mixed
collection:

- You can nest mixed collections up to 100 levels.
- You can query on and react to changes on mixed collections.
- You can find and update individual mixed collection elements.

However, storing data in mixed collections is less performant than using a structured
schema or serializing JSON blobs into a single string property.

To model unstructured data in your app, define the appropriate properties in
your schema as RealmAny types. You can then set these
`RealmAny` properties as a RealmList or a
RealmDictionary collection of `RealmAny` elements.
Note that `RealmAny` *cannot* represent a `RealmSet` or an embedded object.

> **TIP:**
> - Use a map of mixed data types when the type is unknown but each value will have a unique identifier.
> - Use a list of mixed data types when the type is unknown but the order of
objects is meaningful.
>
