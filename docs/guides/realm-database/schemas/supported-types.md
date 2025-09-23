# Supported Types - Kotlin SDK
This page describes the supported data types that you can use to define
properties in your object model. For more information on how to define
your object model, refer to Define an Object Model.

## Supported Data Types List
The Kotlin SDK supports the following [Kotlin types](https://kotlinlang.org/docs/basic-types.html),
[BSON](https://bsonspec.org/) types, and
Realm-specific types, which you can use for unique
identifiers, timestamps, counters, and collections.

The Kotlin SDK does *not* natively support:

- user-defined enumeration properties. Refer to the Enums
section for more information on how to use enums in your Realm objects.
- Kotlin's built-in `Date` or `Instant`. Refer to the
RealmInstant section for more information on
how to use timestamps in your Realm objects.

Realm object properties *must* be mutable and initialized when declared.
The Kotlin SDK does not currently support abstract properties. You
can declare properties optional (nullable) using the built-in
`?` Kotlin operator, or you can assign a default value to a property
when you declare it.

> **NOTE:**
> Realm stores all non-decimal numeric types as `Long`
values and all decimal numeric types as `Double` values.
>

**Kotlin Data Types**

The following table lists the supported Kotlin data types and examples of
how to declare them as required or optional properties in your object model.

|Kotlin Data Type|Required|Optional|
| --- | --- | --- |
|`String`|`var stringReq: String = ""`|`var stringOpt: String? = null`|
|`Byte`|`var byteReq: Byte = 0`|`var byteOpt: Byte? = null`|
|`Short`|`var shortReq: Short = 0`|`var shortOpt: Short? = null`|
|`Int`|`var intReq: Int = 0`|`var intOpt: Int? = null`|
|`Long`|`var longReq: Long = 0L`|`var longOpt: Long? = null`|
|`Float`|`var floatReq: Float = 0.0f`|`var floatOpt: Float? = null`|
|`Double`|`var doubleReq: Double = 0.0`|`var doubleOpt: Double? = null`|
|`Boolean`|`var boolReq: Boolean = false`|`var boolOpt: Boolean? = null`|
|`Char`|`var charReq: Char = 'a'`|`var charOpt: Char? = null`|

**BSON Types**

The following table lists the supported BSON data types and examples
of how to declare them as required or optional properties in your object model.
To use these types, you must import them from the
[org.mongodb.kbson](https://github.com/mongodb/kbson) package.

|BSON Type|Required|Optional|
| --- | --- | --- |
|ObjectId|`var objectIdReq: ObjectId = ObjectId()`|`var objectIdOpt: ObjectId? = null`|
|`Decimal128`|`var decimal128Req: Decimal128 = Decimal128("123.456")`|`var decimal128Opt: Decimal128? = null`|

**Realm-Specific Types**

The following table lists the supported Realm-specific data types and
examples of how to declare them as required or optional properties in
your object model.

|Realm-Specific Type|Required|Optional|
| --- | --- | --- |
|RealmUUID|`var uuidReq: RealmUUID = RealmUUID.random()`|`var uuidOpt: RealmUUID? = null`|
|RealmInstant|`var realmInstantReq: RealmInstant = RealmInstant.now()`|`var realmInstantOpt: RealmInstant? = null`|
|RealmAny|N/A|`var realmAnyOpt: RealmAny? = RealmAny.create("foo")`|
|MutableRealmInt|`var mutableRealmIntReq: MutableRealmInt = MutableRealmInt.create(0)`|`var mutableRealmIntOpt: MutableRealmInt? = null`|
|RealmList|`var listReq: RealmList<CustomObjectType> = realmListOf()`|N/A|
|RealmSet|`var setReq: RealmSet<String> = realmSetOf()`|N/A|
|RealmDictionary|`var dictionaryReq: RealmDictionary<String> = realmDictionaryOf()`|N/A|
|RealmObject|N/A|`var realmObjectPropertyOpt: CustomObjectType? = null`|
|EmbeddedRealmObject|N/A|`var embeddedProperty: EmbeddedObjectType? = null`|

## Unique Identifiers
The Kotlin SDK supports [UUID](https://en.wikipedia.org/wiki/Universally_unique_identifier)
and `ObjectId` as unique identifiers for Realm objects.

> **NOTE:**
> In general, you can use `UUID` for any fields that function as a unique
identifier.

### ObjectId
`ObjectId` is a 12-byte, globally unique value that you can use as an identifier for objects.
It is nullable, indexable, and can
be used as a primary key.

You can initialize an `ObjectId` using `ObjectId()`.

> **IMPORTANT:**
> In Realm Kotlin SDK version 1.5.0 and newer,
`io.realm.kotlin.types.ObjectId`
is deprecated. You must import `ObjectId` from
[org.mongodb.kbson.ObjectId](https://github.com/mongodb/kbson) instead.
>

### RealmUUID
`UUID` (Universal Unique Identifier) is a 16-byte unique value
that you can use as an identifier for objects. It is nullable,
indexable, and can be used as a primary key.

Realm creates UUIDs with the `RealmUUID`
type that conform to [RFC 4122 version 4](https://www.rfc-editor.org/info/rfc4122)
and are created with random bytes.

You can generate a random `RealmUUID` using `RealmUUID.random()`
or pass a UUID-formatted string to `RealmUUID.from()`:

```kotlin
val uuid1 = RealmUUID.from("46423f1b-ce3e-4a7e-812f-004cf9c42d76")
val uuid2 = RealmUUID.random()

```

## MutableRealmInt (Counter)
The Kotlin SDK offers `MutableRealmInt`
as a special integer type that you can use as a logical counter to accurately
synchronize numeric changes across multiple distributed clients.
It behaves like a `Long` but also supports `increment` and `decrement`
methods that implement a
[conflict-free replicated data type](https://en.wikipedia.org/wiki/Conflict-free_replicated_data_type).
This ensures that numeric updates can be executed regardless of order to
converge to the same value.

A `MutableRealmInt` property:

- *cannot* be used as a primary key
- *cannot* store null values, but it can be declared nullable
(`MutableRealmInt?`)

Additionally, `MutableRealmInt` fields:

- are backed by Kotlin's
[numeric types](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-number/),
so no migration is required when changing a numeric field to `MutableRealmInt`.
- can use operators and infix functions similar to those provided by `Long`.
However, note that any operations *other than* `set`, `increment`, and
`decrement` do not mutate the instance on which they are executed. Instead,
they create a new, unmanaged `MutableRealmInt` instance with the updated
value.

Learn how to Create a MutableRealmInt (Counter) Property and
Update a MutableRealmInt (Counter) Property.

## RealmInstant (Timestamp)
You cannot store Kotlin's built-in `Date` or `Instant` types in Realm.

Instead, the Kotlin SDK uses the `RealmInstant`
type to store time information as a [Unix epoch](https://en.wikipedia.org/wiki/Unix_time)
timestamp.

If you need timestamp data in a form other than `RealmInstant`, you
can add conversion code to your model class based on the following
example:

```kotlin
// model class that stores an Instant (kotlinx-datetime) field as a RealmInstant via a conversion
class RealmInstantConversion : RealmObject {
    private var _timestamp: RealmInstant = RealmInstant.from(0, 0)
    public var timestamp: Instant
        get() {
            return _timestamp.toInstant()
        }
        set(value) {
            _timestamp = value.toRealmInstant()
        }
}

fun RealmInstant.toInstant(): Instant {
    val sec: Long = this.epochSeconds
    // The value always lies in the range `-999_999_999..999_999_999`.
    // minus for timestamps before epoch, positive for after
    val nano: Int = this.nanosecondsOfSecond

    return if (sec >= 0) { // For positive timestamps, conversion can happen directly
        Instant.fromEpochSeconds(sec, nano.toLong())
    } else {
        // For negative timestamps, RealmInstant starts from the higher value with negative
        // nanoseconds, while Instant starts from the lower value with positive nanoseconds
        // TODO This probably breaks at edge cases like MIN/MAX
        Instant.fromEpochSeconds(sec - 1, 1_000_000 + nano.toLong())
    }
}

fun Instant.toRealmInstant(): RealmInstant {
    val sec: Long = this.epochSeconds
    // The value is always positive and lies in the range `0..999_999_999`.
    val nano: Int = this.nanosecondsOfSecond

    return if (sec >= 0) { // For positive timestamps, conversion can happen directly
        RealmInstant.from(sec, nano)
    } else {
        // For negative timestamps, RealmInstant starts from the higher value with negative
        // nanoseconds, while Instant starts from the lower value with positive nanoseconds
        // TODO This probably breaks at edge cases like MIN/MAX
        RealmInstant.from(sec + 1, -1_000_000 + nano)
    }
}

```

## RealmAny (Mixed)
> Version changed: 2.0.0
> `RealmAny` can hold lists and dictionaries of mixed data.
>

`RealmAny`
represents a non-nullable mixed data type. It behaves like the value type
that it contains. `RealmAny` can hold:

- supported Kotlin data types (note that
`Byte`, `Char`, `Int`, `Long`, and `Short` values are converted
internally to `int64_t` values)
- supported BSON types
- `RealmList` and `RealmDictionary` collections of mixed data
- the following Realm-specific types: RealmInstantRealmUUIDRealmObject (holds a reference to the object, not a copy of it)

`RealmAny` *cannot* hold `EmbeddedRealmObject` types, `RealmSet`, or
another `RealmAny`.

`RealmAny` properties:

- are indexable but *cannot* be used as a
primary key
- must be declared nullable (`RealmAny?`), but they *cannot* store null values
- can be aggregated with
`RealmQuery.max`,
`RealmQuery.min`,
and
`RealmQuery.sum`.
- can be sorted. Sort order from highest to lowest: `Boolean``Byte`, `Double`, `Decimal128`, `Int`, `Float`, `Long`, `Short``byte[]`, `String``Date``ObjectId``UUID``RealmObject`

You can store multiple `RealmAny` instances in `RealmList`,
`RealmDictionary`, or `RealmSet` fields.

> **TIP:**
> Because you must know the stored type to extract its value, we
recommend using a `when` expression to handle the
`RealmAny` type and its possible inner value class.
>

### Collections as Mixed
In version 2.0.0 and later, a `RealmAny` data type can
hold collections (a list or dictionary, but *not* a set) of `RealmAny`
elements. You can use mixed collections to
model unstructured or variable data. For more information, refer to
Define Unstructured Data.

- You can nest mixed collections up to 100 levels.
- You can query mixed collection properties and
register a listener for changes,
as you would a normal collection.
- You can find and update individual mixed collection elements
- You *cannot* store sets or embedded objects in mixed collections.

To use mixed collections in your app, define the mixed type
property in your data model the same way you would any other `RealmAny` type.
Then, create the list or dictionary collections using `RealmAny.create()`.

## Collection Types
The Kotlin SDK offers several collection types that you can use as
properties in your data model. A collection is an object
that contains zero or more instances of one supported data type.
Realm collections are homogenous (all objects in a collection are of the
same type) and are backed by their corresponding built-in Kotlin classes.

Collection types are non-null. When you define a collection property, you must
initialize it.
For more information, refer to Create a Collection.

### RealmList
The `RealmList`
type implements Kotlin's
[List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/)
interface. Unmanaged lists
behave like Kotlin's `MutableList`.

A `RealmList` represents a to-many relationship
containing:

- any of the supported Kotlin data types
- any of the supported BSON types
- a `RealmObject`
- an `EmbeddedRealmObject`

`RealmList<E>` is a non-null type, where:

- lists of `RealmObject` or `EmbeddedRealmObject` elements *cannot*
be nullable
- lists of any other supported elements can be nullable (`RealmList<E?>`)

### RealmSet
The `RealmSet`
type implements Kotlin's
[Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-set/)
interface. Unmanaged sets behave
like Kotlin's `MutableSet`.

A `RealmSet` represents a to-many relationship
containing distinct values of:

- any of the supported Kotlin data types
- any of the supported BSON types
- a `RealmObject`

You cannot use `EmbeddedRealmObject` elements in a `RealmSet`.

`RealmSet<E>` is a non-null type, where:

- sets of `RealmObject` elements *cannot* be nullable
- sets of any other supported elements can be nullable (`RealmSet<E?>`)

### RealmMap/RealmDictionary
The `RealmMap`
type implements Kotlin's [Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/)
interface and is an associative array that contains key-value `String` pairs
with unique keys.
`RealmDictionary`
is a specialized `RealmMap` that accepts a `String` key and non-string values.
Unmanaged dictionaries behave
like Kotlin's [LinkedHashMap](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-linked-hash-map/).

`RealmDictionary` values can be:

- any of the supported Kotlin data types
- any of the supported BSON types
- a `RealmObject`
- an `EmbeddedRealmObject`

`RealmDictionary<K, V>` is a non-null type, where:

- keys must be strings
- `RealmObject` or `EmbeddedRealmObject` values *must* be nullable
(`RealmDictionary<K, V?>`)
- any other supported element values can be nullable

## RealmObjects as Properties
You can use `RealmObject` and any subclasses, *except*
`AsymmetricRealmObject` as properties in your object model.

> **IMPORTANT:**
> `AsymmetricRealmObject` *cannot* be used as properties.
For more information, refer to Asymmetric Objects.
>

### RealmObjects
A `RealmObject`
type represents a custom object that you can use as a property.

`RealmObject` properties:

- must be declared nullable
- can be used as elements in collections
- can be held as a `RealmAny` value
- *cannot* be used as a primary key

You can also reference one or more Realm objects from another through
to-one and to-many relationships.
For more information, refer to the Relationships
page.

### Backlinks
A backlink represents an inverse, to-many relationship between a
`RealmObject` and one or more `RealmObject` or between a `RealmObject`
and an `EmbeddedRealmObject`. Backlinks cannot be null.

Backlinks implement:

- the `BacklinksDelegate`
type for `RealmObject` backlinks
- the `EmbeddedBacklinksDelegate`
type for `EmbeddedRealmObject` backlinks

For more information, refer to Inverse Relationships.

### EmbeddedRealmObject
An `EmbeddedRealmObject`
is a special type of `RealmObject`.

`EmbeddedRealmObject` properties:

- must be nullable objects within the parent object
- must be nullable values within a dictionary
- *cannot* be nullable elements within a list
- *cannot* be used as a primary key
- can be properties within an asymmetric object

For more information, refer to Embedded Objects.

## Geospatial Types
> Version added: 1.11.0

The Kotlin SDK supports geospatial queries using the following data types:

- `GeoPoint`
- `GeoCircle`
- `GeoBox`
- `GeoPolygon`

> **IMPORTANT:**
> Currently, geospatial data types *cannot* be persisted. For example, you can't
declare a property that is of type `GeoBox`.
>
> These types can only be used as arguments for geospatial queries.
>

For more information on querying with geospatial data, refer to
Geospatial Data.

## Enums
The Kotlin SDK does not natively support enumerations, or enums. To use
enums in a Realm object class, define a field with a type matching the
underlying data type of your enum.

Then, create getters and setters for the field that convert the field
value between the underlying value and the enum type.

```kotlin
enum class EnumClass(var state: String) {
    NOT_STARTED("NOT_STARTED"),
    IN_PROGRESS("IN_PROGRESS"),
    COMPLETE("COMPLETE")
}

class EnumObject : RealmObject {
    var name: String? = null
    private var state: String = EnumClass.NOT_STARTED.state
    var stateEnum: EnumClass
        get() = EnumClass.valueOf(state)
        set(value) {
            state = value.state
        }
}

```
