# React to Changes - Kotlin SDK
Any modern app should be able to react when data changes,
regardless of where that change originated. When a user adds a new item to a list,
you may want to update the UI, show a notification, or log a message.
When someone updates that item, you may want to change its visual state
or fire off a network request.
Finally, when someone deletes the item, you probably want to remove it from the UI.
Realm's notification system allows you to watch for and react to changes in your data,
independent of the writes that caused the changes.

The frozen architecture of the Kotlin SDK makes notifications even more
important. Because the Kotlin SDK doesn't have live objects that update
automatically, you'll use notifications to keep your UI and data layer
in sync.

You can subscribe to changes on the following events:

- Query on collection
- Realm object
- Realm collections (e.g. list)

The SDK only provides notifications for objects nested up to four layers deep.
If you need to react to changes in more deeply-nested objects, register a
key path change listener. For more information, refer to
**Register a Key Path Change Listener** on this page.

> **EXAMPLE:**
> The examples in this page use two Realm object types, `Character`
> and `Fellowship`:
>
> ```kotlin
> class Character(): RealmObject {
>     @PrimaryKey
>     var name: String = ""
>     var species: String = ""
>     var age: Int = 0
>     constructor(name: String, species: String, age: Int) : this() {
>         this.name = name
>         this.species = species
>         this.age = age
>     }
> }
> class Fellowship() : RealmObject {
>     @PrimaryKey
>     var name: String = ""
>     var members: RealmList<Character> = realmListOf()
>     constructor(name: String, members: RealmList<Character>) : this() {
>         this.name = name
>         this.members = members
>     }
> }
>
> ```
>
> The examples have this sample data:
>
> ```kotlin
> val config = RealmConfiguration.Builder(setOf(Fellowship::class, Character::class))
>     .name(realmName)
>     .build()
> val realm = Realm.open(config)
>
> val frodo = Character("Frodo", "Hobbit", 51)
> val samwise = Character("Samwise", "Hobbit", 39)
> val aragorn = Character("Aragorn", "Dúnedain", 87)
> val legolas = Character("Legolas", "Elf", 2931)
> val gimli = Character("Gimli", "Dwarf", 140)
> val gollum = Character("Gollum", "Hobbit", 589)
>
> val fellowshipOfTheRing = Fellowship(
>     "Fellowship of the Ring",
>     realmListOf(frodo, samwise, aragorn, legolas, gimli))
>
> realm.writeBlocking{
>     this.copyToRealm(fellowshipOfTheRing)
>     this.copyToRealm(gollum) // not in fellowship
> }
>
> realm.close()
> ```
>

## Register a Query Change Listener
You can register a notification handler on any query within a Realm.
First, create a Kotlin Flow from the query with `asFlow()`.
Next, use the `collect()` method to handle events on that Flow. Events
of type `UpdatedResults` record all changes to the objects matching the
query using the following properties:

|Property|Type|Description|
| --- | --- | --- |
|`insertions`|*IntArray*|Indexes in the new collection which were added in this version.|
|`insertionRanges`|*Array<ListChangeSet.Range>*|Ranges of indexes in the new collection which were added in this version.|
|`changes`|*IntArray*|Indexes of the objects in the new collection which were modified in this version.|
|`changeRanges`|*Array<ListChangeSet.Range>*|Ranges of indexes in the new collection which were modified in this version.|
|`deletions`|*IntArray*|Indexes in the previous version of the collection which have been removed from this one.|
|`deletionRanges`|*Array<ListChangeSet.Range>*|Ranges of indexes in the previous version of the collection which have been removed from this one.|
|`list`|*RealmResults<T as RealmObject>*|Results collection being monitored for changes.|

```kotlin
// Listen for changes on whole collection
val characters = realm.query(Character::class)

// flow.collect() is blocking -- run it in a background context
val job = CoroutineScope(Dispatchers.Default).launch {
    // create a Flow from that collection, then add a listener to the Flow
    val charactersFlow = characters.asFlow()
    val subscription = charactersFlow.collect { changes: ResultsChange<Character> ->
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
// Listen for changes on RealmResults
val hobbits = realm.query(Character::class, "species == 'Hobbit'")
val hobbitJob = CoroutineScope(Dispatchers.Default).launch {
    val hobbitsFlow = hobbits.asFlow()
    val hobbitsSubscription = hobbitsFlow.collect { changes: ResultsChange<Character> ->
        // ... all the same data as above
    }
}
```

## Register a RealmObject Change Listener
You can register a notification handler on a specific object within a realm.
Realm notifies your handler when any of the object's properties change.
To register a change listener on a single object, obtain a `RealmSingleQuery`
with `realm.query.first()`. Generate a Flow from that query with `asFlow()`.
The handler receives a `SingleQueryChange` object that communicates
object changes using the following subtypes:

|Subtype|Properties|Notes|
| --- | --- | --- |
|`UpdatedObject`|*changedFields*, *obj*|Pass a field name to `isFieldChanged()` to check if that field changed.|
|`DeletedObject`|*obj*|Since *obj* always reflects the latest version of the object, it always returns a null value in this subtype.|

```kotlin
// query for the specific object you intend to listen to
val frodo = realm.query(Character::class, "name == 'Frodo'").first()
// flow.collect() is blocking -- run it in a background context
val job = CoroutineScope(Dispatchers.Default).launch {
    val frodoFlow = frodo.asFlow()
    frodoFlow.collect { changes: SingleQueryChange<Character> ->
        when (changes) {
            is UpdatedObject -> {
                changes.changedFields // the changed properties
                changes.obj // the object in its newest state
                changes.isFieldChanged("name") // check if a specific field changed in value
            }
            is DeletedObject -> {
                // if the object has been deleted
                changes.obj // returns null for deleted objects -- always reflects newest state
            }
            is InitialObject -> {
                // Initial event observed on a RealmObject or EmbeddedRealmObject flow.
                // It contains a reference to the starting object state.
                changes.obj
            }
            is PendingObject -> {
                // Describes the initial state where a query result does not contain any elements.
                changes.obj
            }
        }
    }
}
```

## Register a Collection Change Listener
You can register a notification handler on a `RealmList`, `RealmSet`,
or `RealmMap`.
Realm notifies your handler when any of the collection items change.
First, create a Kotlin Flow from the collection with `asFlow()`.
Next, use the `collect()` method to handle events on that Flow. Events
of type `ListChange`, `SetChange`, or `MapChange` record all
changes to the collection.

#### List

`RealmList` notifications implement the `ListChange`
interface, which describes the possible changes that can occur in a RealmList
collection. These states are represented by subclasses:

- `InitialList`
- `UpdatedList`
- `DeletedList`

A `ListChangeSet`
is an interface that models the changes that can occur in a list, and the
properties include:

|Property|Type|Description|
| --- | --- | --- |
|`insertions`|*IntArray*|Indexes in the new collection which were added in this version.|
|`insertionRanges`|*Array<ListChangeSet.Range>*|Ranges of indexes in the new collection which were added in this version.|
|`changes`|*IntArray*|Indexes of the objects in the new collection which were modified in this version.|
|`changeRanges`|*Array<ListChangeSet.Range>*|Ranges of indexes in the new collection which were modified in this version.|
|`deletions`|*IntArray*|Indexes in the previous version of the collection which have been removed from this one.|
|`deletionRanges`|*Array<ListChangeSet.Range>*|Ranges of indexes in the previous version of the collection which have been removed from this one.|
|`list`|*RealmResults<T as RealmObject>*|Results collection being monitored for changes. This is provided when the event type is an `UpdatedList`.|

#### Set

`RealmSet` notifications implement the `SetChange`
interface, which describes the possible changes that can occur in a RealmSet
collection. These states are represented by subclasses:

- `InitialSet`
- `UpdatedSet`
- `DeletedSet`

A `SetChangeSet`
is an interface that models the changes that can occur in a set, and the
properties include:

|Property|Type|Description|
| --- | --- | --- |
|`deletions`|*Int*|The number of entries that have been deleted in this version of the collection.|
|`insertions`|*Int*|The number of entries that have been inserted in this version of the collection.|
|`set`|*RealmSet<T>*|The set being monitored for changes. This is provided when the event type is an `UpdatedSet`.|

#### Map

`RealmMap` notifications implement the `MapChange`
interface, which describes the possible changes that can occur in a RealmMap
collection. These states are represented by subclasses:

- `InitialMap`
- `UpdatedMap`
- `DeletedMap`

A `MapChangeSet`
is an interface that models the changes that can occur in a map, and the
properties include:

|Property|Type|Description|
| --- | --- | --- |
|`deletions`|*Array<K>*|The keys that have been deleted in this version of the map.|
|`insertions`|*Array<K>*|The keys that have been inserted in this version of the map.|
|`changes`|*Array<K>*|The keys whose values have changed in this version of the collection.|
|`set`|*RealmMap<K, V>*|The map being monitored for changes. This is provided when the event type is an `UpdatedMap`.|

```kotlin
// query for the specific object you intend to listen to
val fellowshipOfTheRing = realm.query(Fellowship::class, "name == 'Fellowship of the Ring'").first().find()!!
val members = fellowshipOfTheRing.members
// flow.collect() is blocking -- run it in a background context
val job = CoroutineScope(Dispatchers.Default).launch {
    val membersFlow = members.asFlow()
    membersFlow.collect { changes: ListChange<Character> ->
        when (changes) {
            is UpdatedList -> {
                changes.insertions // indexes of inserted objects
                changes.insertionRanges // ranges of inserted objects
                changes.changes // indexes of modified objects
                changes.changeRanges // ranges of modified objects
                changes.deletions // indexes of deleted objects
                changes.deletionRanges // ranges of deleted objects
                changes.list // the full collection of objects
            }
            is DeletedList -> {
                // if the list was deleted
            }
            is InitialList -> {
                // Initial event observed on a RealmList flow. It contains a reference
                // to the starting list state.
                changes.list
            }
        }
    }
}
```

## Register a Key Path Change Listener
> Version added: 1.13.0

When you register a notification handler, you can pass an optional list of
string property names to specify the key path or key paths to watch.

When you specify key paths, only changes to those key paths trigger
notification blocks. Any other changes do not trigger notification blocks.

In the following example, we register a key path change listener for the `age`
property:

```kotlin
runBlocking {
    // Query for the specific object you intend to listen to.
    val frodoQuery = realm.query(Character::class, "name == 'Frodo'").first()
    val observer = async {
        val frodoFlow = frodoQuery.asFlow(listOf("age"))
        frodoFlow.collect { changes: SingleQueryChange<Character> ->
            // Change listener stuff in here.
        }
    }
    // Changing a property whose key path you're not observing does not trigger a notification.
    realm.writeBlocking {
        findLatest(frodoObject)!!.species = "Ring Bearer"
    }

    // Changing a property whose key path you are observing triggers a notification.
    realm.writeBlocking {
        findLatest(frodoObject)!!.age = 52
    }
    // For this example, we send the object change to a Channel where we can verify the
    // changes we expect. In your application code, you might use the notification to
    // update the UI or take some other action based on your business logic.
    channel.receiveOrFail().let { objChange ->
        assertIs<UpdatedObject<*>>(objChange)
        assertEquals(1, objChange.changedFields.size)
        // Because we are observing only the `age` property, the change to
        // the `species` property does not trigger a notification.
        // The first notification we receive is a change to the `age` property.
        assertEquals("age", objChange.changedFields.first())
    }
    observer.cancel()
    channel.close()
}

```

> **NOTE:**
> Multiple notification tokens on the same object which filter for
> separate key paths *do not* filter exclusively. If one key path
> change is satisfied for one notification token, then all
> notification token blocks for that object will execute.
>

### Observe Nested Key Paths
You can use dot notation to observe nested key paths. By default, the SDK
only reports notifications for objects nested up to four layers deep. Observe
a specific key path if you need to observe changes on more deeply nested
objects.

In the following example, we watch for updates to the nested property
`members.age`. Note that the SDK reports the change to the top-level
`members` property, even though we're watching for changes to a nested
property:

```kotlin
runBlocking {
    // Query for the specific object you intend to listen to.
    val fellowshipQuery = realm.query(Fellowship::class).first()
    val observer = async {
        val fellowshipFlow = fellowshipQuery.asFlow(listOf("members.age"))
        fellowshipFlow.collect { changes: SingleQueryChange<Fellowship> ->
            // Change listener stuff in here.
        }
    }

    // Changing a property whose nested key path you are observing triggers a notification.
    val fellowship = fellowshipQuery.find()!!
    realm.writeBlocking {
        findLatest(fellowship)!!.members[0].age = 52
    }
    // For this example, we send the object change to a Channel where we can verify the
    // changes we expect. In your application code, you might use the notification to
    // update the UI or take some other action based on your business logic.
    channel.receiveOrFail().let { objChange ->
        assertIs<UpdatedObject<*>>(objChange)
        assertEquals(1, objChange.changedFields.size)
        // While you can watch for updates to a nested property, the notification
        // only reports the change on the top-level property. In this case, there
        // was a change to one of the elements in the `members` property, so `members`
        // is what the notification reports - not `age`.
        assertEquals("members", objChange.changedFields.first())
    }
    observer.cancel()
    channel.close()
}
```

### Observe Key Paths with Wildcards
You can use wildcards (`*`) to observe changes to all key paths at the
level of the wildcard.

In the following example, we use the wildcard watch for changes to any nested
properties one level deep within the `members` property. Based on the model
used in this example, this change listener would report changes to any member's
`age`, `species`, or `name` properties. Note that the SDK reports the
change to the top-level `members` property, even though we're watching for
changes to a nested property:

```kotlin
runBlocking {
    // Query for the specific object you intend to listen to.
    val fellowshipQuery = realm.query(Fellowship::class).first()
    val observer = async {
        // Use a wildcard to observe changes to any key path at the level of the wildcard.
        val fellowshipFlow = fellowshipQuery.asFlow(listOf("members.*"))
        fellowshipFlow.collect { changes: SingleQueryChange<Fellowship> ->
            // Change listener stuff in here.
        }
    }

    // Changing any property at the level of the key path wild card triggers a notification.
    val fellowship = fellowshipQuery.find()!!
    realm.writeBlocking {
        findLatest(fellowship)!!.members[0].age = 52
    }
    // For this example, we send the object change to a Channel where we can verify the
    // changes we expect. In your application code, you might use the notification to
    // update the UI or take some other action based on your business logic.
    channel.receiveOrFail().let { objChange ->
        assertIs<UpdatedObject<*>>(objChange)
        assertEquals(1, objChange.changedFields.size)
        // While you can watch for updates to a nested property, the notification
        // only reports the change on the top-level property. In this case, there
        // was a change to one of the elements in the `members` property, so `members`
        // is what the notification reports - not `age`.
        assertEquals("members", objChange.changedFields.first())
    }
    observer.cancel()
    channel.close()
}
```

## Unsubscribe a Change Listener
Unsubscribe from your change listener when you no longer want to receive notifications on updates to the data it's watching. To unsubscribe a change listener, [cancel the enclosing coroutine](https://kotlinlang.org/docs/cancellation-and-timeouts.html).

```kotlin
// query for the specific object you intend to listen to
val fellowshipOfTheRing = realm.query(Fellowship::class, "name == 'Fellowship of the Ring'").first().find()!!
val members = fellowshipOfTheRing.members
// flow.collect() is blocking -- run it in a background context
val job = CoroutineScope(Dispatchers.Default).launch {
    val membersFlow = members.asFlow()
    membersFlow.collect { changes: ListChange<Character> ->
        // change listener stuff in here
    }
}
job.cancel() // cancel the coroutine containing the listener

```

## Change Notification Limits
Changes in nested documents deeper than four levels down do not trigger
change notifications.

If you have a data structure where you need to listen for changes five
levels down or deeper, workarounds include:

- Refactor the schema to reduce nesting.
- Add something like "push-to-refresh" to enable users to manually refresh data.
