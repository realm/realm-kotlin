# Property Annotations - Kotlin SDK
This page describes the available annotations that you can use to
customize the behavior of properties in your object models.
For more information on how to define your object model, refer to Define a Realm Object Model - Kotlin SDK.

The Kotlin SDK provides several **property annotations** that add
functionality to Realm object properties. Refer also to the
`Annotations API reference`.

> **NOTE:**
> In Kotlin, value types are implicitly non-nullable. You can
declare properties optional (nullable) using the built-in `?`
Kotlin operator. Or you can assign a default value to a property
in the property declaration. Refer to the
Supported Data Types List for examples.
>

Examples on this page refer to the following `Frog` class:

```kotlin
class Frog : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId() // Primary key property
    @Index
    var name: String = "" // Indexed property
    @Ignore
    var age: Int = 0 // Ignored property
    @PersistedName("latin_name")
    var species: String? = null // Remapped property
    @FullText
    var physicalDescription: String? = null // Full-text search indexed property
}

```

## Specify a Primary Key
A **primary key** is a unique identifier for an object in a realm.
No other object of the same type can share an object's primary key.

To specify a property as the object type's primary key, use the
`@PrimaryKey`
annotation:

```kotlin
@PrimaryKey
var _id: ObjectId = ObjectId() // Primary key property

```

Important aspects of primary keys:

- You can define only one primary key per object schema.
- You *cannot* change the primary key field for an object type after
adding any object of that type to a realm.
- Primary key values must be unique across all instances of an object
in a realm. Attempting to insert a duplicate primary key value results
in an error.
- Primary key values are immutable. To change the primary key value of
an object, you must delete the original object and insert a new object
with a different primary key value.
- Primary keys are nullable. Because primary key values must be
unique, `null` can only be the primary key of one object in a
collection.
- Realm automatically indexes primary keys, so you can efficiently
read and modify objects based on their primary key.

You can create a primary key with any of the following types:

- `String`
- `Byte`
- `Char`
- `Short`
- `Int`
- `Long`
- `ObjectId`
- `RealmUUID`

## Map a Property or Class to a Different Name
> Version added: 10.8.0
> Remap class names with @PersistedName
>

By default, Realm uses the name defined in the model class
to represent classes and fields internally. The Kotlin SDK lets you
map a property or class name to a different **persisted name** than
the name used in your code. Persisting a different name to the realm is useful in some cases, including:

- To make it easier to work across platforms where naming conventions differ.
- To change a class or field name in Kotlin without forcing a migration.
- To support multiple model classes with the same simple name in different packages.
- To use a class name that is longer than the 57-character limit enforced by Realm.

To map a Kotlin class or property name in your code to a different name to persist in
a realm:

1. Use the
`@PersistedName`
annotation on the Kotlin class or property.
2. Specify a class or property `name` that you want persisted to
the realm.

#### Class Persisted Name

In this example, `Frog` is the Kotlin class name used in the code
throughout the project to perform CRUD operations, and `Frog_Entity` is the
persisted name to used to store objects in a realm:

```kotlin
@PersistedName(name = "Frog_Entity") // Remapped class name
class Frog : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var name: String = ""
    var age: Int = 0
    var species: String? = null
    var owner: String? = null
}

```

> **IMPORTANT:**
> When querying an inverse relationship on an object with a
remapped class name, you must use the persisted class name.
In the example above, you must query `Frog_Entity` instead of
`Frog`.
For more information, refer to Query Inverse Relationships.
>

#### Property Persisted Name

In this example, `species` is the Kotlin property name used in the code
throughout the project to perform CRUD operations and `latin_name` is the
persisted name used to store values in a realm:

```kotlin
@PersistedName("latin_name")
var species: String? = null // Remapped property

```

## Ignore Properties from Realm Schema
By default, Realm manages properties defined in your Realm object
model. However, you can choose to ignore properties that you don't
want to persist in a realm.

To ignore a property and prevent it from persisting in a realm, use the
`@Ignore`
annotation:

```kotlin
@Ignore
var age: Int = 0 // Ignored property

```

An ignored property behaves exactly like a managed property, except
they aren't stored to the database, can't be used in queries, and
don't trigger Realm notifications. You can mix managed and ignored properties
within a class.

## Index Properties
**Indexes** are special data structures that store a small portion of
a realm's data in an easy-to-traverse form. Indexes support more
efficient query execution in a realm. When an appropriate index
exists for a query, Realm uses the index to limit the number of
documents that it must inspect. Otherwise, Realm must scan every
document in a collection and select those documents that match a query.

The index stores the value of a specific field ordered by the value of
the field. The ordering of the index entries supports efficient equality
matches and range-based query operations. While indexes speed up some
queries, they also cause slightly slower writes. They come with additional
storage and memory overhead. Realm stores indexes on disk, which makes your
realm files larger. Each index entry is a minimum of 12 bytes.

To create an index on a property, use the `@Index`
annotation on the property:

```kotlin
@Index
var name: String = "" // Indexed property

```

> **NOTE:**
> Primary keys are indexed by default.
>

You can index fields with the following types:

- `String`
- `Byte`
- `Short`
- `Int`
- `Long`
- `Boolean`
- `RealmInstant`
- `ObjectId`
- `RealmUUID`

You *cannot* combine standard indexes with full-text search (FTS)
indexes on the same property. To create an FTS index on a property, refer to
the Full-Text Search Indexes section.

## Full-Text Search Indexes
In addition to standard indexes, Realm also supports Full-Text Search
(FTS) indexes on `String` properties. While you can query a string
field with or without a standard index, an FTS index enables searching
for multiple words and phrases and excluding others.

To create an FTS index on a property, use the `@FullText`
annotation:

```kotlin
@FullText
var physicalDescription: String? = null // Full-text search indexed property

```

Note the following constraints on FTS indexes:

- You can only create an FTS index on properties of `String` type.
- You *cannot* combine FTS indexes and standard indexes on the
same property. To create a standard index on a property, refer to the
Index Properties section.

> **NOTE:**
> For Full-Text Search (FTS) indexes, only ASCII and Latin-1 alphanumerical
chars (most western languages) are included in the index.
>
> Indexes are diacritics- and case-insensitive.
>

For more information on querying full-text indexes, refer to
Filter By Full-Text Search (FTS) Property.
