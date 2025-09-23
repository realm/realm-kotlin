# Quick Start - Kotlin SDK
This page contains information to quickly use Realm with the Kotlin
SDK.

Before you begin, ensure you have installed
the Kotlin SDK for your platform.

- Install for Kotlin Multiplatform (KMP)
- Install for Android

> **NOTE:**
> If you're running this project in a fresh Kotlin Multiplatform (KMP) template project, you can
> copy and paste the following snippets into the `Greeting.greeting()` method in the
> `commonMain` module.
>

## Define Your Object Model
Your application's **data model** defines the structure of data
stored within Realm.
You can define your application's data model via Kotlin
classes in your application code with
Realm Object Models.

To define your application's data model, add a class definition to your
application code. The example below illustrates the creation of an 'Item' model
that represents Todo items in a Todo list app.

```kotlin
class Item() : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var isComplete: Boolean = false
    var summary: String = ""
    var owner_id: String = ""
    constructor(ownerId: String = "") : this() {
        owner_id = ownerId
    }
}
```

## Open a Realm
Use
`RealmConfiguration.create()`
to open a realm using default parameters. Pass your configuration to the
`Realm factory constructor`
to generate an instance of that realm:

```kotlin
val config = RealmConfiguration.create(schema = setOf(Item::class))
val realm: Realm = Realm.open(config)

```

For more information on how to control the specifics of the
`RealmConfiguration`
you would like to open (e.g. name, location, schema version), refer to
Open & Close a Realm.

## Create, Read, Update, and Delete Objects
Once opened, you can create objects within a realm in a write transaction block.

To create a new Item, instantiate an instance of the
Item class and add it to the realm in a write transaction block:

```kotlin
realm.writeBlocking {
    copyToRealm(Item().apply {
        summary = "Do the laundry"
        isComplete = false
    })
}
```

You can retrieve a collection of all Todo items in the realm with
`query.find()`:

```kotlin
// all items in the realm
val items: RealmResults<Item> = realm.query<Item>().find()
```

You can also filter a collection to retrieve a more specific collection
of objects:

```kotlin
// items in the realm whose name begins with the letter 'D'
val itemsThatBeginWIthD: RealmResults<Item> =
    realm.query<Item>("summary BEGINSWITH $0", "D")
        .find()
//  todo items that have not been completed yet
val incompleteItems: RealmResults<Item> =
    realm.query<Item>("isComplete == false")
        .find()
```

Find more information about string Realm queries in Filter Data.

To modify a Todo item, update its properties in a write transaction block:

```kotlin
// change the first item with open status to complete to show that the todo item has been done
realm.writeBlocking {
    findLatest(incompleteItems[0])?.isComplete = true
}
```

Finally, you can delete a Todo item by calling `mutableRealm.delete()`
in a write transaction block:

```kotlin
// delete the first item in the realm
realm.writeBlocking {
    val writeTransactionItems = query<Item>().find()
    delete(writeTransactionItems.first())
}
```

## Watch for Changes
You can watch a realm, collection, or object for changes with the `observe` method.

In the following example, we listen for changes on all `Item` objects.

```kotlin
// flow.collect() is blocking -- run it in a background context
val job = CoroutineScope(Dispatchers.Default).launch {
    // create a Flow from the Item collection, then add a listener to the Flow
    val itemsFlow = items.asFlow()
    itemsFlow.collect { changes: ResultsChange<Item> ->
        when (changes) {
            // UpdatedResults means this change represents an update/insert/delete operation
            is UpdatedResults -> {
                changes.insertions // indexes of inserted objects
                changes.insertionRanges // ranges of inserted objects
                changes.changes // indexes of modified objects
                changes.changeRanges // ranges of modified objects
                changes.deletions // indexes of deleted objects
                changes.deletionRanges // ranges of deleted objects
                changes.list // the full collection of objects
            }
            else -> {
                // types other than UpdatedResults are not changes -- ignore them
            }
        }
    }
}
```

Later, when you're done observing, cancel the job to cancel the coroutine:

```kotlin
job.cancel() // cancel the coroutine containing the listener
```

## Close a Realm
To close a realm and all underlying resources, call `realm.close()`. The
`close()` method blocks until all write transactions on the realm have
completed.

```kotlin
realm.close()
```
