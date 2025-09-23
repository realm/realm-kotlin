# Create Realm Objects - Kotlin SDK
This page describes the concepts of write transactions and managed objects in
a realm, then explains how to create and persist a new object to a
local database using the Kotlin SDK. To learn more about Realm objects and how
to define them, refer to Realm Objects.

You can create objects whose object type is included in the
realm schema when you open the realm.
For more information, refer to
Open a Realm or Open a Synced Realm.

## Write Transactions
Realm handles writes in terms of transactions. All writes must happen
within a transaction. A **transaction** is a list of read and write operations
that Realm treats as a single indivisible operation: either *all* of the
operations succeed or *none* of the operations in the transaction
take effect.

Realm represents each transaction as a callback function
that contains zero or more read and write operations. To run
a transaction, you define a transaction callback and pass it to
the realm's
`write()`
or
`writeBlocking()`
method. Within this callback, you can access a
`MutableRealm`
instance and then create, read, update, and delete objects within the realm.
The **mutable realm** represents the writeable state of a Realm file.
Mutable realms are provided and managed automatically through the
`realm.write` or `realm.writeBlocking` methods.

A realm allows only one open write transaction at a time. Realm
blocks other writes on other threads until the open
transaction on the mutable realm is complete. Consequently, there is no race
condition when reading values from the realm within a
transaction.

When you are done with your transaction, Realm either
commits it or cancels it:

- When Realm commits a transaction, Realm writes
all changes to disk.
- When Realm cancels a write transaction or an operation in
the transaction causes an error, all changes are discarded.

> **NOTE:**
> Objects returned from a write closure become frozen objects when the
write transaction completes. For more information, refer to
Frozen Architecture - Kotlin SDK.
>

## Managed and Unmanaged Objects
Realm APIs may refer to objects as managed or unmanaged. When you create
a Realm object with the Kotlin SDK, it is unmanaged until it is copied
to a realm, which creates a managed instance.

- **Managed objects** are Realm objects that persist in a realm. Managed objects
can only be accessed from an open realm. They can be updated
with changes within write transactions as long as that realm remains open.
Managed objects are tied to the realm instance from which they originated
and cannot be written to another realm. You can use Realm APIs with managed objects. For example,
managed objects can have relationships with other objects and
be observed for changes.
You can also create an unmanaged copy of a managed object, refer to the
Create an Unmanaged Copy of a Realm Object or Collection section on this page.
- **Unmanaged objects** are instances of Realm objects that
behave like normal Kotlin objects, but they are not persisted in a realm.
All Realm objects are unmanaged until you copy them to a realm within
a write transaction.
You cannot use Realm APIs with unmanaged objects or observe them for changes.

> **TIP:**
> You can check if an object is managed with the `isManaged()` method.
>

## Create a Realm Object
Before you can create a new object and persist it to the realm, you must
Define a New Object Type.
Then, you include that object type in your realm schema
when you open the realm.

> **IMPORTANT:**
> You can only write objects whose object type
is included in the realm schema. If you try to
reference or write an object of an object type
that isn't in your schema, Realm will return a
schema validation error.
>

To create a new object and persist it to the realm:

1. Open a write transaction with `realm.write()` or
`realm.writeBlocking()`.
2. Instantiate an unmanaged object instance with the class
constructor. You can use an [apply block](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/apply.html)
to configure multiple properties at once.
3. Pass the unmanaged object instance to `copyToRealm()`
to persist the object data to the realm. This method returns a
live managed instance of the object. Asymmetric objects are special write-only objects that do not
persist to the realm. They *do not* use `copyToRealm()`.
Instead, you pass the asymmetric object instance to the
`insert()` extension method within a write transaction.
Refer to the Create an Asymmetric Object section
on this page for more information.
4. Work with the persisted Realm object
through the returned instance. The live object is accessible until the
write transaction completes. Note that this *does not* apply to
asymmetric objects, which are write-only and do not persist to the realm.

You can also upsert into a realm using specific criteria. For more
information, refer to Upsert a Realm Object.

### Create a Realm Object
To create a new `RealmObject` instance, instantiate a new object of a
realm object type.

In the following example, we instantiate a `Frog` object in a
`realm.write()` block, then pass the instantiated object to
`copyToRealm()` to return a managed instance:

```kotlin
// Open a write transaction
realm.write {
    // Instantiate a new unmanaged Frog object
    val unmanagedFrog = Frog().apply {
        name = "Kermit"
        age = 42
        owner = "Jim Henson"
    }
    assertFalse(unmanagedFrog.isManaged())

    // Copy the object to realm to return a managed instance
    val managedFrog = copyToRealm(unmanagedFrog)
    assertTrue(managedFrog.isManaged())

    // Work with the managed object ...
}
```

### Create an Embedded Object
To create a new `EmbeddedRealmObject` instance, assign an
instance of an embedded object type to a
parent object's property. This can be in a one-to-one, one-to-many, or
inverse relationship
depending on how you defined the embedded object within the parent
object type. For more information, refer to
Define an Embedded Object.

> **NOTE:**
> An embedded object requires a parent object and *cannot* exist as an
> independent Realm object.
>

Embedded objects have strict ownership with their parent object.
After you create the embedded object, you *cannot* reassign it to a
different parent object or share it between multiple parent objects.

In the following example, we instantiate a new  `Contact` object with
an embedded `Address`, which contains a `Contact` object and an embedded
`Country` object:

```kotlin
realm.write {
    // Instantiate a parent object with one embedded address
    val contact = Contact().apply {
        name = "Kermit"
        address = EmbeddedAddress().apply {
            propertyOwner = Contact().apply { name = "Mr. Frog" }
            street = "123 Pond St"
            country = EmbeddedCountry().apply { name = "United States" }
        }
    }
    // Copy all objects to the realm to return managed instances
    copyToRealm(contact)
}
```

We also instantiate a new `Business` object with a
list of embedded `Address` objects, which also contain `Contact`
objects and embedded `Country` objects:

```kotlin
realm.write {
    // Instantiate a parent object with multiple embedded addresses
    val localOffice = EmbeddedAddress().apply {
        propertyOwner = Contact().apply { name = "Michigan J. Frog" }
        street = "456 Lily Pad Ln"
        country = EmbeddedCountry().apply { name = "United States" }
    }
    val remoteOffice = EmbeddedAddress().apply {
        propertyOwner = Contact().apply { name = "Mr. Toad" }
        street = "789 Leaping Frog Ave"
        country = EmbeddedCountry().apply { name = "Ireland" }
    }
    val business = Business().apply {
        name = "Big Frog Corp."
        addresses = realmListOf(localOffice, remoteOffice)
    }
    // Copy all objects to the realm to return managed instances
    copyToRealm(business)
}
```
## Create Realm Properties
Depending on how you define your object type, you might have properties
that are special
Realm-specific types.

In the following example, we have a `Frog` object type with
several Realm properties:

```kotlin
class Frog : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var name: String = ""
    var birthdate: RealmInstant? = null
    var fliesEaten: MutableRealmInt? = null
    var favoriteThings: RealmList<RealmAny?> = realmListOf()
}
```

### Create a RealmInstant (Timestamp) Property
To create a new object instance with a
`RealmInstant`
property, instantiate an object and pass an initial value to the
`RealmInstant` property using either:

- `RealmInstant.from()`:
the epochSeconds and nanoseconds since the Unix epoch
- `RealmInstant.now()`:
the epochSeconds and nanoseconds since the Unix epoch until now

For more information about the `RealmInstant` type, refer to
RealmInstant (Timestamp).

In the following example, we instantiate a new `Frog` object with a
`birthdate` property and pass an initial value to `RealmInstant.from()`:

```kotlin
realm.write {
    // Instantiate a new unmanaged Frog object with a RealmInstant property
    val frog = Frog().apply {
        name = "Kermit"
        // Set an initial value with RealmInstant.from() or RealmInstant.now()
        birthdate = RealmInstant.from(1_577_996_800, 0)
    }
    // Copy the object to the realm to return a managed instance
    copyToRealm(frog)
}
```

### Create a MutableRealmInt (Counter) Property
To create a new object instance with a
`MutableRealmInt`
property, instantiate an object and pass an initial value to the
`MutableRealmInt` property using
`MutableRealmInt.create()`.
For more information about the `MutableRealmInt` type, refer to
MutableRealmInt (Counter).

In the following example, we instantiate a new `Frog` object with a
`fliesEaten` property and pass an initial value to
`MutableRealmInt.create()`:

```kotlin
realm.write {
    // Instantiate a new unmanaged Frog object with a MutableRealmInt property
    val frog = Frog().apply {
        name = "Michigan J. Frog"
        // Set an initial value with MutableRealmInt.create()
        fliesEaten = MutableRealmInt.create(200)
    }
    // Copy the object to the realm to return a managed instance
    copyToRealm(frog)
}
```

### Create a RealmAny (Mixed) Property
To create a new object instance with a polymorphic
`RealmAny`
property, instantiate an object and pass an initial value of a
supported type to the `RealmAny` property using
`RealmAny.create()`.
For a list of the value types that `RealmAny` can hold, refer to
RealmAny (Mixed).

In the following example, we instantiate a new `Frog` object with a
`favoriteThings` list of `RealmAny` type and pass the initial values to
`RealmAny.create()`:

```kotlin
realm.write {
    // Instantiate a new unmanaged Frog object with a RealmAny property
    val frog = Frog().apply {
        name = "Kermit"
        // Set initial values with RealmAny.create()
        favoriteThings = realmListOf(
            RealmAny.create(42),
            RealmAny.create("rainbows"),
            RealmAny.create(Frog().apply {
                name = "Kermit Jr."
            })
        )
    }
    // Copy the object to the realm to return a managed instance
    copyToRealm(frog)
}
```

After you create the object, you *must* know the stored value type
to work with the `RealmAny` property. To learn how to update `RealmAny`
properties after you create the object, refer to Update a RealmAny (Mixed) Property.

## Create Collection Properties
Depending on how you define your object type, you might have properties
that are defined as one of the following
supported Collection Types:

- `RealmList`
- `RealmSet`
- `RealmDictionary`

For more information, refer to Define Collection Properties.

Collections are mutable: you can add and remove elements in a collection
within a write transaction.

Collections can contain both managed and unmanaged objects. When
you copy a collection to the realm, you create a managed instance of the
collection *and* all elements in the collection, including any
unmanaged elements. Unmanaged collections behave like their corresponding
Kotlin classes and are not persisted to the realm.

> **TIP:**
> After you create a collection, you can register a notification
> handler to listen for changes. For more information, refer to
> Register a Collection Change Listener.
>

### Create a RealmList
To create a new object instance with a
`RealmList`
property, instantiate an object and pass any values of a
supported type to the `RealmList` property. For a list of the value types
that `RealmList` can hold, refer to RealmList.

You can instantiate an unmanaged list with `realmListOf()`
or pass elements to the list using
`list.add()`,
`list.addAll()`,
or `list.set()`.
The list is unmanaged until you copy it to the realm.

In the following example, we instantiate a new `Frog` object with
initial values for several `RealmList` properties:

```kotlin
realm.write {
    // Instantiate a new unmanaged Frog object with a RealmList property
    val frog = Frog().apply {
        name = "Kermit"
        // Set values for each unmanaged list
        favoritePonds.addAll(realmListOf(
            Pond().apply { name = "Picnic Pond" },
            Pond().apply { name = "Big Pond" }
        ))
        favoriteForests.add(EmbeddedForest().apply { name = "Hundred Acre Wood" })
        favoriteWeather = realmListOf("rain", "snow")
    }
    // Copy all objects to the realm to return managed instances
    copyToRealm(frog)
}
```

### Create a RealmSet Property
To create a new object instance with a
`RealmSet`
property, instantiate an object and pass any values of a
supported type to the `RealmSet` property. For a list of valid types
that `RealmSet` can hold, refer to RealmSet.

You can instantiate an unmanaged set with
`realmSetOf()`
or pass elements to the set using
`set.add()`
or
`set.addAll()`.
The set is unmanaged until you copy it to the realm.

In the following example, we instantiate a new `Frog` object with
initial values for `favoriteSnacks` and `favoriteWeather` set
properties:

```kotlin
realm.write {
    // Instantiate a new unmanaged Frog object with RealmSet properties
    val frog = Frog().apply {
        name = "Kermit"
        // Set initial values to each unmanaged set
        favoriteSnacks.addAll(setOf(
            Snack().apply { name = "flies" },
            Snack().apply { name = "crickets" },
            Snack().apply { name = "worms" }
        ))
        favoriteWeather.add("rain")
    }
    // Copy all objects to the realm to return managed instances
    copyToRealm(frog)
}
```

### Create a Dictionary Property
To create a new object instance with a
`RealmDictionary`
property, instantiate an object and pass any key-value pairs of a
supported type to the `RealmDictionary` property.
`RealmDictionary` only accepts a `String` key, but values may be
non-string types. For a list of valid types, refer to
RealmMap/RealmDictionary.

You can instantiate an unmanaged dictionary with
`realmDictionaryOf()`
or
`realmDictionaryEntryOf()`.
Or you can pass key-values using
`put()`
or
`putAll()`.
The dictionary is unmanaged until you copy it to the realm.

In the following example, we instantiate a new `Frog` object with
initial key-values for several dictionary properties:

```kotlin
realm.write {
    val frog = Frog().apply {
        name = "Kermit"
        // Set initial key-values to each unmanaged dictionary
        favoriteFriendsByPond = realmDictionaryOf(
            "Picnic Pond" to Frog().apply { name = "Froggy Jay" },
            "Big Pond" to Frog().apply { name = "Mr. Toad" }
        )
        favoriteTreesInForest["Maple"] = EmbeddedForest().apply {
            name = "Hundred Acre Wood"
        }
        favoritePondsByForest.putAll(
            mapOf(
                "Silver Pond" to "Big Forest",
                "Big Lake" to "Elm Wood",
                "Trout Pond" to "Sunny Wood"
            )
        )
    }
    // Copy all objects to the realm to return managed instances
    copyToRealm(frog)
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

## Create a Relationship Property
Depending on how you define your object type, you might have properties
that reference another Realm object. This can be a to-one, to-many, or
inverse relationship.
For more information on defining relationships in your object model,
refer to Define a Relationship.

You can also embed one Realm object directly within another to create
a nested data structure with an `EmbeddedRealmObject` type.
To create a relationship with an embedded object, refer to the
Create an Embedded Object section on this page.

### Create a To-One Relationship
To create a new object instance with a
to-one relationship
property, instantiate both objects and pass the referenced object to the
relationship property.

In the following example, we instantiate a new `Frog` object with a
`favoritePond` property that references a `Pond` object and a
`bestFriend` property that references another `Frog` object:

```kotlin
realm.write {
    // Instantiate a new unmanaged Frog object with to-one
    // relationship with a Realm object
    val frog = Frog().apply {
        name = "Kermit"
        age = 12
        favoritePond = Pond().apply { name = "Picnic Pond" }
        bestFriend = Frog().apply { name = "Froggy Jay" }
    }
    // Copy all objects to the realm to return managed instances
    copyToRealm(frog)
}
```

### Create a To-Many Relationship
To create a new object instance with a
to-many relationship
property, instantiate all objects and pass any referenced objects to the
relationship collection property.

In the following example, we instantiate a new `Forest` object with a
`frogsThatLiveHere` property that references a set of `Frog`
objects and a `nearByPonds` property that references a list of
`Pond` objects:

```kotlin
realm.write {
    // Instantiate a new unmanaged Forest object with to-many
    // relationship with multiple Realm objects
    val forest = Forest().apply {
        name = "Froggy Forest"
        frogsThatLiveHere = realmSetOf(
            Frog().apply { name = "Kermit" },
            Frog().apply { name = "Froggy Jay" }
        )
        nearbyPonds = realmListOf(
            Pond().apply { name = "Small Picnic Pond" },
            Pond().apply { name = "Big Pond" }
        )
    }
    // Copy all objects to the realm to return managed instances
    copyToRealm(forest)
}
```

### Create an Inverse Relationship
To create a new object instance with an
inverse relationship
property, instantiate the parent object and pass any referenced child
objects to the backlink collection property.

In the following example, we instantiate a new `User` object with a
backlinks `posts` property that references a list of `Post` objects:

```kotlin
realm.write {
    // Instantiate a new unmanaged User object with to-many
    // relationship with multiple Realm objects
    val post1 = Post().apply {
        title = "Forest Life"
    }
    val post2 = Post().apply {
        title = "Top Ponds of the Year!"
    }

    val user = User().apply {
        name = "Kermit"
        posts = realmListOf(post1, post2)
    }
    // Copy all objects to the realm to return managed instances
    copyToRealm(user)
}
```

After you create the object, you can access the backlinks collection
property to get the child objects, but you *cannot* directly modify
the backlink itself. For more information, refer to
Update an Inverse Relationship.

## Create an Unmanaged Copy of a Realm Object or Collection
You can create an unmanaged copy of a managed object or collection
by passing it to
`copyFromRealm()`.
This method returns an unmanaged, in-memory
copy of the object or collection. For collections, this is a deep copy
that includes all referenced objects up to the specified `depth`.

In the following example, we create an unmanaged copy of an existing
managed `Pond` object that contains a list of two `Frog` objects.
After copying the object from the realm, we confirm that the copy is
unmanaged and contains both referenced `Frog` objects:

```kotlin
realm.writeBlocking {
    // Fetch the managed object you want to copy
    val managedPond = query<Pond>("name == $0", "Big Pond").find().first()
    assertTrue(managedPond.isManaged())

    // Create an unmanaged copy of the object
    val unmanagedPond = copyFromRealm(managedPond)
    assertFalse(unmanagedPond.isManaged())
    Log.v("Unmanaged pond name: ${unmanagedPond.name}")

    // Confirm the unmanaged copy contains all elements
    // in the copied object's RealmList
    val unmanagedFrogs = unmanagedPond.frogsThatLiveHere
    assertFalse(unmanagedFrogs[0].isManaged())
    assertFalse(unmanagedFrogs[1].isManaged())
    assertEquals(2, unmanagedFrogs.size)
    Log.v("Unmanaged frogs: ${unmanagedFrogs[0].name}, ${unmanagedFrogs[1].name}")
}
```

Outputs:
```console
Unmanaged pond name: Big Pond
Unmanaged frogs: Kermit, Froggy Jay
```
