# Configure & Open a Realm - Kotlin SDK
This page describes realm files and how to configure, open, and close a realm
that only persists data locally.

A **realm** is the core data structure used to organize data in
Realm. Each realm is a collection of Realm objects: the objects
that you use in your application as well as additional metadata that
describe those objects. Each Realm object type has an object schema
based on your defined object model.

A **realm schema** is a list of the valid object schemas that a realm
may contain. You specify the schema when you open the realm.
If a realm already contains data when you open it, Realm
validates each object to ensure that an object schema was provided
for its type and that it meets all of the constraints specified in
the schema.

## Realm Files
Realm stores a binary-encoded version of every object and type in a realm
in a single `.realm` file. When you open a realm, Realm creates the
`.realm` file if it doesn't already exist. The file is located at a specific
path, which you can define when you open the realm.

> **TIP:**
> You can open, view, and edit the contents of realm files with Realm Studio.
>

If you don't want to create a `.realm` file or its associated auxiliary
files, you can open an in-memory realm.
Refer to the Open an In-Memory Realm section for
more information.

### Auxiliary Files
Realm creates additional files for each realm:

- **realm files**, suffixed with "realm", e.g. `default.realm`:
contain object data.
- **lock files**, suffixed with "lock", e.g. `default.realm.lock`:
keep track of which versions of data in a realm are
actively in use. This prevents realm from reclaiming storage space
that is still used by a client application.
- **note files**, suffixed with "note", e.g. `default.realm.note`:
enable inter-thread and inter-process notifications.
- **management files**, suffixed with "management", e.g. `default.realm.management`:
internal state management.

Deleting these files has important implications.
For more information about deleting `.realm` or auxiliary files, see Delete a Realm.

## Open a Realm
To open a realm, create a `RealmConfiguration`
object that defines the details of a realm. Then, pass the resulting
`RealmConfiguration` to `Realm.open()`.

You can open a realm with default configuration values or you can build a
`RealmConfiguration` with additional configuration options. However, you
*must* pass a `schema`
parameter that includes all object classes
that you want to use in the realm.

After you open the realm with the desired configuration, you can
read and write data based on your defined schema.

The examples on this page refer to the following Realm objects:

```kotlin
class Frog : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var name: String = ""
}

class Person : RealmObject {
    @PrimaryKey
    var _id: ObjectId = ObjectId()
    var name: String = ""
}
```

### Open a Default Realm
To open a realm with default configuration values, use the
`RealmConfiguration.create()`.
method. You only need to define a set of object classes as the realm schema.
Then, pass the `RealmConfiguration` to `Realm.open()`.

The following example opens a `default.realm` file at the default path with
a schema that includes the `Frog` and `Person` classes:

```kotlin
// Creates a realm with default configuration values
val config = RealmConfiguration.create(
    // Pass object classes for the realm schema
    schema = setOf(Frog::class, Person::class)
)

// Open the realm with the configuration object
val realm = Realm.open(config)
Log.v("Successfully opened realm: ${realm.configuration.name}")

// ... use the open realm ...
```

#### Default File Location
If you don't define a directory for the realm file, the default app storage
location for the platform is used. You can find the default directory for your
platform with the following:

- Android:
    ```
    Context.getFiles()
    ```
- JVM:
    ```
    System.getProperty("user.dir")
    ```
- macOS:
  ```
  platform.Foundation.NSFileManager.defaultManager.currentDirectoryPath
  ```
- iOS:
    ```
    NSFileManager.defaultManager.URLForDirectory(
    NSDocumentDirectory,
    NSUserDomainMask,
    null,
    true,
    null
    )
    ```

### Open an In-Memory Realm
You can open a realm entirely in memory, which does not create a
`.realm` file or its associated auxiliary files. Instead, the SDK stores
objects in memory while the realm is open, and then discards the data
when all instances are closed.

To open a realm that runs without being written to a file, build a
`RealmConfiguration` with the
`RealmConfiguration.Builder`
using the `inMemory`
property.
Then, pass the resulting `RealmConfiguration` to `Realm.open()`:

```kotlin
// Create the in-memory realm configuration
val config = RealmConfiguration.Builder(
    schema = setOf(Frog::class, Person::class)
)
    .inMemory()
    .build()

// Open the realm with the configuration object
val realm = Realm.open(config)
Log.v("Successfully opened an in-memory realm")

// ... use the open realm ...
```

> **NOTE:**
> Because in-memory realms are not persisted, ensure you have at least one open
> instance of the realm for as long as you want to access the data. After you
> close the last instance of an in-memory realm,
> the data is no longer available.
>

## Customize a Realm Configuration
You can add optional arguments to the `RealmConfiguration` to control the
specifics of the realm that you would like to open, including:

- Defining a custom file name and file path
- Passing an encryption key to encrypt a realm
- Compacting a realm to reduce its file size
- Specifying a schema version or migration block when
making schema changes
- Flagging whether Realm should delete the realm file if a migration is required

For more information on specific configuration implementations, refer to
Manage Realm Files.

To configure a realm with non-default values, create the `RealmConfiguration`
through `RealmConfiguration.Builder.build()`
and pass any properties you want to set. Then, pass the resulting
`RealmConfiguration` to `Realm.open()`.

In the following example, the `RealmConfiguration` specifies a custom
name and directory ("my-directory-path/myRealmName.realm") and encryption key:

```kotlin
// Create a realm configuration with configuration builder
// and pass all optional arguments
val config = RealmConfiguration.Builder(
    schema = setOf(Frog::class, Person::class)
)
    .name("myRealmName.realm")
    .directory("my-directory-path")
    .encryptionKey(myEncryptionKey)
    .build()

// Open the realm with the configuration object
val realm = Realm.open(config)
Log.v("Successfully opened realm: ${realm.configuration.name}")

// ... use the open realm ...
```

### Add Initial Data to Realm
You can add initial data to a realm by using an `initialDataCallback`.
The callback is triggered when the realm is opened for the first time.

```kotlin
val config = RealmConfiguration.Builder(
    schema = setOf(Frog::class, Person::class)
)
    .initialData {
        copyToRealm(Frog().apply { name = "Kermit" })
        copyToRealm(Person().apply { name = "Jim Henson" })
    }
    .build()

val realm = Realm.open(config)
Log.v("Successfully opened realm: ${realm.configuration.name}")

// Opened realm includes the initial data
val initialFrog = realm.query<Frog>().find().first()
val initialPerson = realm.query<Person>().find().first()
Log.v("Realm initialized with: ${initialFrog.name} and ${initialPerson.name}")
```

## Find a Realm File Path
To find the file path for a `.realm` file, use the `realm.configuration.path` property:

```kotlin
val realmPath = realm.configuration.path
Log.v("Realm path: $realmPath")
```

## Close a Realm
You close a realm with `realm.close()`. This method
blocks until all write transactions on the realm have completed.

```kotlin
realm.close()

```

> **WARNING:**
> It's important to close your realm instance to free resources.
> Failing to close realms can lead to an `OutOfMemoryError`.
>

## Copy Data into a New Realm
To copy data from an existing realm to a new realm with different
configuration options, pass the new configuration to the
`Realm.writeCopyTo()`
method.
For example, you might copy data as a way to backup a local realm.

You can copy data to a realm that uses the same configuration:

- Local realm to local realm
- In-memory realm to in-memory realm

Some additional considerations to keep in mind while using
`Realm.writeCopyTo()`:

- The destination file cannot already exist.
- Copying a realm is not allowed within a write transaction or during
a migration.

You can also include a new encryption key
in the copied realm's configuration or remove the encryption key from the new configuration.

> **EXAMPLE:**
> ```kotlin
> runBlocking {
>     // Create the unencrypted realm
>     val unencryptedConfig = RealmConfiguration.Builder(setOf(Frog::class))
>         .name("unencrypted.realm")
>         .build()
>
>     // Open the realm and add data to it
>     val unencryptedRealm = Realm.open(unencryptedConfig)
>
>     unencryptedRealm.write {
>         this.copyToRealm(Frog().apply {
>             name = "Kermit"
>         })
>     }
>
>     // ... Generate encryption key ...
>
>     // Create the encrypted realm
>     val encryptedConfig = RealmConfiguration.Builder(setOf(Frog::class))
>         .name("encrypted.realm")
>         .encryptionKey(encryptionKey)
>         .build()
>
>     // Copy data from `unencryptedRealm` to the new realm
>     // Data is encrypted as part of the copy process
>     unencryptedRealm.writeCopyTo(encryptedConfig)
>
>     // Close the original realm when you're done copying
>     unencryptedRealm.close()
>
>     // Open the new encrypted realm
>     val encryptedRealm = Realm.open(encryptedConfig)
>
>     // Copied Frog object is available in the new realm
>     val frog = encryptedRealm.query<Frog>().find().first()
>     Log.v("Copied Frog: ${frog.name}")
>
>     encryptedRealm.close()
> }
>
> ```
>

> **EXAMPLE:**
> ```kotlin
> runBlocking {
>     // Create the in-memory realm
>     val inMemoryConfig = RealmConfiguration.Builder(setOf(Frog::class))
>         .name("inMemory.realm")
>         .inMemory()
>         .build()
>
>     // Open the realm and add data to it
>     val inMemoryRealm = Realm.open(inMemoryConfig)
>
>     inMemoryRealm.write {
>         this.copyToRealm(Frog().apply {
>             name = "Kermit"
>         })
>     }
>
>     // Create the local realm
>     val localConfig = RealmConfiguration.Builder(setOf(Frog::class))
>         .name("local.realm")
>         .build()
>     // Copy data from `inMemoryRealm` to the new realm
>     inMemoryRealm.writeCopyTo(localConfig)
>     // Close the original realm when you're done copying
>     inMemoryRealm.close()
>
>     // Open the new local realm
>     val localRealm = Realm.open(localConfig)
>
>     // Copied Frog object is available in the new realm
>     val frog = localRealm.query<Frog>().find().first()
>     Log.v("Copied Frog: ${frog.name}")
>
>     localRealm.close()
> }
>
> ```
>
