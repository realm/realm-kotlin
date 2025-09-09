# Read Realm Objects - Kotlin SDK
This page describes how to query and lazily read objects persisted in a database
with the Realm SDK for Kotlin. This lazy evaluation enables code
efficiency and performance when handling large data sets and complex queries.

## Read Operations
A read operation consists of querying database objects, then running the query
when you're ready to access the results.

All queries are based on object type.
You can query any object, including embedded objects,
that persist to the database and whose type is included in your
database schema.

Construct queries using the SDK's query builder
`RealmQuery`,
passing the object type as the type parameter. You can query objects on a
`Realm` or `MutableRealm` instance, a `RealmResults` collection, or a
`RealmList` collection.

A basic `RealmQuery` returns all objects of the specified type:

```kotlin
// Finds all objects of type <T>
.query<T>()
```

You can refine your query with additional filters and conditions (for example,
sorting, aggregating, limiting results). You can refine results by one or
more property using  **Realm Query Language (RQL)**, a string-based query
language, in combination with built-in Kotlin extension functions and
helper methods provided by the SDK.

```kotlin
// Finds all objects of type <T> that meet the filter conditions
// and returns them in the specified sort order
.query<T>(filter).sort(sort)
```

You can also **chain queries** together using additional
`query()`
methods. Each appended `query()` acts as an `AND` query condition.
And because of the SDK's lazy evaluation, successive queries do not require
separate trips to the database.

```kotlin
// Finds all objects of type <T> that meet the first filter conditions
// AND the subsequent filter conditions
.query<T>(filter).query(filter)
```

When you're ready to access the data and work with the returned results,
run the query:

- Use `find()`
to perform a **synchronous query**. The SDK lazily returns a
`RealmResults`
collection, which represents all database objects that match the query
conditions. In general, you can can work with a results collection like any
other [Kotlin Collection](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-collection/).
- Use `asFlow()`
to perform an **asynchronous query**. The SDK lazily subscribes to a
[Kotlin Coroutine Flow](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-flow/)
that you can collect and iterate over or listen to for changes. You *cannot*
call `asFlow()` on a `MutableRealm.query`.

Note that any retrieved results don't actually hold matching database objects
in memory. Instead, the database uses **direct references**, or pointers.
Database objects in a results collection or flow reference the matched
objects, which map directly to data in the database file. This also means
that you can traverse your graph of an object's
relationships directly through the results
of a query.

> **EXAMPLE:**
> ```kotlin
> val queryAllFrogs = realm.query<Frog>()
> val queryAllLiveFrogs = this.query<Frog>() // this: MutableRealm
>
> // Calling 'find()' on the query returns a RealmResults collection
> // Can be called on a `Realm.query()` or `MutableRealm.query()`
> val allFrogs: RealmResults<Frog> = queryAllFrogs.find()
> val allLiveFrogs: RealmResults<Frog> = queryAllLiveFrogs.find()
>
> // Calling 'asFlow()' on the query returns a ResultsChange Flow
> // Can ONLY be called on a `Realm.query()`
> val allFrogsFlow: Flow<ResultsChange<Frog>> = queryAllFrogs.asFlow()
>
> ```
>

### Frozen and Live Results
Unlike other Realm SDKs, which always return live results,
results with the Kotlin SDK can be frozen or live. For more information on the
Kotlin SDK's frozen architecture, refer to Frozen Architecture - Kotlin SDK.

To access **frozen results**, run a query on a
`Realm`. Frozen
results cannot be modified and do not reflect the latest changes to the
database. A `Realm.query()` does not require a write transaction.

To access **live results**, run a query on a
`MutableRealm`
instance in a write transaction. A `MutableRealm` represents a writeable
state of a database and is *only* accessible through a write transaction.
Results from a
`MutableRealm.query`
are live, but are only valid on the calling thread and are frozen once the
write transaction completes.
For more information on write transactions and accessing a `MutableRealm`,
refer to Write Transactions.

You can also access live objects from frozen results by calling
`MutableRealm.findLatest()` .
For more information, refer to the Find Latest Version of an Object section
on this page.

> **EXAMPLE:**
> ```kotlin
> // 'Realm.query()' results are always frozen
> val frozenResults = realm.query<Frog>("age > $0", 50).find()
>
> // 'MutableRealm.query()' results are live within the current write transaction
> realm.write { // this: MutableRealm
>     val liveResults = this.query<Frog>("age > $0", 50).find()
>
> ```
>

## Find Database Objects
To find objects stored within a database:

1. Pass the object type as a type parameter to
`query()`.
The object type must already be included in your database schema.
2. Optionally, pass any query conditions to further refine the results: Specify a filter to only return objects that meet the condition. If
you don't specify a filter, the SDK returns all objects of the specified
type.You can chain filters by appending additional
`query()`
methods to the `RealmQuery`.Specify the sort order for the results.
Because the database is unordered, if you don't include a sort order,
the SDK cannot guarantee the query returns objects in any specific order.
3. Execute the query using either: `find()`
for synchronous queries. Returns a collection of results.[asFlow()](https://www.mongodb.com/docs/realm-sdks/kotlin/latest/library-base/io.realm.kotlin.query/-realm-element-query/as-flow.html)
for synchronous queries. Returns a collection of results.`asFlow()`
for asynchronous queries. Subscribes to a `Flow` of results changes. `find()` runs a synchronous query on the thread it is called from.
As a result, avoid using `find()` for large data sets on the UI thread or in logic that
could delay the UI thread.Prefer `asFlow()` to prevent negative performance or UI impact.
4. Work with the results. Objects may be frozen or live, depending on the
type of query you ran.

> **EXAMPLE:**
> ```kotlin
> // Pass the object type as <T> parameter and filter by property
> val findFrogs = realm.query<Frog>("age > 1")
>     // Chain another query filter
>     .query("owner == $0 AND name CONTAINS $1", "Jim Henson", "K")
>     // Sort results by property
>     .sort("age", Sort.ASCENDING)
>     // Run the query
>     .find()
> // ... work with the results
>
> ```
>

### Find Latest Version of an Object
Because of the SDK's frozen architecture, you aren't always working
with the latest version of an object or collection.

To get a version of an object or collection that reflects the latest changes to
The database, you can call
`findLatest()`
from a `MutableRealm` instance.
Like a `MutableRealm.query()`, the results are live *but* are only valid on
the calling thread and are frozen once the write transaction completes.

In the following example, we pass an existing `frozenFrogs` query's
`RealmResults` to `findLatest()` to get the latest live copy of the
collection. We then modify the now-live objects within the write transaction:

```kotlin
// Open a write transaction to access the MutableRealm
realm.write {  // this: MutableRealm
    for (frog in frozenFrogs) {
        // Call 'findLatest()' on an earlier query's frozen results
        // to return the latest live objects that you can modify
        // within the current write transaction
        findLatest(frog)?.also { liveFrog ->
            copyToRealm(liveFrog.apply { age += 1 })
            println(liveFrog.name + " is now " + liveFrog.age + " years old")
        }
    }
}

```

> **TIP:**
> You can check if an object is frozen with the
`isFrozen()`
method.
>
> ```kotlin
> val isFrozen = frog.isFrozen()
>
> ```
>

### Query All Objects of a Type
To query all objects of a specific type, pass the `RealmObject` or
`EmbeddedRealmObject` object type as a type parameter to
`query()`
without any query arguments. The SDK returns all objects of the specified type.

In the following example, we query all `RealmObject` objects of type `Frog`:

```kotlin
// Query all Frog objects in the database
val queryAllFrogs = realm.query<Frog>()
val allFrogs = queryAllFrogs.find()

```

In the following example, we query all `EmbeddedRealmObject` objects of type
`EmbeddedAddress`:

```kotlin
val queryAllEmbeddedAddresses = realm.query<EmbeddedAddress>()
val allEmbeddedAddresses = queryAllEmbeddedAddresses.find()

```

You can also query an embedded object through its parent object. For more
information, refer to the Filter By Embedded Object Property section
on this page.

> **TIP:**
> Once you find an embedded object, you can use the
`EmbeddedRealmObject.parent()`
method to access its parent:
>
> ```kotlin
> val getParent = embeddedObject.parent<Contact>()
>
> ```
>

### Query a Single Object
To find a single object of a specific object type, call
`first()`
on the query. When you run the query, the SDK returns the first object that
matches the conditions or `null`.

In the following example, we query a `Frog` object type and return the first
object:

```kotlin
val querySingleFrog = realm.query<Frog>().first()
val singleFrog = querySingleFrog.find()

if (singleFrog != null) {
    println("${singleFrog.name} is a frog.")
} else {
    println("No frogs found.")
}

```

### Filter By Property
You can filter a query by any property in the object type that persists in the
database. This includes child properties, which you can refer to using dot
notation.

To filter by property, you can pass Realm Query Language (RQL) filters and
operators, use Kotlin's built-in extension methods or the SDK's convenience
methods, or use a combination. For information on all currently supported RQL
operators and syntax, refer to the Realm Query Language reference
documentation.

In the following example, we query a `Frog` object type and filter by the
`name` property:

```kotlin
val filterByProperty = realm.query<Frog>("name == $0", "Kermit")
val frogsNamedKermit = filterByProperty.find()

```

#### Filter By Primary Key
Primary keys are unique identifiers for objects
in a database, which makes them useful for querying specific objects.

To filter by a specific primary key, pass the object type as a type
parameter and query the primary key field for the desired value.

In the following example, we query a `Frog` object and filter by the primary
key property `_id`:

```kotlin
val filterByPrimaryKey = realm.query<Frog>("_id == $0", PRIMARY_KEY_VALUE)
val findPrimaryKey = filterByPrimaryKey.find().first()

```

#### Filter By Embedded Object Property
Embedded objects act as nested data inside of
a single specific parent object. You can query an embedded object directly or
as a property on its parent object. For information on querying an embedded
object directly, refer to the Query All Objects of a Type section
on this page.

To find an embedded object through its parent object, pass the parent object
type as a type parameter and filter by the embedded object property using dot
notation.

In the following example, we have a `Contact` parent object that contains an
embedded object property called `address`. We query the `Contact` object
type against the embedded object's `address.street` property:

```kotlin
// Use dot notation to access the embedded object properties as if it
// were in a regular nested object
val filterEmbeddedObjectProperty =
    realm.query<Contact>("address.street == '123 Pond St'")

// You can also access properties nested within the embedded object
val queryNestedProperty = realm.query<Contact>()
    .query("address.propertyOwner.name == $0", "Mr. Frog")

```

#### Filter By RealmAny (Mixed) Property
A RealmAny (Mixed) property represents a polymorphic value that can
hold any one of its supported data types at a particular moment. You can query
a `RealmAny` property the same way you would any property.

In the following example, we query a `RealmAny` property called
`favoriteThing` for a frog with a favorite thing of type `Int`:

```kotlin
val filterByRealmAnyInt = realm.query<Frog>("favoriteThing.@type == 'int'")
val findFrog = filterByRealmAnyInt.find().first()

```

Unlike other properties, you must extract a `RealmAny` property's stored
value before you can work with it. To extract the value, use
the SDK's getter method for the stored type. If you use the wrong getter for
the type, the SDK throws an exception.

A best practice is to use a conditional expression to get the currently stored
type with `RealmAny.type()`,
then extract the value based on the type. For a full list of getter
methods, refer to the
`RealmAny`
API reference.

In the following example, we extract the value using
`RealmAny.asInt()`
since we know the returned frog's favorite thing is an `Int` type value:

```kotlin
val frogsFavoriteThing = findFrog.favoriteThing // Int

// Using the correct getter method returns the value
val frogsFavoriteNumber = frogsFavoriteThing?.asInt()
println("${findFrog.name} likes the number $frogsFavoriteNumber")

```

> **TIP:**
> Use a conditional `when` expression to handle the possible inner value
class of a given `RealmAny` property:
>
> ```kotlin
> // Handle possible types with a 'when' statement
> frogsFavoriteThings.forEach { realmAny ->
>     if (realmAny != null) {
>         when (realmAny.type) {
>             RealmAny.Type.INT -> {
>                 val intValue = realmAny.asInt()
>                 // Do something with intValue ...
>             }
>             RealmAny.Type.STRING -> {
>                 val stringValue = realmAny.asString()
>                 // Do something with stringValue ...
>             }
>             RealmAny.Type.OBJECT -> {
>                 val objectValue = realmAny.asRealmObject(Frog::class)
>                 // Do something with objectValue ...
>             }
>             // Handle other possible types...
>             else -> {
>                 // Debug or perform a default action for unhandled types
>                 Log.d("Unhandled type: ${realmAny.type}")
>             }
>         }
>     }
> }
>
> ```
>

Once you have the currently stored value, you can work with it the same way you
would another value of that type.

> **NOTE:**
> `Byte`, `Char`, `Int`, `Long`, and `Short` values are converted
internally to `int64_t` values. Keep this in mind when comparing,
sorting, or aggregating `RealmAny` values of these types.
>

#### Filter By Remapped Property
If your data model includes
remapped property names, you can filter by
both the Kotlin property name used in your code and the remapped property
name that's persisted in the database.

In the following example, the `Frog` object has a property named `species`
in the code that is remapped to `latin_name` in the database:

```kotlin
@PersistedName("latin_name")
var species: String? = null // Remapped property

```

In the database, we can filter by either property name and return the same
results:

```kotlin
val filterByKotlinName = realm.query<Frog>("species == $0", "Muppetarium Amphibius")
val findSpecies = filterByKotlinName.find().first()

val filterByRemappedName = realm.query<Frog>("latin_name == $0", "Muppetarium Amphibius")
val find_latin_name = filterByRemappedName.find().first()

// Both queries return the same object
assertEquals(findSpecies, find_latin_name)

```

#### Filter By Full-Text Search (FTS) Property
> Version changed: 1.11.0
> Support for prefix wildcard searches
>

If your data model includes a Full-Text Search (FTS)
index property, you can filter by the property with the `TEXT` predicate.
Words in the query are converted to tokens by a tokenizer using the following
rules:

- Tokens can only consist of characters from ASCII and the Latin-1
supplement (western languages). All other characters are considered whitespace.
- Words split by a hyphen (`-`) are split into two tokens. For example,
`full-text` splits into `full` and `text`.
- Tokens are diacritics- and case-insensitive.

You can search for an entire word or phrase, or limit your results with the
following characters:

- Exclude results for a word by placing the `-` character in front of the
word. For example, `fiction -science` would include all search results
for `fiction` and exclude those that include the word `science`.
- In Kotlin SDK version 1.11.0 and later, you can specify prefixes by placing
the `*` character at the end of a word. For example, `fict*` would include
all search results for `fiction` and `fictitious`. (The Kotlin SDK
does *not* currently support suffix searches.)

The SDK returns a Boolean match for the specified query, instead of a
relevance-based match.

In the following example, the `Frog` object type has an FTS index property
called `physicalDescription` that we can filter by to find different types of frogs:

```kotlin
// Filter by FTS property value using 'TEXT'

// Find all frogs with "green" in the physical description
val onlyGreenFrogs =
    realm.query<Frog>("physicalDescription TEXT $0", "green").find()

// Find all frogs with "green" but not "small" in the physical description
val onlyBigGreenFrogs =
    realm.query<Frog>("physicalDescription TEXT $0", "green -small").find()

// Find all frogs with "muppet-" and "rain-" in the physical description
val muppetsInTheRain =
    realm.query<Frog>("physicalDescription TEXT $0", "muppet* rain*").find()

```

#### Filter By Collection (List, Set, Dictionary) Property
Depending on how you define your object type, you might have properties that
are defined as one of the following supported Collection Types:

- `RealmList`
- `RealmSet`
- `RealmDictionary`

You can query these collection properties the same way you would any other
property using RQL. You can also use Kotlin's built-in collection functions
to filter, sort, and iterate over the results.

##### RealmList
You can query and iterate through a RealmList
property as you would a
[Kotlin List](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-list/#kotlin.collections.List).

In the following example, we query a `RealmList` property called
`favoritePonds`:

```kotlin
// Find frogs with a favorite pond
val allFrogs = query<Frog>().find()
val frogsWithFavoritePond = allFrogs.query("favoritePonds.@size > $0", 0).find()

// Check if the list contains a value
for (frog in frogsWithFavoritePond) {
    val likesBigPond = frog.favoritePonds.any { pond -> pond.name == "Big Pond" }
    if (likesBigPond) {
        Log.v("${frog.name} likes Big Pond")
    } else {
        Log.v("${frog.name} does not like Big Pond")
    }
}

```

##### RealmSet
You can query and iterate through a RealmSet property
as you would a
[Kotlin Set](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-set/#kotlin.collections.Set).

In the following example, we query a `RealmSet` property called
`favoriteSnacks`:

```kotlin
// Find frogs with flies and crickets as a favorite snack
val filterBySnackSet = query<RealmSet_Frog>("favoriteSnacks.name CONTAINS $0 AND favoriteSnacks.name CONTAINS $1", "Flies", "Crickets")
val potentialFrogs = filterBySnackSet.find()

// Check if the set contains a value
val frogsThatLikeWorms = potentialFrogs.filter { frog ->
    val requiredSnacks = query<RealmSet_Snack>("name == $0", "Worms")
    frog.favoriteSnacks.contains(requiredSnacks.find().first())
}
for (frog in frogsThatLikeWorms) {
    Log.v("${frog.name} likes both Flies, Worms, and Crickets")
}

```

##### RealmDictionary
You can query and iterate through a RealmDictionary
property as you would a
[Kotlin Map](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.collections/-map/#kotlin.collections.Map).

In the following example, we query a `RealmDictionary` property called
`favoritePondsByForest`, which maps a `String` key (forest) to a
`String` value (pond):

```kotlin
// Find frogs who have forests with favorite ponds
val frogs = realm.query<Frog>().find()
val frogsWithFavoritePonds = frogs.query("favoritePondsByForest.@count > $0", 1).find()
val thisFrog = frogsWithFavoritePonds.first()

// Iterate through the map and log each key-value pair
for (forestName in thisFrog.favoritePondsByForest.keys) {
    val pondName = thisFrog.favoritePondsByForest[forestName]
    Log.v("Forest: $forestName, Pond: $pondName")
}

// Check if the dictionary contains a key
if (thisFrog.favoritePondsByForest.containsKey("Hundred Acre Wood")) {
    Log.v("${thisFrog.name}'s favorite pond in Hundred Acre Wood is ${thisFrog.favoritePondsByForest["Hundred Acre Wood"]}")
}
// Check if the dictionary contains a value
if (thisFrog.favoritePondsByForest.containsValue("Picnic Pond")) {
    Log.v("${thisFrog.name} lists Picnic Pond as a favorite pond")
}

```

#### Filter By Relationship Property
Depending on how you define your object type, you might have properties that
reference another database object. This can be a to-one, to-many, or inverse
relationship.

For information on filtering by relationship properties that reference an
embedded object, refer to the
Filter By Embedded Object Property section on this page.

##### To-One Relationships
A to-one relationship property maps to
a single instance of another object type. You can filter by the relationship
property using dot notation, the same way you would a nested object.

In the following example, the `Frog` object type has a property called
`favoritePond` of type `Pond`:

```kotlin
// Find frogs who have a favorite pond
val allFrogs = query<Frog>().find()
val frogsWithFavoritePond = allFrogs.query("favoritePond.@count == $0", 1).find()

// Iterate through the results
for (frog in frogsWithFavoritePond) {
    Log.v("${frog.name} likes ${frog.favoritePond?.name}")
}

```

##### To-Many Relationships
To-many relationships properties are
collections (a RealmList or
RealmSet) of another object type. You can filter by
and iterate through the relationship property the same way you would any other
collection property.

In the following example, the `Forest` object type has a property called
`nearbyPonds` that is a `RealmList` of type `Pond`:

```kotlin
// Find all forests with at least one nearby pond
val allForests = query<Forest>().find()
val forestsWithPonds = allForests.query("nearbyPonds.@count > $0", 0).find()

// Iterate through the results
for (forest in forestsWithPonds) {
    val bigPond = query<Pond>("name == $0", "Big Pond").find().first()
    if (forest.nearbyPonds.contains(bigPond)) {
        Log.v("${forest.name} has a nearby pond named ${bigPond.name}")
    } else {
        Log.v("${forest.name} does not have a big pond nearby")
    }
}

```

##### Inverse Relationships
Unlike to-one and to-many relationships, an inverse relationship automatically
creates a backlink between parent and child objects. This means that you can
always query against both the parent and child. You can also use RQL-specific
syntax to query the backlink (for more information, refer to Backlink Queries).

In the following example, a parent object of type `User` has an inverse
relationship to a child object of type `Post`. We can query the parent
object's `User.posts` relationship ("User has many Posts") as well
as the inverse `Post.user` relationship ("Post belongs to User"):

```kotlin
// Query the parent object
val filterByUserName = query<User>("name == $0", "Kermit")
val kermit = filterByUserName.find().first()

// Use dot notation to access child objects
val myFirstPost = kermit.posts[0]

// Iterate through the backlink collection property
kermit.posts.forEach { post ->
    Log.v("${kermit.name}'s Post: ${post.date} - ${post.title}")
}

// Filter posts through the parent's backlink property
// using `@links.<ObjectType>.<PropertyName>` syntax
val oldPostsByKermit = realm.query<Post>("date < $0", today)
    .query("@links.User.posts.name == $0", "Kermit")
    .find()

// Query the child object to access the parent
val post1 = query<Post>("title == $0", "Forest Life").find().first()
val post2 = query<Post>("title == $0", "Top Ponds of the Year!").find().first()
val parent = post1.user.first()

```

> **IMPORTANT:**
> If the inverse relationship property is an object type with a
remapped (persisted) class name, you *must* use the remapped class
name in the raw RQL query.
>
> ```kotlin
> @PersistedName(name = "Blog_Author")
> class User : RealmObject {
>     @PrimaryKey
>     var _id: ObjectId = ObjectId()
>     var name: String = ""
>     var posts: RealmList<Post> = realmListOf()
> }
>
> ```
>
> ```kotlin
> // Filter by the remapped object type name
> // using `@links.<RemappedObjectType>.<PropertyName>` syntax
> val postsByKermit = realm.query<Post>()
>     .query("@links.Blog_Author.posts.name == $0", "Kermit")
>     .find()
>
> ```
>

### Query Geospatial Data
> Version added: 1.11.0

Kotlin SDK version 1.11.0 and later adds experimental geospatial APIs that
support querying with geospatial data.

> **NOTE:**
> To persist geospatial data, you must define a custom GeoJSON-compatible
embedded class in your data model. For more information on the requirements,
refer to Persist Geospatial Data.
>

Geospatial data is persisted as latitude/longitude pair in a custom
embedded object's `coordinates` property. A geospatial query checks
whether the point defined by the `coordinates` property is contained within
the boundary of a defined geospatial shape.

The SDK supports the following geospatial shapes:

- `GeoCircle`: defined by a center `GeoPoint` and a radius `Distance`
- `GeoBox`: defined by two `GeoPoint` coordinates that represent the
southwest and northeast corners of the box
- `GeoPolygon`: defined by a set of at least 4 `GeoPoint` coordinates that
represent a closed shape. This shape can contain holes that represent
exclusive boundaries within the confines of the defined polygon.

For more information on geospatial shapes and how to define them, refer to
Geospatial Data - Kotlin SDK.

To query geospatial data:

1. Create an object containing the embedded geospatial data.
2. Define the geospatial shape to set the boundary for the query.
3. Query using the `GEOWITHIN` RQL operator. This method takes the
`coordinates` property of your GeoJSON-compatible embedded object
and checks if that point is contained within the boundary of a defined
shape. The syntax is the same, regardless of the geospatial shape.

In the following example, we want to query for geospatial data persisted in
a `Company` object through its embedded `location` property. We create
two `GeoCircle` objects to set our query boundary:

```kotlin
val circle1 = GeoCircle.create(
    center = GeoPoint.create(47.8, -122.6),
    radius = Distance.fromKilometers(44.4)
)
val circle2 = GeoCircle.create(
    center = GeoPoint.create(47.3, -121.9),
    radius = Distance.fromDegrees(0.25)
)

```

We then query for any `Company` objects with a `location` contained within
the defined `GeoCircle` boundaries:

```kotlin
val companiesInLargeCircle =
    realm.query<Company>("location GEOWITHIN $circle1").find()
println("Companies in large circle: ${companiesInLargeCircle.size}")

val companiesInSmallCircle =
    realm.query<Company>("location GEOWITHIN $circle2").find()
println("Companies in small circle: ${companiesInSmallCircle.size}")

```

```
Companies in large circle: 1
Companies in small circle: 0
```

![Querying a GeoCircle example](../../../../images/geocircles-query.png)

## Sort and Limit Results
To ensure results are returned as expected, you can specify a
sort order and limit conditions using
RQL Sort, Limit, and Distinct operators,
one of the following SDK convenience methods, or a combination of both:

- `sort()`
- `limit()`
- `distinct()`

> **IMPORTANT:**
> Regardless of whether you use RQL or convenience methods, the SDK executes
each request in the order it's added to the query. This can impact the
results returned. For example, sorting a query before limiting it can
return very different results than sorting *after* limiting it.
>

In the following example, we sort and limit using convenience methods only,
RQL only, and then a combination of both to return the same results:

```kotlin
// Query for all frogs owned by Jim Henson, then:
// 1. Sort results by age in descending order
// 2. Limit results to only distinct names
// 3. Limit results to only the first 2 objects

val organizedWithMethods = realm.query<Frog>("owner == $0", "Jim Henson")
    .sort("age", Sort.DESCENDING)
    .distinct("name")
    .limit(2)
    .find()
organizedWithMethods.forEach { frog ->
    Log.v("Method sort: ${frog.name} is ${frog.age}")
}

val organizedWithRql = realm.query<Frog>()
    .query("owner == $0 SORT(age DESC) DISTINCT(name) LIMIT(2)", "Jim Henson")
    .find()
organizedWithRql.forEach { frog ->
    Log.v("RQL sort: ${frog.name} is ${frog.age}")
}

val organizedWithBoth = realm.query<Frog>()
    .query("owner == $0 SORT(age DESC)", "Jim Henson")
    .distinct("name")
    .limit(2)
    .find()
organizedWithBoth.forEach { frog ->
    Log.v("Combined sort: ${frog.name} is ${frog.age}")
}

```

```
Method sort: Kermit, Sr. is 100
Method sort: Kermit is 42
RQL sort: Kermit, Sr. is 100
RQL sort: Kermit is 42
Combined sort: Kermit, Sr. is 100
Combined sort: Kermit is 42
```

> **NOTE:**
> String sorting and case-insensitive queries are only supported for
character sets in 'Latin Basic', 'Latin Supplement', 'Latin Extended
A', and 'Latin Extended B' (UTF-8 range 0-591).
>

## Aggregate Results
You can also aggregate results, which reduces results to a single value
based on a specified numerical property or collection. You can use
RQL aggregate operators, one of the
following convenience methods, or a combination of both:

- [max()](https://www.mongodb.com/docs/realm-sdks/kotlin/latest/library-base/io.realm.kotlin.query/-realm-query/max.html)
- [min()](https://www.mongodb.com/docs/realm-sdks/kotlin/latest/library-base/io.realm.kotlin.query/-realm-query/min.html)
- [sum()](https://www.mongodb.com/docs/realm-sdks/kotlin/latest/library-base/io.realm.kotlin.query/-realm-query/sum.html)
- [count()](https://www.mongodb.com/docs/realm-sdks/kotlin/latest/library-base/io.realm.kotlin.query/-realm-query/count.html)

In the following example, we aggregate the `age` property of a `Frog`
object type:

```kotlin
val jimHensonsFrogs = realm.query<Frog>("owner == $0", "Jim Henson")

// Find the total number of frogs owned by Jim Henson
val numberOfJimsFrogs = jimHensonsFrogs.count().find()

// Find the oldest frog owned by Jim Henson
val maxAge = jimHensonsFrogs.max<Int>("age").find()
val oldestFrog = jimHensonsFrogs.query("age == $0", maxAge).find().first()

```

## Iterate Results using Flow
You can iterate through results using a
[Kotlin Coroutine Flow](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-flow/).

To convert query results into an asynchronous `Flow`, call
[asFlow()](https://www.mongodb.com/docs/realm-sdks/kotlin/latest/library-base/io.realm.kotlin.query/-realm-element-query/as-flow.html)
on the query. The SDK returns a [ResultsChange](https://www.mongodb.com/docs/realm-sdks/kotlin/latest/library-base/io.realm.kotlin.notifications/-results-change/index.html)
`Flow` that you can iterate through with
[flow.collect()](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/collect.html).

In the following example, we iterate through a `Flow` of `Frog` objects:

```kotlin
// Get a Flow of all frogs in the database
val allFrogsQuery = realm.query<Frog>()
val frogsFlow: Flow<ResultsChange<Frog>> = allFrogsQuery.asFlow()

// Iterate through the Flow with 'collect()'
val frogsObserver: Deferred<Unit> = async {
    frogsFlow.collect { results ->
        when (results) {
            is InitialResults<Frog> -> {
                for (frog in results.list) {
                    Log.v("Frog: $frog")
                }
            }
            else -> { /* no-op */ }
        }
    }
}

// ... Later, cancel the Flow, so you can safely close the database
frogsObserver.cancel()
realm.close()

```

> **TIP:**
> After generating a `Flow` from a query, you can register a notification
handler to listen for changes to the `ResultsChanges`. For more information,
refer to React to Changes - Kotlin SDK.
>
