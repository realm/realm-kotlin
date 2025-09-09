# Frozen Architecture - Kotlin SDK
Unlike the other Realm SDKs, the Kotlin SDK
does not provide live objects and collections that
update simultaneously with underlying data. Instead,
the Kotlin SDK works exclusively with **frozen objects**
that can be passed between threads safely.

## Work with Frozen Objects
Because frozen objects don't automatically update when data changes
in your realm, they work a little differently from the live objects
you may have used in other Realm SDKs.

### Access a Live Version of Frozen Object
In order to update or delete
objects, they must be live. You can convert a frozen
object to a live object in a transaction with `mutableRealm.findLatest()`.
Live objects are only accessible inside of a write transaction within a `write` or `writeBlocking` closure.

Objects returned from a write closure become frozen objects when the
write transaction completes.

```kotlin
val sample: Sample? =
    realm.query<Sample>()
        .first().find()

// delete one object synchronously
realm.writeBlocking {
    if (sample != null) {
        findLatest(sample)
            ?.also { delete(it) }
    }
}

// delete a query result asynchronously
GlobalScope.launch {
    realm.write {
        query<Sample>()
            .first()
            .find()
            ?.also { delete(it) }
    }
}

```

> **TIP:**
> You can check if an object is frozen with the `isFrozen()` method.
>

## Thread-safe Realms
The Realm class is no longer thread-confined, so you can share a
single realm across multiple threads. You no longer need to handle the
realm lifecycle explicitly with calls to `Realm.close()`.

### Access Changes
To access changes to objects and collections, use
[Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
and the [Flow API](https://kotlinlang.org/docs/flow.html). Changes are
thread-safe, so you can access them from any context. Notifications
are handled on a dedicated internal notifier thread. Frozen objects
now support change listeners.

```kotlin
val config = RealmConfiguration.Builder(schema = setOf(Task::class))
    .build()
val realm = Realm.open(config)

// fetch objects from a realm as Flowables
CoroutineScope(Dispatchers.Main).launch {
    val flow: Flow<ResultsChange<Task>> = realm.query<Task>().asFlow()
    flow.collect { task ->
        Log.v("Task: $task")
    }
}

// write an object to the realm in a coroutine
CoroutineScope(Dispatchers.Main).launch {
    realm.write {
        copyToRealm(Task().apply { name = "my task"; status = "Open"})
    }
}

```

> **IMPORTANT:**
> To use the Flows API in your Kotlin Multiplatform project, install the
> [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines#multiplatform)
> library.
>

Just like in other Realm SDKs, write transactions implicitly
advance your realm to the most recent version of data stored on disk.

> **SEE ALSO:**
> For more information on notifications, refer to React to Changes.
>

### Lazy Loading
Realm objects are still lazy-loaded by default. This allows
you to query large collections of objects without reading large amounts
of data from disk. This also means that the first access to
a field of an object will always return the most recent data.
