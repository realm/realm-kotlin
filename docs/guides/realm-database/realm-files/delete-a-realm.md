# Delete a Realm - Kotlin SDK
## Overview
In some circumstances, you might need to delete
a realm file and its auxiliary files. This is often useful during development to
quickly reset your environment.

To avoid losing data, you can delete these files when
all instances of a realm are closed.

To safely delete a realm file while the app is running, you can use the
`Realm.deleteRealm()`
method. The following code demonstrates this:

```kotlin
// You must close a realm before deleting it
realm.close()
// Delete the realm
Realm.deleteRealm(config)

```

## Delete a Realm File to Avoid Migration
If you iterate rapidly as you develop your app, you may want to delete a realm
file instead of migrating it when you make schema changes. The Realm
configuration provides a `deleteRealmIfMigrationNeeded`
parameter to help with this case.

When you use `deleteRealmIfMigrationNeeded`, Realm deletes the realm
file if a migration is required. Then, you can create objects that match the new
schema instead of writing migration blocks for development or test data.

```kotlin
val config = RealmConfiguration.Builder(
    schema = setOf(Frog::class)
)
    .deleteRealmIfMigrationNeeded()
    .build()
val realm = Realm.open(config)
Log.v("Successfully opened realm: ${realm.configuration.name}")

```

> **IMPORTANT:**
> Never release an app to production with this flag set to `true`.
>
