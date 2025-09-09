# Change an Object Model - Kotlin SDK

## Overview
You can make changes to your object schema after you
create your Realm object model.
Depending on the type of changes you make to the schema, the changes can be
automatically applied or require a manual update to the new schema.

- Realm will automatically update a Realm object schema when you add or
delete a property from a Realm object model.
You only need to update the schema version.
- For all other schema changes, you must manually migrate old instances
of a given object to the new schema.

> **TIP:**
> When developing or debugging your application, you may prefer to delete
the realm instead of migrating it. Use the
`deleteRealmIfMigrationNeeded`
flag to delete the database automatically when a schema mismatch would
require a migration.
>
> Never release an app to production with this flag set to `true`.
>

## Schema Version
A **schema version** identifies the state of a
realm schema at some point in time.
Realm tracks the schema version of each realm and uses it to map the
objects in each realm to the correct schema.

Schema versions are ascending integers that you can optionally include
in the realm configuration when you open a realm.
If a client application does not specify a version number when it opens a
realm, then the realm defaults to version `0`.

> **IMPORTANT:**
> Migrations must update a realm to a higher schema version.
Realm will throw an error if a client
application opens a realm with a schema version that is lower than
the realm's current version or if the specified schema version is the
same as the realm's current version but includes different
object schemas.
>

## Migration
Schema updates are called **migrations** in Realm.
A migration updates a realm and any objects it
contains from one schema version to a newer version.

Whenever you open an existing realm with a schema version greater than the
realm's current version, you can provide a **migration function** that
defines any additional logic needed for the schema update.
For example:

- Setting property values
- Combining or splitting fields
- Renaming a field
- Changing a field's type

The function has access to the realm's version number and incrementally
updates objects in the realm to conform to the new schema.

## Automatically Update Schema
If your schema update adds or removes properties,
Realm can perform the migration automatically.
You only need to increment the `schemaVersion`.

### Add a Property
To add a property to a schema:

1. Add the new property to the `RealmObject` definition.
2. Set the schema version through the `RealmConfiguration` builder.

Realm automatically sets values for new properties if the updated object schema
specifies a default value.
If the updated object schema does not specify a default value, you must manually
set values for the new property through a migration function.

> **EXAMPLE:**
> A realm using schema version `1` has a `Person` object type
with first name, last name, and age properties:
>
> ```kotlin
> // Realm schema version 1
> class Person : RealmObject {
>     var firstName: String = ""
>     var lastName: String = ""
>     var age: Int = 0
> }
>
> ```
>
> The developer adds an `email` field to the `Person` class:
>
> ```kotlin
> // Realm schema version 2
> class Person : RealmObject {
>     var firstName: String = ""
>     var lastName: String = ""
>     var age: Int = 0
>     var email: String? = null
> }
>
> ```
>
> To change the realm to conform to the updated `Person` schema, the
developer sets the realm's schema version to `2`:
>
> ```kotlin
> val config = RealmConfiguration.Builder(
>     schema = setOf(Person::class)
> )
>     .schemaVersion(2) // Sets the new schema version to 2
>     .build()
> val realm = Realm.open(config)
>
> ```
>

### Delete a Property
To delete a property from a schema:

1. Remove the property from the object's class.
2. Set the schema version through the
`RealmConfiguration` builder.

Deleting a property will not impact existing objects.

> **EXAMPLE:**
> A realm using schema version `2` has a `Person` object type
with first name, last name, age, and email properties:
>
> ```kotlin
> // Realm schema version 2
> class Person : RealmObject {
>     var firstName: String = ""
>     var lastName: String = ""
>     var age: Int = 0
>     var email: String? = null
> }
>
> ```
>
> The developer removes the `age` field from the `Person` class:
>
> ```kotlin
> // Realm schema version 3
> class Person : RealmObject {
>     var firstName: String = ""
>     var lastName: String = ""
>     // var age: Int = 0
>     var email: String? = null
> }
>
> ```
>
> To change the realm to conform to the updated `Person` schema, the
developer sets the realm's schema version to `3`:
>
> ```kotlin
> val config = RealmConfiguration.Builder(
>     schema = setOf(Person::class)
> )
>     .schemaVersion(3) // Sets the new schema version to 3
>     .build()
> val realm = Realm.open(config)
>
> ```
>

## Manually Migrate Schema
For more complex schema updates, Realm requires you to manually
migrate old instances of a given object to the new schema.

When you open the realm with the updated schema, you must do the following
in the
`RealmConfiguration`:

- Increment the `schemaVersion` property.
- Define the migration logic using the `migrationContext`.

### Modify a Property
To modify an object property (e.g. rename, merge, split, or change property
type):

1. Change the property or properties in the object schema.
2. Open the realm with an incremented schema version and a
migration function that maps the existing objects to use the new properties.

In the following example, the schema is updated to change a property type, merge two properties into a new property, and rename an existing property:

```kotlin
// Realm schema version 1 (oldObject)
class Person : RealmObject {
    var _id: ObjectId = ObjectId()
    var firstName: String = ""
    var lastName: String = ""
    var age: Int = 0
}

// Realm schema version 2 (newObject)
class Person : RealmObject {
    var _id: String = "" // change property type
    var fullName: String = "" // merge firstName and lastName properties
    var yearsSinceBirth: Int = 0 // rename property
}

```

Then, the migration function defines the migration logic to map data between
the modified properties in the old object schema and the new object schema:

```kotlin
// Use the configuration builder to open the realm with the newer schema version
// and define the migration logic between your old and new realm objects
val config = RealmConfiguration.Builder(
    schema = setOf(Person::class)
)
    .schemaVersion(2) // Set the new schema version to 2
    .migration(AutomaticSchemaMigration {
        it.enumerate(className = "Person") { oldObject: DynamicRealmObject, newObject: DynamicMutableRealmObject? ->
            newObject?.run {
                // Change property type
                set(
                    "_id",
                    oldObject.getValue<ObjectId>(fieldName = "_id").toString()
                )

                // Merge properties
                set(
                    "fullName",
                    "${oldObject.getValue<String>(fieldName = "firstName")} ${oldObject.getValue<String>(fieldName = "lastName")}"
                )

                // Rename property
                set(
                    "yearsSinceBirth",
                    oldObject.getValue<String>(fieldName = "age")
                )
            }
        }
    })
    .build()
val realm = Realm.open(config)

```

> **NOTE:**
> If your schema update includes converting a
`RealmObject` to an
`EmbeddedRealmObject`,
the migration function must ensure that the embedded object has exactly one
parent object linked to it.
Embedded objects cannot exist independently of a parent object.
>

### Other Migration Tasks
To perform other realm schema migrations, use the following properties of
the `AutomaticSchemaMigration.MigrationContext` interface:

- `oldRealm`:
The realm as it existed before the migration with the previous schema
version.
The `dynamic API` lets you find Realm objects by a string representation of
their class name.
- `newRealm`:
The realm as it exists after the migration using the new schema version.

Any objects obtained from `oldRealm` and `newRealm`
are valid only in the scope of the migration function.

By the end of the migration, you must migrate all data affected by the schema
update from the old realm to the new realm.
Any data affected by the schema update that is not migrated will be lost.

```kotlin
val config = RealmConfiguration.Builder(
    schema = setOf(Person::class)
)
    .schemaVersion(2)
    .migration(AutomaticSchemaMigration { migrationContext ->
        val oldRealm = migrationContext.oldRealm // old realm using the previous schema
        val newRealm = migrationContext.newRealm // new realm using the new schema

        // Dynamic query for all Persons in old realm
        val oldPersons = oldRealm.query(className = "Person").find()
        for (oldPerson in oldPersons) {
            // Get properties from old realm
            val firstName: String = oldPerson.getValue(
                propertyName = "firstName", String::class
            )
            // Get objects from old realm as dynamic realm objects
            val pet: DynamicRealmObject? = oldPerson.getObject(
                propertyName = "pets"
            )
        }

        // Get migrated objects from the new realm as mutable objects
        val oldPerson: DynamicMutableRealmObject? =
            newRealm.findLatest(oldPersons[0])
        oldPerson?.let {
            it.set("fullName", "Crow T. Robot")
        }

        // Create an object in the new realm and set property values
        val newPerson = newRealm.copyToRealm(
            DynamicMutableRealmObject.create(
                type = "Person",
                mapOf(
                    "_id" to "123456",
                    "fullName" to "Tom Servo",
                    "yearsSinceBirth" to 33,
                )
            )
        )
    })
    .build()
val realm = Realm.open(config)

```
