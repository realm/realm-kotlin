![Realm](./images/logo.png)

[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/io/realm/kotlin/io.realm.kotlin.gradle.plugin/maven-metadata.xml.svg?colorB=ff6b00&label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/io.realm.kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.realm.kotlin/gradle-plugin?colorB=4dc427&label=Maven%20Central)](https://search.maven.org/artifact/io.realm.kotlin/gradle-plugin)
[![Kotlin](https://img.shields.io/badge/kotlin-1.6.10-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache-blue.svg)](https://github.com/realm/realm-kotlin/blob/master/LICENSE)

Realm is a mobile database that runs directly inside phones, tablets or wearables.
This repository holds the source code for the Kotlin SDK for Realm, which runs on Kotlin Multiplatform and Android.

# Beta Notice

The Realm Kotlin SDK is in Beta for local database support, with [MongoDB Realm](https://www.mongodb.com/realm) and Sync API's in Alpha.


# Resources

* 🧬 Samples: https://github.com/realm/realm-kotlin-samples
* 📘 Documentation: https://docs.mongodb.com/realm/sdk/kotlin-multiplatform/


# Multiplatform Quick Start

## Prerequisite

Start a new [KMM](https://kotlinlang.org/docs/mobile/create-first-app.html) project. 

## Setup

*See [Config.kt](buildSrc/src/main/kotlin/Config.kt#L2txt) or the [realm-kotlin releases](https://github.com/realm/realm-kotlin/releases) for the latest version number.*

- In the shared module (`shared/build.gradle.kts`), apply the `io.realm.kotlin` plugin and specify the dependency in the common source set.

```Gradle
plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("io.realm.kotlin") version "<VERSION>"
}

kotlin {
  sourceSets {
      val commonMain by getting {
          dependencies {
              implementation("io.realm.kotlin:library-base:<VERSION>")
          }
      }
}
```

- If you use the model classes or query results inside the Android module(`androidApp/build.gradle.kts`) you need to add a compile time dependency as follows:

```Gradle
dependencies {
    compileOnly("io.realm.kotlin:library-base:<VERSION>")
}
```
## Define model

Start writing your shared database logic in the shared module by defining first your model

```Kotlin
class Person : RealmObject {
    var name: String = "Foo"
    var dog: Dog? = null
}

class Dog : RealmObject {
    var name: String = ""
    var age: Int = 0
}
```

## Open Database

Define a _RealmConfiguration_ with the database schema, then open the Realm using it.

```Kotlin
val configuration = RealmConfiguration.with(schema = setOf(Person::class, Dog::class)) // use the RealmConfiguration.Builder for more options
val realm = Realm.open(configuration)
```

## Write

Persist some data by instantiating the data objects and copying it into the open Realm instance

```Kotlin
// plain old kotlin object
val person = Person().apply {
    name = "Carlo"
    dog = Dog().apply { name = "Fido"; age = 16 }
}

// persist it in a transaction
realm.writeBlocking { // this : MutableRealm
    val managedPerson = this.copyToRealm(person)
}

// asynchronous updates with Kotlin coroutines
CoroutineScope(context).async {
    realm.write {
        val managedPerson = copyToRealm(person)
    }
}
```

## Query

The query language supported by Realm is inspired by Apple’s [NSPredicate](https://developer.apple.com/documentation/foundation/nspredicate), see more examples [here](https://docs.mongodb.com/realm/sdk/kotlin/realm-database/query-language/)

```Kotlin
// All persons
import io.realm.kotlin.ext.query

val all = realm.query<Person>().find()

// Persons named 'Carlo'
val personsByNameQuery = realm.query<Person>("name = $0", "Carlo")
val filteredByName = personsByNameQuery.find()

// Person having a dog aged more than 7 with a name starting with 'Fi'
val filteredByDog = realm.query<Person>("dog.age > $0 AND dog.name BEGINSWITH $1", 7, "Fi").find()

// Observing for changes with Coroutine Flows
CoroutineScope(context).async {
    personsByNameQuery.asFlow().collect { result: ResultsChange<Person> ->
        println("Realm updated: Number of persons is ${result.list.size}")
    }
}
```

## Update

```Kotlin
// Find the first Person without a dog
realm.query<Person>("dog == NULL LIMIT(1)")
    .first()
    .find()
    ?.also { personWithoutDog ->
        // Add a dog in a transaction
        realm.writeBlocking {
            findLatest(personWithoutDog)?.dog = Dog().apply { name = "Laika"; age = 3 }
        }
    }
```

## Delete

Use the result of a query to delete from the database
```Kotlin
// delete all Dogs
realm.writeBlocking {
    // Selected by a query
    val query = this.query<Dog>()
    delete(query)

    // From a results
    val results = query.find()
    delete(results)

    // From individual objects
    results.forEach { delete(it) }
}
```

## Observing data changes

Realm support asynchronous observers on all its data structures.

### Realm

A Realm can be observed globally for changes on its data.

```Kotlin
realm.asFlow()
    .collect { realmChange: RealmChange<Realm> ->
        when (realmChange) {
            is InitialRealm<*> -> println("Initial Realm")
            is UpdatedRealm<*> -> println("Realm updated")
        }
    }
```

### RealmObject

Realm objects can be observed individually. A list of the changed field names is provided on each update.

```Kotlin
person.asFlow().collect { objectChange: ObjectChange<Person> ->
        when (objectChange) {
            is InitialObject -> println("Initial object: ${objectChange.obj.name}")
            is UpdatedObject -> 
                println("Updated object: ${objectChange.obj.name}, changed fields: ${objectChange.changedFields.size}")
            is DeletedObject -> println("Deleted object")
        }
    }
```

### RealmLists

Realm data structures can be observed too. On `RealmList` on each update you receive what positions were inserted, changed or deleted.

```Kotlin
person.addresses.asFlow()
        .collect { listChange: ListChange<String> ->
            when (listChange) {
                is InitialList -> println("Initial list size: ${listChange.list.size}")
                is UpdatedList -> 
                    println("Updated list size: ${listChange.list.size} insertions ${listChange.insertions.size}")
                is DeletedList -> println("Deleted list")
            }
        }
```

### RealmQuery

Query results are also observable, and like `RealmList` on each update the inserted, changed and deleted indices are also provided.

```Kotlin
realm.query<Person>().asFlow()
    .collect { resultsChange: ResultsChange<Person> ->
        when (resultsChange) {
            is InitialResults -> println("Initial results size: ${resultsChange.list.size}")
            is UpdatedResults -> 
                println("Updated results size: ${resultsChange.list.size} insertions ${resultsChange.insertions.size}")
        }
    }
```

### RealmSingleQuery

Single element queries allow observing a `RealmObject` that might not be in the realm.

```Kotlin
realm.query<Person>("name = $0", "Carlo").first().asFlow()
    .collect { objectChange: SingleQueryChange<Person> ->
        when (objectChange) {
            is PendingObject -> println("Pending object")
            is InitialObject -> println("Initial object: ${objectChange.obj.name}")
            is UpdatedObject -> 
                println("Updated object: ${objectChange.obj.name}, changed fields: ${objectChange.changedFields.size}")
            is DeletedObject -> println("Deleted object")
        }
    }
```

Next: head to the full KMM [example](https://github.com/realm/realm-kotlin-samples/tree/main/Bookshelf).  


# Using Snapshots

If you want to test recent bugfixes or features that have not been packaged in an official release yet, you can use a **-SNAPSHOT** release of the current development version of Realm via Gradle, available on [Maven Central](https://oss.sonatype.org/content/repositories/snapshots/io/realm/kotlin/)

```
// Global build.gradle
buildscript {
    repositories {
        google()
        mavenCentral()
        maven {
            url 'https://oss.sonatype.org/content/repositories/snapshots'
        }
    }
    dependencies {
        classpath 'io.realm.kotlin:gradle-plugin:<VERSION>'
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven {
            url 'https://oss.sonatype.org/content/repositories/snapshots'
        }
    }
}

// Module build.gradle

// Don't cache SNAPSHOT (changing) dependencies.
configurations.all {
    resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
}

apply plugin: "io.realm.kotlin"
```

See [Config.kt](buildSrc/src/main/kotlin/Config.kt#L2txt) for the latest version number.


## Kotlin Memory Model and Coroutine compatibility

Realm Kotlin is implemented against Kotlin's default memory model (the old one), but still supports running with the new memory model if enabled in the consuming project. See https://github.com/JetBrains/kotlin/blob/master/kotlin-native/NEW_MM.md#switch-to-the-new-mm for details on enabled the new memory model.

By default Realm Kotlin depends and requires you to run with Kotlin Coroutines version `1.6.0-native-mt`. To use Realm Kotlin with the non-`native-mt` version of Coroutines you will have to enable the new memory model and also disables our internal freezing to accomodate the new freeze transparency for Coroutine 1.6.0. 

```
kotlin.native.binary.memoryModel=experimental
kotlin.native.binary.freezing=disabled
```

See https://github.com/JetBrains/kotlin/blob/master/kotlin-native/NEW_MM.md#unexpected-object-freezing for more details.


## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for more details!


## Code of Conduct

This project adheres to the [MongoDB Code of Conduct](https://www.mongodb.com/community-code-of-conduct).
By participating, you are expected to uphold this code. Please report
unacceptable behavior to [community-conduct@mongodb.com](mailto:community-conduct@mongodb.com).


## License

Realm Kotlin is published under the [Apache 2.0 license](LICENSE).

This product is not being made available to any person located in Cuba, Iran, North Korea, Sudan, Syria or the Crimea region, or to any other person that is not eligible to receive the product under U.S. law.

<img style="width: 0px; height: 0px;" src="https://3eaz4mshcd.execute-api.us-east-1.amazonaws.com/prod?s=https://github.com/realm/realm-kotlin#README.md">
