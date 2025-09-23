# Migrate from the Java SDK to the Kotlin SDK
> **NOTE:**
> The Kotlin SDK is a separate Realm client SDK built entirely with the
> Kotlin programming language. The Kotlin SDK uses an entirely
> different codebase from the Java SDK. It is designed specifically to
> take advantage of Kotlin language features such as coroutines and
> suspend functions. The Java SDK also supports some of these
> features, as well as Android applications written in Kotlin. But the
> Kotlin SDK is more Kotlin-idiomatic than the Java SDK.
>

## Overview
The Java SDK and the Kotlin SDK differ in many ways. On this page, you'll
find a high-level comparison of most of the ways the SDKs differ.

## Kotlin SDK Architecture
The Java SDK provided live objects, queries, and realms that
automatically update when underlying data changes. The Kotlin SDK still
provides this live interface in write transactions, but otherwise relies
on a new frozen architecture that makes Realm objects easier to work
with. Here are some of the main differences between the Java SDK
architecture and the Kotlin SDK architecture:

- **Frozen by default**: All objects are now frozen. Unlike live objects,
frozen objects do not automatically update after database writes. You
can still access live objects within a write transaction, but passing
a live object out of a write transaction freezes the object.
- **Thread-safety**: All realm instances, objects, query results, and
collections can now be transferred across threads.
- **Singleton**: You now only need one instance of each realm.
No need to open and close realms on individual threads.

> **SEE ALSO:**
> Frozen Architecture in the Kotlin SDK
>

## Opening Realms
The Java SDK automatically detects Realm Object Models defined in your
application, and uses all of them in the schema of opened realms unless
you specify otherwise. The Kotlin SDK requires you to manually specify
the Realm Object Models to use in your realm schema. Additionally:

- The Kotlin SDK does not provide the ability to set and access a
default realm in your application. Since you can now share realms,
objects, and results across threads, you can rely on a global singleton
instead.
- The Java SDK used `RealmConfiguration.Builder().build()` to
generate instances of `RealmConfiguration`. With the Kotlin SDK,
use the `RealmConfiguration.create()`
companion method `RealmConfiguration` instead.
- The Java SDK used the static `Realm.getInstance()` method to
open a realm with a given config. With the Kotlin SDK, use the static
`Realm.open()` method instead.

Optionally, use `RealmConfiguration.Builder`
to customize your configuration even more.

> **SEE ALSO:**
> Open & Close a Realm
>

## Realm Object Models
In the Java SDK, you declare Realm object models in one of two ways:

- extend `RealmObject`
- implement `RealmModel`

The Kotlin SDK uses default methods in the `RealmObject` interface
instead. With the Kotlin SDK, inherit from `RealmObject` to
declare a Realm object model. Annotations work the same way they did
in java for fields with special properties, such as ignored fields,
primary keys, and indexes.

> **SEE ALSO:**
> Supported Schemas Types
>

## Relationships
Both the Java and Kotlin SDKs declare relationships through Realm object
fields.

With the Java SDK, you could define one-to-many relationships with fields
of type RealmList. The Kotlin SDK still uses fields of
type RealmList, but you should instantiate RealmList
instances with the `realmListOf()` companion method.


## Schema Types
With the Java SDK, you needed to use the @Required annotation to
make lists of primitives non-nullable in realm object models. The Kotlin
SDK makes lists of primitives non-nullable by default. Use the
`?` operator to make a list of primitives nullable.


## Writes
The Kotlin SDK introduces new names for the methods that write to realms.

### Async
With the Java SDK, you could write asynchronously to a realm with
realm.executeTransactionAsync(). The Kotlin SDK uses
the suspend function `realm.write()` instead.


> **SEE ALSO:**
> - Create a New Object
> - Modify an Object
> - Delete an Object
>

## Queries
There are several differences between queries in the Java SDK and queries
in the Kotlin SDK:

- With the Java SDK, you can query objects in realms using a fluent
interface or Realm Query Language (RQL).
The Kotlin SDK only uses RQL.
- The Java SDK uses `realm.where()`
to query realms, whereas the Kotlin SDK uses `realm.query()`.
- With the Java SDK, you could query asynchronously with
`realmQuery.findAllAsync()` and `realmQuery.findFirstAsync()`.
In the Kotlin SDK, query asynchronously with
`realmQuery.asFlow()`.
Once you have a flow of results, you can `collect`
the results.
- With the Java SDK, you could query synchronously with
`realmQuery.findAll()` and realmQuery.findFirst().
In the Kotlin SDK, query synchronously with
`realmQuery.find()`.

> **SEE ALSO:**
> - Filter Data
> - Sort Queries
>

## Deletes
In both SDKs, you can only delete live objects. The Kotlin SDK provides
`mutableRealm.findLatest()`
to access a live version of any frozen object. In a write transaction,
you can directly query for live objects and delete them without using
findLatest().

> **SEE ALSO:**
> - Delete all Objects of a Type
> - Delete Multiple Objects
> - Delete an Object
>

## Notifications
In both SDKs, you can subscribe to change to collections of results.
With the Java SDK, you could receive notifications whenever realm results
changed with the following interfaces:

- `realmResults.addChangeListener()`
- RxJava through `asFlowable()`
- Kotlin Extensions with `toFlow()`

The Kotlin SDK replaces all of these options with `realmQuery.asFlow()`.
Once you have a flow of results, you can call `collect`
to subscribe to changes. Any object of type `UpdatedResults` emitted
by the flow represents a change to the results set.

## Threading
With the Java SDK, realms, Realm objects, and results cannot be passed
between threads. The Kotlin SDK freezes these objects by default, making
them thread-safe. Unlike the live objects used by the Java SDK, the
frozen objects found in the Kotlin SDK do not automatically update when
underlying data changes. With the Kotlin SDK, you must use notifications to subscribe to
updates instead.

> **SEE ALSO:**
> Frozen Architecture
>

## Migrations
With the Java SDK, migrations were a manual process. The Kotlin SDK
automates migrations, but also gives you access to a similar dynamic
realm interface for custom tweaks to migration logic.

## What Next?
Now that you understand the differences between the Java SDK and the
Kotlin SDK, check out the rest of the Kotlin SDK documentation.
