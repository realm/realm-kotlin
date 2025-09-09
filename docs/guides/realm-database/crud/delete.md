# Delete Realm Objects - Kotlin SDK
This page describes how to delete objects from a local database
using the Kotlin SDK.

You can choose to delete a single object, multiple objects, or all
objects from the realm. After you delete an object, you can no longer access or
modify it. If you try to use a deleted object, Realm throws an error.

Deleting objects from a realm *does not* delete the realm file
or affect the realm schema. It only deletes the object instance from the
realm. If you want to delete the realm file itself,
refer to Delete a Realm - Kotlin SDK.

## Delete Operations
All operations that modify a realm - including delete operations -
must be performed inside of a **write transaction**. Write transactions
are passed to the realm's
`write()`
or
`writeBlocking()`
method. Within this callback, you can access a
`MutableRealm`
instance and then delete objects within the realm. For more information on
write transactions and how Realm handles them, refer to
Write Transactions.

You can only delete live objects, which are only accessible inside of a
write transaction. You can convert a frozen object to a
live object in a transaction with `mutableRealm.findLatest()`.

> **EXAMPLE:**
> ```kotlin
> val frozenFrog = realm.query<Frog>("name == $0", "Kermit").find().firstOrNull()
>
> // Open a write transaction
> realm.writeBlocking {
>     // Get the live frog object with findLatest(), then delete it
>     if (frozenFrog != null) {
>         findLatest(frozenFrog)
>             ?.also { delete(it) }
>     }
> }
>
> ```
>

### Related Objects and References
When you delete an object that has a relationship
property with another object, Realm *does not* automatically
delete the instance of the related object. Instead, Realm only
deletes the reference to the other object. The referenced object remains in the
realm, but it can no longer be queried through the parent property.

The only exception is if the related object is
embedded. When you delete an object that
has a relationship with an `EmbeddedRealmObject`, Realm automatically
deletes the embedded object in a **cascading delete**. For more information,
refer to the Delete an Embedded Object section on this page.

#### Chaining Deletes with Related Realm Objects
If you want to delete any related objects when you delete a parent object,
we recommend performing a **chaining delete**. A chaining delete consists
of manually deleting dependent objects by iterating through the
dependencies and deleting them before deleting the parent object.
For more information on chaining deletes, refer to the
Delete an Object and Its Related Objects section on this page.

If you do not delete the related objects yourself, they remain
orphaned in your realm. Whether or not this is a problem depends on
your application's needs.

## Delete Realm Objects
To delete specific objects from a realm:

1. Open a write transaction with `realm.write()` or
`realm.writeBlocking()`.
2. Get the live objects by querying the transaction's mutable realm
for the objects that you want to delete using
`query()`: Specify the object type as a type parameter passed to `query()`.(Optional) Filter the set of returned objects by specifying a query.
If you don't include a query filter, you return all objects of the
specified type. For more information on querying with the Kotlin SDK, refer to Read Realm Objects - Kotlin SDK. You can only delete live objects. If your query occurs outside of the
write transaction, you must convert the frozen objects
to live objects in the transaction with
`mutableRealm.findLatest()`.
3. Pass the set of `RealmResults`
returned by the query to
`mutableRealm.delete()`.
4. The specified objects are deleted from the realm and can no longer be accessed
or modified. If you try to use a deleted object, Realm throws an error. If any deleted objects had a relationship with another object, Realm
only deletes the reference to the other object. The referenced object
remains in the realm, but it can no longer be queried through the deleted
parent property. Refer to the Delete an Object and Its Related Objects section
for more information.

> **TIP:**
> You can check whether an object is still valid to use by calling
`isValid()`.
Deleted objects return `false`.
>

### Delete a Single Object
To delete a single `RealmObject` object,
query for the object type using a filter
that returns the specific object that you want to delete.

> **TIP:**
> We recommend filtering with unique identifying information such as
a primary key value to ensure your query returns the correct object.
>

In the following example, we query for a `Frog` object with a specific
primary key, and then pass the returned object to `mutableRealm.delete()` to
delete it from the realm:

```kotlin
// Open a write transaction
realm.write {
    // Query the Frog type and filter by primary key value
    val frogToDelete: Frog = query<Frog>("_id == $0", PRIMARY_KEY_VALUE).find().first()
    // Pass the query results to delete()
    delete(frogToDelete)
}

```

### Delete Multiple Objects
To delete multiple objects at the same time, pass the object type to
`query()` and specify a query that returns all objects that you want
to delete.

In the following example, we query for the first three `Frog` objects whose
`species` is "bullfrog", and then pass the results to
`mutableRealm.delete()` to delete them from the realm:

```kotlin
// Open a write transaction
realm.write {
    // Query by species and limit to 3 results
    val bullfrogsToDelete: RealmResults<Frog> = query<Frog>("species == 'bullfrog' LIMIT(3)").find()
    // Pass the query results to delete()
    delete(bullfrogsToDelete)
}

```

### Delete All Objects of a Type
To delete all objects of a specific type from a realm at the same time, pass the
object type to `query()` and leave the query filter empty to return all
objects of that type.

In the following example, we query for all `Frog` objects, and then pass
the results to `mutableRealm.delete()` to delete them all from the realm:

```kotlin
// Open a write transaction
realm.write {
    // Query Frog type with no filter to return all frog objects
    val frogsLeftInTheRealm = query<Frog>().find()
    // Pass the query results to delete()
    delete(frogsLeftInTheRealm)
}

```

### Delete All Objects in a Realm
The Kotlin SDK lets you delete all managed objects of all types, which is useful
for quickly clearing out your realm while prototyping. This does not affect
the realm schema or any objects that are not managed by the realm.

To delete *all* objects from the realm at the same time, call
`mutableRealm.deleteAll()`. This deletes all objects of all types.

In the following example, we delete all objects from the realm with `deleteAll()`:

```kotlin
// Open a write transaction
realm.write {
    // Delete all objects from the realm
    deleteAll()
}

```

> **TIP:**
> The `deleteAll()` method is useful for quickly clearing out
your realm during development. For example, instead of writing
a migration to update objects to a new schema, it may be faster to
delete all, and then re-generate the objects with the app itself.
>

## Delete Related Objects
Deleting a parent object *does not* automatically delete any objects that are
related to it unless the related object is embedded.
Instead, Realm only deletes the reference to the related object.

In the following example, we have a `Frog` object with a list of
`Pond` objects. After we delete the `Frog` object, we confirm that all
`Pond` objects still remain in the realm:

```kotlin
// Open a write transaction
realm.write {
    // Query for the parent frog object with ponds
    val parentObject = query<Frog>("_id == $0", PRIMARY_KEY_VALUE).find().first()
    assertEquals(2, parentObject.favoritePonds.size)

    // Delete the frog and all references to ponds
    delete(parentObject)

    // Confirm pond objects are still in the realm
    val ponds = query<Pond>().find()
    assertEquals(2, ponds.size)
}

```

### Delete an Object and Its Related Objects
To delete related objects when you delete a parent object, you must manually
delete the related objects yourself. We recommend chaining deletes:
first query for the parent object that you want to delete, then iterate
through the parent object's relationships and delete each related object.
Finally, delete the parent object itself.

In the following example, we query for a `Frog` object named "Kermit", then
iterate through the object's `favoritePonds` property and delete
each `Pond` object. Then, we delete the `Frog` object itself:

```kotlin
realm.write {
    // Query for the parent frog object with ponds
    val frog = query<Frog>("name == $0", "Kermit").find().first()
    val ponds = frog.favoritePonds
    // Iterate over the list and delete each pond object
    if (ponds.isNotEmpty()) {
        ponds.forEach { pond ->
            delete(pond)
        }
    }
    // Delete the parent frog object
    val frogToDelete = findLatest(frog)
    if (frogToDelete != null) {
        delete(frogToDelete)
    }
}

```

### Delete an Embedded Object
> **WARNING:**
> When you delete a Realm object, Realm automatically deletes any
embedded objects referenced by that object.
If you want the referenced objects to persist after the deletion of the
parent object, use a regular Realm object with a to-one relationship instead.
>

You can delete an embedded object through the parent object in a cascading
delete or by deleting the embedded object directly.

- To delete the embedded object through the parent object, fetch and delete
the parent object. Realm automatically deletes all of its embedded objects
from the realm.
- To delete an embedded object instance directly: Fetch and delete a specific embedded object.Clear the parent's reference to the embedded object, which also
deletes the embedded object instance.

In the following example, we have a `Business` object with a list of
embedded `EmbeddedAddress` objects. We query for and delete the `Business` object,
which automatically deletes all of its embedded `EmbeddedAddress` objects:

```kotlin
// Delete the parent object
realm.write {
    val businessToDelete = query<Business>("name == $0", "Big Frog Corp.").find().first()
    // Delete the parent object (deletes all embedded objects)
    delete(businessToDelete)
}

```

In the following example, we have `Contact` objects with embedded
`EmbeddedAddress` objects. We delete an `EmbeddedAddress` object directly and delete
another through the parent object:

```kotlin
// Delete an embedded object directly
realm.write {
    val addressToDelete = query<EmbeddedAddress>("street == $0", "456 Lily Pad Ln").find().first()
    // Delete the embedded object (nullifies the parent property)
    delete(addressToDelete)
}
// Delete an embedded object through the parent
realm.write {
    val propertyToClear = query<Contact>("name == $0", "Kermit").find().first()
    // Clear the parent property (deletes the embedded object instance)
    propertyToClear.address = null
}

```

> **TIP:**
> You can get the unique parent of an embedded object using
`parent()`.
>

## Remove Elements from Collections
Realm collection instances that contain objects only store
references to those objects. You can remove one or more referenced
objects from a collection without deleting the objects themselves.
The objects that you remove from a collection remain in the realm
until you manually delete them. Alternatively, deleting a Realm object from a
realm also deletes that object from any collection instances that contain
the object.

### Remove Elements from a RealmList
You can remove one or more elements in a single transaction from a
`RealmList`:

- To remove one element from the list, pass the element to
`list.remove()`.
- To remove one element at a specified index in the list, pass the index to
`list.removeAt()`.
- To remove multiple elements from the list, pass the elements to
`list.removeAll()`.

You can also remove *all* list elements at once by calling
`list.clear()`.

In the following example, we have a `Forest` object with a list of
`Pond` objects. We remove the list elements in a series of operations until the
list is empty:

```kotlin
// Open a write transaction
realm.write {
    // Query for the parent forest object
    val forest = query<Forest>("name == $0", "Hundred Acre Wood").find().first()
    val forestPonds = forest.nearbyPonds
    assertEquals(5, forestPonds.size)

    // Remove the first pond in the list
    val removeFirstPond = forestPonds.first()
    forestPonds.remove(removeFirstPond)
    assertEquals(4, forestPonds.size)

    // Remove the pond at index 2 in the list
    forestPonds.removeAt(2)
    assertEquals(3, forestPonds.size)

    // Remove the remaining three ponds in the list
    forestPonds.removeAll(forestPonds)
    assertEquals(0, forestPonds.size)
}

```

In the following example, we have a `Forest` object with a list of
`Pond` objects. We remove all list elements with the `list.clear()` method:

```kotlin
// Open a write transaction
realm.write {
    val forest = query<Forest>("name == $0", "Hundred Acre Wood").find().first()
    val forestPonds = forest.nearbyPonds
    assertEquals(5, forestPonds.size)

    // Clear all ponds from the list
    forestPonds.clear()
    assertEquals(0, forestPonds.size)
}

```

### Remove Elements from a RealmSet
You can remove one or more elements in a single transaction from a
`RealmSet`:

- To remove one element from the set, pass the element
you want to delete to
`set.remove()`.
- To remove multiple elements from the set, pass the
elements you want to delete to
`set.removeAll()`.

You can also remove *all* set elements at once by calling
`set.clear()`.

In the following example, we have a `Frog` object with a set of
`Snack` objects. We remove the set elements in a series of operations until the
set is empty:

```kotlin
// Open a write transaction
realm.write {
    // Query for the parent frog object
    val myFrog = query<RealmSet_Frog>("name == $0", "Kermit").find().first()
    val snackSet = myFrog.favoriteSnacks
    assertEquals(3, snackSet.size)

    // Remove one snack from the set
    snackSet.remove(snackSet.first { it.name == "Flies" })
    assertEquals(2, snackSet.size)

    // Remove the remaining two snacks from the set
    val allSnacks = findLatest(myFrog)!!.favoriteSnacks
    snackSet.removeAll(allSnacks)
    assertEquals(0, snackSet.size)
}

```

In the following example, we have a `Frog` object with a set of
`Snack` objects. We remove all set elements with the `set.clear()` method:

```kotlin
realm.write {
    val myFrog = realm.query<RealmSet_Frog>("name == $0", "Kermit").find().first()
    val snackSet = findLatest(myFrog)!!.favoriteSnacks
    assertEquals(3, snackSet.size)

    // Clear all snacks from the set
    snackSet.clear()
    assertEquals(0, snackSet.size)
}

```

### Remove Dictionary Keys/Values
You can remove
`RealmDictionary`
entries in a few ways:

- To remove the value but keep the key, set the key to `null` (the
dictionary's value must be nullable)
- To remove the key and the value, pass the key to
`remove()`

You can also remove *all* keys and values by calling
`clear()`.

In the following example, we have a `Frog` object with a dictionary of
`String` values. We remove the dictionary elements in a series of operations
until the dictionary is empty:

```kotlin
// Find frogs who have forests with favorite ponds
val thisFrog = realm.query<Frog>("favoritePondsByForest.@count > 1").find().first()
// Set an optional value for a key to null if the key exists
if (thisFrog.favoritePondsByForest.containsKey("Hundred Acre Wood")) {
    realm.write {
        val mutableFrog = findLatest(thisFrog)
        if (mutableFrog != null) {
            mutableFrog.favoritePondsByForest["Hundred Acre Wood"] = null
        }
    }
}
realm.write {
    // Remove a key and its value
    findLatest(thisFrog)?.favoritePondsByForest?.remove("Lothlorien")
    // Remove all keys and values
    findLatest(thisFrog)?.favoritePondsByForest?.clear()
    assertTrue(thisFrogUpdated.favoritePondsByForest.isEmpty())
}

```

## Delete RealmAny (Mixed) Property Value
Although `RealmAny` instances *cannot* store null values, you can
delete a `RealmAny` property value by assigning `null` directly
to the property.
For more information on the `RealmAny` data type, refer to RealmAny (Mixed).

In the following example, we have a `Frog` object with a list of
`RealmAny` properties, and we clear the first `RealmAny` property value:

```kotlin
realm.write {
    val frog = query<Frog>().find().first()
    frog.favoriteThings[0] = null
}

```
