# Update Realm Objects - Kotlin SDK
This page describes how to update an existing Realm object in a
realm using the Kotlin SDK. To learn more about creating objects in a
realm, refer to Create Realm Objects - Kotlin SDK.

Realm supports update and upsert operations on Realm objects and embedded
objects. An **upsert operation** either inserts a new instance of an object
or updates an existing object that meets certain criteria. For more information,
refer to the Upsert a Realm Object section on this page.

## Update Operations
All operations that modify a realm - including update and upsert operations -
must be performed inside of a write transaction. Write transactions
are passed to the realm's
`write`
or
`writeBlocking`
method. Within this callback, you can access a
`MutableRealm`
instance, and then update objects within the realm. For more information on
write transactions and how Realm handles them, refer to
Write Transactions.

Additionally, you can only modify live objects, which are only accessible
inside of a write transaction. You can convert a frozen object to a
live object in a transaction with `mutableRealm.findLatest()`.

> **EXAMPLE:**
> ```kotlin
> val frozenFrog = realm.query<Frog>().find().first()
>
> // Open a write transaction
> realm.write {
>     // Get the live frog object with findLatest(), then update it
>     findLatest(frozenFrog)?.let { liveFrog ->
>         liveFrog.name = "Kermit"
>         liveFrog.age -= 1
>     }
> }
>
> ```
>

## Update an Existing Object
To modify the properties of a Realm object or embedded object stored
within a realm:

1. Open a write transaction with `realm.write` or
`realm.writeBlocking`.
2. Get the live objects by querying the transaction's mutable realm
for the objects that you want to modify using
`query()`: Specify the object type as a type parameter passed to `query()`.(Optional) Filter the set of returned objects by specifying a query.
If you don't include a query filter, you return all objects of the
specified type. For more information on querying with the Kotlin SDK,
refer to Read Realm Objects - Kotlin SDK. You can only modify live objects. If your query occurs outside of the
write transaction, you must convert the frozen objects
to live objects in the transaction with
`mutableRealm.findLatest()`.
3. Modify the object within the write transaction. You can use an [apply block](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/apply.html)
to configure multiple properties at once. All changes are
automatically persisted to the realm when Realm commits the write
transaction.

> **NOTE:**
> Because Realm operates on fields as a whole, it's not possible
to directly update individual elements of strings or byte arrays. Instead,
you must read the whole field, make your modification to individual
elements, and then write the entire field back again in a transaction block.
>

### Update a Realm Object
To update a Realm object, query for the type using a filter that returns the
specific object that you want to update. Then, modify the object properties.

> **TIP:**
> We recommend filtering with unique identifying information such as
a primary key value
to ensure your query returns the correct object.
>

In the following example, we query a `Frog` object by primary key,
then update the `name` and `age` properties:

```kotlin
// Query and update a realm object in a single write transaction
realm.write {
    val liveFrog = query<Frog>("_id == $0", PRIMARY_KEY_VALUE).find().first()
    liveFrog.name = "Michigan J. Frog"
    liveFrog.age += 1
}

```

### Update an Embedded Object
You can update an embedded object by modifying individual properties
or by overwriting the entire embedded object.

> **TIP:**
> You can use dot notation to access embedded object
properties as if it were in a regular, nested object. For more information, refer to
Filter By Embedded Object Property.
>

To update one or more properties in an embedded object,
fetch the parent or embedded object and reassign the
embedded object properties in a write transaction.

In the following example, we have a `Contact` object that contains an
embedded `EmbeddedAddress` object. We update the embedded object properties
in several operations:

```kotlin
// Modify embedded object properties in a write transaction
realm.write {
    // Fetch the objects
    val addressToUpdate = findLatest(address) ?: error("Cannot find latest version of embedded object")
    val contactToUpdate = findLatest(contact) ?: error("Cannot find latest version of parent object")

    // Update a single embedded object property directly
    addressToUpdate.street = "100 10th St N"

    // Update multiple properties
    addressToUpdate.apply {
        street = "202 Coconut Court"
        city = "Los Angeles"
        state = "CA"
        postalCode = "90210"
    }

    // Update property through the parent object
    contactToUpdate.address?.state = "NY"
}

```

To overwrite an embedded object completely, assign a new embedded object
instance to the parent property in a write transaction. This deletes the
existing embedded object.

In the following example,  we have a `Contact` object that contains an
embedded `EmbeddedAddress` object. We define a new `EmbeddedAddress` object and
assign it to the parent property:

```kotlin
realm.write {
    val parentObject = query<Contact>("_id == $0", PRIMARY_KEY_VALUE).find().first()

    val newAddress = EmbeddedAddress().apply {
        propertyOwner = Contact().apply { name = "Michigan J. Frog" }
        street = "456 Lily Pad Ln"
        country = EmbeddedCountry().apply { name = "Canada" }
    }
    // Overwrite the embedded object with the new instance (deletes the existing object)
    parentObject.address = newAddress
}

```

## Update Multiple Objects
You can also update multiple objects in a realm:

1. Query a realm for a collection of objects
with `realm.query()`.
2. Open a write transaction with `realm.write()` or
`realm.writeBlocking()`.
3. Update elements of the set of `RealmResults`
returned by the query.

```kotlin
val tadpoles = realm.query<Frog>("age <= $0", 2)
for (tadpole in tadpoles.find()) {
    realm.write {
        findLatest(tadpole)?.name = tadpole.name + " Jr."
    }
}

```

## Update Realm Properties
Depending on how you define your object type, you might have properties
that are special Realm-specific types.

### Update a MutableRealmInt (Counter) Property
You can use the following Realm API functions to update a `MutableRealmInt`
property value:

- `increment()`
- `decrement()`
- `set()`

These methods return the `MutableRealmInt` property with a mutated value.

> **TIP:**
> The `set()` operator overwrites any prior calls to
`increment()` or `decrement()`. We do not recommend
mixing `set()` with `increment()` or `decrement()`,
unless fuzzy counting is acceptable for your use case.
>

In addition to the Realm API functions, you can also use
the following set of operators and infix functions similar
to those provided by Kotlin's standard library for `Long`:

- Unary prefix operators: `unaryPlus`, `unaryMinus`
- Increment and decrement operators: `inc`, `dec` (not to be confused
with `increment` and `decrement`)
- Arithmetic operators: `plus`, `minus`, `times`, `div`, `rem`
- Equality operators: `equals`
- Comparison operators: `compareTo`
- Bitwise functions: `shl`, `shr`, `ushr`, `and`, `or`, `xor`, `inv`

However, these operators and infix functions *do not* mutate
the instance on which they are called. Instead, they return
a new unmanaged instance with the result of the operation.

> **IMPORTANT:**
> The only operations that result in a mutated value are
the Realm API functions:
`increment`, `decrement`, and `set`.
All other operations (including `inc` and `dec`)
return an unmanaged instance with a new value.
>

In the following example, we update a `MutableRealmInt`
property with `increment()`, `decrement()`, and
`set()` operations:

```kotlin
// Open a write transaction
realm.write {
    // Get the live object
    val frog = query<Frog>("_id == $0", PRIMARY_KEY_VALUE).find().first()
    val counter: MutableRealmInt? = frog.fliesEaten
    counter?.get() // 1

    // Increment the value of the MutableRealmInt property
    // ** Note use of decrement() with negative value **
    counter?.increment(0) // 1
    counter?.increment(5) // 6
    counter?.decrement(-2) // 8

    // Decrement the value of the MutableRealmInt property
    // ** Note use of increment() with negative value **
    counter?.decrement(0) // 8
    counter?.decrement(2) // 6
    counter?.increment(-1) // 5

    // Set the value of the MutableRealmInt property
    // ** Use set() with caution **
    counter?.set(0)// 0
}

```

### Update a RealmAny (Mixed) Property
`RealmAny`
properties are immutable. To update a `RealmAny` value, you must create
a new instance of the property with the desired value and type. For more
information on adding `RealmAny` properties, refer to Create a RealmAny (Mixed) Property.

Additionally, you *must* know the stored type to extract the value from a
`RealmAny` property. If you call a getter method with the wrong type,
Realm throws an exception.

> **TIP:**
> Because you must know the stored type to extract its value, we
recommend using a `when` expression to handle the
`RealmAny` type and its possible inner value class.
>
> ```kotlin
> val favoriteThings = frog.favoriteThings
> when (favoriteThings.type) {
>    INT -> rating(favoriteThings.asInteger())
>    STRING -> description(favoriteThings.asString())
>    BYTE_ARRAY -> image(favoriteThings.asByteArray())
>    // Handle other possible types...
>    else -> {
>       // Debug or a perform default action
>       Log.d("ExampleHandler", "Unhandled type: ${favoriteThings.type}")
>    }
> }
> ```
>

In the following example, we update a `Frog` object that
contains a list of `RealmAny` properties by creating new
instances of each list element:

```kotlin
// Find favoriteThing that is an Int
// Convert the value to Double and update the favoriteThing property
realm.write {
val kermit = query<Frog>().find().first()
val realmAny: RealmList<RealmAny?> = kermit.favoriteThings

    for (i in realmAny.indices) {
        val thing = realmAny[i]
        if (thing?.type == RealmAny.Type.INT) {
            val intValue = thing.asInt()
            val doubleValue = intValue.toDouble()
               realmAny[i] = RealmAny.create(doubleValue)
        }
    }
}

// Overwrite all existing favoriteThing properties
// ** Null clears the property value **
realm.write {
    val frog = query<Frog>().find().first()
    val realmAny: RealmList<RealmAny?> = frog.favoriteThings

    realmAny[0] = RealmAny.create("sunshine")
    realmAny[1] = RealmAny.create(Frog().apply { name = "Kermit Sr." })
    realmAny[2] = null
}

```

> **NOTE:**
> You can assign `null` directly to a `RealmAny` property to
remove the current value.
>

## Update Collection Properties
Depending on how you define your object type, you might have properties
that are defined as one of the following
supported Collection Types:

- `RealmList`
- `RealmSet`
- `RealmDictionary`

Collections are mutable and are backed by their corresponding built-in Kotlin classes.
You can add and remove elements in a collection within a write transaction.

> **TIP:**
> You can register a notification handler to
listen for changes. For more information, refer to
Register a Collection Change Listener.
>

### Update a RealmList
You can update elements in a
`RealmList`
as you would a
[Kotlin MutableList](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-mutable-list/).

In the following example, we update `RealmList` elements
for an existing `Frog` object:

```kotlin
realm.write {
    // Get the live object
    val realmList = query<Frog>("name = $0", "Kermit").first().find()!!.favoritePonds
    realmList[0].name = "Picnic Pond"
    realmList.set(1, Pond().apply { name = "Big Pond" })
}

```

For more information on adding and removing `RealmList` elements,
refer to Create a RealmList and Remove Elements from a RealmList.

### Update a RealmSet
You can add, update, and remove elements in a
`RealmSet`
as you would a
[Kotlin MutableSet](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-mutable-set/).

In the following example, we iterate through and update `RealmSet` elements
for existing `Frog` objects:

```kotlin
// Find a frog in the realm
val kermit = realm.query<Frog>("name = $0", "Kermit").find().first()
val realmSet = kermit.favoriteSnacks
// Update the name of each snack in the set
for (snack in realmSet) {
    realm.write {
        findLatest(snack)?.name = snack.name.uppercase()
    }
}

realm.write {
    // Find all frogs who like rain
    val frogsWhoLikeRain = realm.query<Frog>("favoriteWeather CONTAINS $0", "rain").find()
    // Add thunderstorms to their favoriteWeather set
    for (frog in frogsWhoLikeRain) {
        val latestFrog = findLatest(frog)
        latestFrog?.favoriteWeather?.add("thunderstorms")
    }
}

```

For more information on adding and removing `RealmSet` elements,
refer to Create a RealmSet Property and Remove Elements from a RealmSet.

### Update a Dictionary
You can update keys and values in a
`RealmDictionary`
as you would a
[Kotlin MutableMap](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-mutable-map/).

In the following example, we update a `RealmDictionary`

```kotlin
// Find frogs who have forests with favorite ponds
val thisFrog = realm.query<RealmDictionary_Frog>("favoritePondsByForest.@count > 1").find().first()
// Update the value for a key if it exists
if (thisFrog.favoritePondsByForest.containsKey("Hundred Acre Wood")) {
    realm.write {
        findLatest(thisFrog)?.favoritePondsByForest?.set("Lothlorien", "Lily Pad Pond")
    }
}
// Add a new key-value pair
realm.write {
    findLatest(thisFrog)?.favoritePondsByForest?.put("Sherwood Forest", "Miller Pond")
}

```

For more information on adding and removing `RealmDictionary` entries,
refer to Create a Dictionary Property and
Remove Dictionary Keys/Values.

## Update Relationships
Depending on how you define your object type, you might have properties
that reference another Realm object. This can be a to-one, to-many, or
inverse relationship.

You can also embed one Realm object directly within another to create
a nested data structure with an `EmbeddedRealmObject` type.
For more information, refer to Update an Embedded Object section
on this page.

### Update a To-One Relationship
You can update relationships between objects in a realm by modifying
the properties that define the relationship the same way you would
update any other property.

In the following example, we have a `Frog` object with a `favoritePond`
property that references a single `Pond` object and a
`bestFriend` property that references another `Frog` object:

```kotlin
realm.write {
    val kermit = query<Frog>("name == $0", "Kermit").find().first()
    // Update a property on the related object
    kermit.favoritePond?.name = "Big Pond"

    // Assign a new related object
    val newBestFriend = Frog().apply { name = "Froggy Jr." }
    kermit.bestFriend = newBestFriend
}

```

### Update a To-Many Relationship
You can update to-many relationships the same way you would update any other
collection. Refer to the Update Collection Properties section on this page.

In the following example, we have a `Forest` object with a
`frogsThatLiveHere` property that references a set of `Frog`
objects and a `nearByPonds` property that references a list of
`Pond` objects:

```kotlin
realm.write {
    val forest = query<Forest>().find().first()
    // Update a property on a related object in the set
    forest.frogsThatLiveHere.first().name = "Kermit Sr."
    // Add a new related object to the list
    forest.frogsThatLiveHere.add(Frog().apply { name = "Froggy Jr." })
}

```

### Update an Inverse Relationship
You can access and update objects in an inverse relationship. However, you *cannot*
directly modify the backlink collection itself. Instead, Realm automatically
updates the implicit relationship when you modify any related objects.
For more information, refer to Define an Inverse Relationship.

In the following example, we have a parent `User` object with a
backlinks `posts` property that references a list of `Post` child objects.
We update properties on the parent and child objects through the
backlink:

```kotlin
realm.write {
    // Update child objects through the parent
    val parent = query<User>().find().first()
    parent.posts[0].title = "Forest Life Vol. 2"
    parent.posts.add(Post().apply { title = "Forest Life Vol. 3" })

    // Update child objects (Realm automatically updates the backlink collection)
    val child = query<Post>("title == $0", "Top Ponds of the Year!").find().first()
    child.title = "Top Ponds of the Year! Vol. 2"
    assertEquals("Top Ponds of the Year! Vol. 2", parent.posts[1].title)
    // Update the parent object through the child
    child.user[0].name = "Kermit Sr."

    // ** You CANNOT directly modify the backlink collection **
    val readOnlyBacklink: RealmResults<User> = child.user
}

```

## Upsert a Realm Object
To upsert an object into a realm, insert an object with a primary key
using
`copyToRealm()`,
as you would when creating a new object, and pass an
`UpdatePolicy`
parameter to specify how the SDK handles existing objects with the same
primary key:

- `UpdatePolicy.ALL`: Update all properties on any existing objects
identified with the same primary key.
- `UpdatePolicy.ERROR` (default): Disallow updating existing objects and instead
throw an exception if an object already exists with the same primary key. If
you do not specify an update policy, Realm uses this policy by default.

The following can occur depending on the update policy:

- If no object exists that matches the primary key, the SDK inserts the new object.
- If an object with the same primary key already exists, the SDK either: Updates all properties on any existing objects identified with the
same primary key. Note that properties are marked as updated in change
listeners, even if the property was updated to the same value.Throws an exception indicating that an object already exists in the realm.

In the following example, we attempt to insert a `Frog` object with a
primary key that already exists in the realm with `UpdatePolicy.ALL`
and confirm the object is successfully upserted:

```kotlin
realm.write {
    val existingFrog = query<Frog>("_id == $0", PRIMARY_KEY_VALUE).find().first()
    assertEquals(existingFrog.name, "Kermit")

    // Use copyToRealm() to insert the object with the primary key
    // ** UpdatePolicy determines whether to update or throw an error if object already exists**
    copyToRealm(Frog().apply {
        _id = PRIMARY_KEY_VALUE
        name = "Wirt"
        age = 4
        species = "Greyfrog"
        owner = "L'oric"
    }, UpdatePolicy.ALL)

    val upsertFrog = query<Frog>("_id == $0", PRIMARY_KEY_VALUE).find().first()
    assertEquals(upsertFrog.name, "Wirt")
}

```
