![Realm](./images/logo.png)

[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/io/realm/kotlin/io.realm.kotlin.gradle.plugin/maven-metadata.xml.svg?colorB=ff6b00&label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/io.realm.kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.realm.kotlin/gradle-plugin?colorB=4dc427&label=Maven%20Central)](https://search.maven.org/artifact/io.realm.kotlin/gradle-plugin)
[![Kotlin](https://img.shields.io/badge/kotlin-1.7.20-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache-blue.svg)](https://github.com/realm/realm-kotlin/blob/master/LICENSE)


Realm is a mobile database that runs directly inside phones, tablets or wearables. 

This repository holds the source code for the Kotlin SDK for Realm, which runs on Kotlin Multiplatform and Android.

## Why Use Realm

* **Intuitive to Developers:** Realm’s object-oriented data model is simple to learn, doesn’t need an ORM, and lets you write less code.
* **Built for Mobile:** Realm is fully-featured, lightweight, and efficiently uses memory, disk space, and battery life.
* **Designed for Offline Use:** Realm’s local database persists data on-disk, so apps work as well offline as they do online.
* **[Device Sync](https://www.mongodb.com/atlas/app-services/device-sync)**: Makes it simple to keep data in sync across users, devices, and your backend in real-time. [Get started](http://mongodb.com/realm/register?utm_medium=github_atlas_CTA&utm_source=realm_kotlin_github) for free with a template application that includes a cloud backend and Sync.

# General Availability 

The Realm Kotlin SDK is GA.

Documentation can be found [here](https://docs.mongodb.com/realm/sdk/kotlin-multiplatform/).

Sample projects can be found [here](https://github.com/realm/realm-kotlin-samples).

If you are upgrading from a previous beta release of Realm Kotlin, please see the [CHANGELOG](CHANGELOG.md) for the full list of changes.

If you are migrating from [Realm Java](https://github.com/realm/realm-java), please see the [Migration Guide](https://www.mongodb.com/docs/realm/sdk/kotlin/migrate-from-java-sdk-to-kotlin-sdk/).


# Usage

## Installation

Installation differs slightly depending on the type of project and whether or not you are using Device Sync. See the details in the documentation:

* [Android](https://www.mongodb.com/docs/realm/sdk/kotlin/install/android/#installation)
* [Kotlin Multiplatform](https://www.mongodb.com/docs/realm/sdk/kotlin/install/kotlin-multiplatform/#installation)

## Define model

Start writing your database logic by first defining your model.

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
// use the RealmConfiguration.Builder() for more options
val configuration = RealmConfiguration.create(schema = setOf(Person::class, Dog::class)) 
val realm = Realm.open(configuration)
```

## Write

Persist some data by instantiating the model object and copying it into the open Realm instance.

```Kotlin
// plain old kotlin object
val person = Person().apply {
    name = "Carlo"
    dog = Dog().apply { name = "Fido"; age = 16 }
}

// Persist it in a transaction
realm.writeBlocking { // this : MutableRealm
    val managedPerson = copyToRealm(person)
}

// Asynchronous updates with Kotlin coroutines
CoroutineScope(context).async {
    realm.write { // this : MutableRealm
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
val personsByNameQuery: RealmQuery<Person> = realm.query<Person>("name = $0", "Carlo")
val filteredByName: RealmResults<Person> = personsByNameQuery.find()

// Person having a dog aged more than 7 with a name starting with 'Fi'
val filteredByDog = realm.query<Person>("dog.age > $0 AND dog.name BEGINSWITH $1", 7, "Fi").find()

// Observing changes with Coroutine Flows
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

Use the result of a query to delete from the database.

```Kotlin
// delete all Dogs
realm.writeBlocking {
    // Selected by a query
    val query = this.query<Dog>()
    delete(query)

    // From a query result
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

Query results are also observable, and like `RealmList` on each update, the inserted, changed and deleted indices are also provided.

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

## Groovy 
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

## Kotlin
```
// Global build.gradle

buildscript {
    dependencies {
        classpath("io.realm.kotlin:gradle-plugin:<VERSION>-SNAPSHOT")
    }
}

repositories {
    google()
    mavenCentral()
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
}

// Module build.gradle

sourceSets {
        val commonMain  by getting {
            dependencies {
                implementation("io.realm.kotlin:library-base:<VERSION>-SNAPSHOT")
            }
        }
}        

// Don't cache SNAPSHOT (changing) dependencies.
configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0,TimeUnit.SECONDS)
}
```

See [Config.kt](buildSrc/src/main/kotlin/Config.kt#L20txt) for the latest version number.

# Kotlin Memory Model and Coroutine compatibility

Realm Kotlin 1.3.0 and above *only* works with the new Kotlin Native memory model. This is also the default memory model from Kotlin 1.7.20 and onwards. This mean that you need the default Kotlin Coroutine library 1.6.0 and above and not the `-mt` variant, which have also been [deprecated](https://blog.jetbrains.com/kotlin/2021/12/introducing-kotlinx-coroutines-1-6-0/). 

See the `## Compatibility` section of the [CHANGELOG](CHANGELOG.md) for information about exactly which versions are compatible with a given version of Realm Kotlin.

When upgrading older projects, it is important to be aware that certain Gradle properties will control the memory model being used. So, if you have the Gradle properties below defined in your project. Make sure they are set to the values shown: 

```
kotlin.native.binary.memoryModel=experimental
kotlin.native.binary.freezing=disabled
```

See https://kotlinlang.org/docs/native-memory-manager.html for more details about the new memory model.


# Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for more details!


# Code of Conduct

This project adheres to the [MongoDB Code of Conduct](https://www.mongodb.com/community-code-of-conduct).
By participating, you are expected to uphold this code. Please report
unacceptable behavior to [community-conduct@mongodb.com](mailto:community-conduct@mongodb.com).


# License

Realm Kotlin is published under the [Apache 2.0 license](LICENSE).

This product is not being made available to any person located in Cuba, Iran, North Korea, Sudan, Syria or the Crimea region, or to any other person that is not eligible to receive the product under U.S. law.

<img style="width: 0px; height: 0px;" src="https://3eaz4mshcd.execute-api.us-east-1.amazonaws.com/prod?s=https://github.com/realm/realm-kotlin#README.md">
