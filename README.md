![Realm](./images/logo.png)

[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/io/realm/kotlin/io.realm.kotlin.gradle.plugin/maven-metadata.xml.svg?colorB=ff6b00&label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/io.realm.kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.realm.kotlin/gradle-plugin?colorB=4dc427&label=Maven%20Central)](https://search.maven.org/artifact/io.realm.kotlin/gradle-plugin)
[![License](https://img.shields.io/badge/License-Apache-blue.svg)](https://github.com/realm/realm-kotlin/blob/master/LICENSE)

Realm is a mobile database that runs directly inside phones, tablets or wearables.
This repository holds the source code for the Kotlin SDK for Realm, which runs on Kotlin Multiplatform and Android.

# Examples 

https://github.com/realm/realm-kotlin-samples

## Kotlin Multiplatform Sample

The folder `examples/kmm-sample` contains an example showing how to use Realm in a multiplatform
project, sharing code for using Realm in the `shared` module. The project is based on
`https://github.com/Kotlin/kmm-sample`.

# Documentation

https://docs.mongodb.com/realm/sdk/kotlin-multiplatform/

# Quick Start

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
val configuration = RealmConfiguration.with(schema = setOf(Person::class, Dog::class))
```

```Kotlin
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
realm.writeBlocking {
    val managedPerson = this.copyToRealm(person)
}

// asynchroneous updates with Kotlin coroutines
CoroutineScope(context).async {
    realm.write {
        val managedPerson = copyToRealm(person())
    }
}
```

## Query

The query language supported by Realm is inspired by Apple’s [NSPredicate](https://developer.apple.com/documentation/foundation/nspredicate), see more examples [here](https://docs.mongodb.com/realm-legacy/docs/javascript/latest/index.html#queries)

```Kotlin
// All Persons
val all = realm.objects<Person>()

// Person named 'Carlo'
val filteredByName = realm.objects<Person>().query("name = $0", "Carlo")

// Person having a dog aged more than 7 with a name starting with 'Fi'
val filteredByDog = realm.objects<Person>().query("dog.age > $0 AND dog.name BEGINSWITH $1", 7, "Fi")

// Observing for changes with Kotlin Coroutine Flows
CoroutineScope(context).async {
    filteredByName.observe().collect { result: RealmResults<Person> ->
        println("Realm updated: Number of persons is ${result.size}")
    }
}
```

## Update

```Kotlin
// Find the first Person without a dog
realm.objects<Person>().query("dog == NULL LIMIT(1)")
    .firstOrNull()
    ?.also { personWithoutDog ->
        // Add a dog in a transaction
        realm.writeBlocking {
            personWithoutDog.dog = Dog().apply { name = "Laika"; age = 3 }
        }
    }
```

## Delete

Use the result of a query to delete from the database
```Kotlin
// delete all Dogs
realm.writeBlocking {
    realm.objects<Dog>().delete()
}
```

Next: head to the full KMM [example](./examples/kmm-sample).  

### NOTE: The SDK doesn't currently support  `x86` - Please use an `x86_64` or `arm64` emulator/device
 
## The project is in Alpha. Features and API may change in future versions.

## Design documents

The public API of the SDK has not been finalized. Design discussions will happen in both Google Doc and this Github repository. Most bigger features will first undergo a design process that might not involve code. These design documents can be found using the following links:

* [Intial Project Description](https://docs.google.com/document/d/10adRFquingm_JgyjDhUzcYXIDJsDG2A1ldFw53GSVJQ/edit)
* [API Design Overview](https://docs.google.com/document/d/1RSPNO95wZAAojYlFwshSpLiuEu9ZqXptO58RDoPHKNc/edit)


# How to build locally:

## Prerequisites

- Swig. On Mac this can be installed using Homebrew: `brew install swig`.
- CMake 3.18.1. Can be installed through the Android SDK Manager.
- Java 11.

## Commands to build from source

```
git submodule update --init --recursive
cd packages
./gradlew assemble
```
In Android Studio open the `test` project, which will open also the `realm-library` and the compiler projects

You can also run tests from the commandline:

```
cd test
./gradlew connectedAndroidTest
./gradlew macosTest
```

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


# Repository Guidelines

## Branch Strategy

We have three branches for shared development: `master`, `releases` and `next-major`. Tagged releases are only made from `releases`.

`master`:

* Target branch for new features.
* Cotains the latest publishable state of the SDK.
* [SNAPSHOT releases](#using-snapshots) are being created for every commit.

`releases`:

* All tagged releases are made from this branch.
* Target branch for bug fixes.
* Every commit should be merged back to master `master`.
* Minor changes (e.g. to documentation, tests, and the build system) may not affect end users but should still be merged to `releases` to avoid diverging too far from `master` and to reduce the likelihood of merge conflicts.

`next-major`:

* Target branch for breaking changes that would result in a major version bump.


Note: We currently only have the `master` branch, as no tagged releases have been made yet.

## Code Style

We use the offical [style guide](https://kotlinlang.org/docs/reference/coding-conventions.html) from Kotlin which is enforced using [ktlint](https://github.com/pinterest/ktlint) and [detekt](https://github.com/detekt/detekt).

```sh
# Call from root folder to check if code is compliant.
./gradlew ktlintCheck
./gradlew detekt

# Call from root folder to automatically format all Kotlin code according to the code style rules.
./gradlew ktlintFormat
```

Note: ktlint does not allow group imports using `.*`. You can configure IntelliJ to disallow this by going to preferences `Editor > Code Style > Kotlin > Imports` and select "Use single name imports".

## Multiplatform source layout

The multiplatform source hierarchy is structured like this:

```
- commonMain
  ├── jvm
  │   ├── androidMain
  │   └── jvmMain
  └── native
      └── darwin
          ├── ios
          |   ├── iosArm64Main
          |   └── iosX64Main
          └── macosX64Main
```

All source sets ending with `Main` is platform specific source sets, while the others are intermediate source sets shared between multiple targets. Only exception is `commonMain` which is kept to follow the Kotlin MPP gradle convention.

It is currently not possible to enable hierarchical setup due to various issues rendering the IDE unable to resolve common symbols, so for now we are just adding shared source sets to the individual platform specific targets they belong to. (Issues to track: https://youtrack.jetbrains.com/issue/KT-48153, https://youtrack.jetbrains.com/issue/KT-42466, https://youtrack.jetbrains.com/issue/KT-40975, see description of https://github.com/realm/realm-kotlin/pull/370 for details).

All platform differentiated implementations are kept in `platform`-packages with their current package hierarchy, to make it easier to keep track of the level of platform differentiation.

## Writing Tests

Currently all unit tests should be place in the `test/` project instead of `packages/library`. The reason for this is that we need to apply the Realm Compiler Plugin to the tests and this introduces a circular dependency if the tests are in `library`.

Inside `tests/` there are 3 locations the files can be placed in:

* `test/src/commonTest`
* `test/src/androidTest`
* `test/src/macosTest`

Ideally all shared tests should be in `commonTest` with specific platform tests in `androidTest`/`macosTest`. However IntelliJ does not yet allow you to run common tests on Android from within the IDE](https://youtrack.jetbrains.com/issue/KT-46452), so we
are using the following work-around:

1) All "common" tests should be placed in the `test/src/androidtest/kotlin/io/realm/shared` folder. They should be written using only common API's. I'e. use Kotlin Test, not JUnit. This `io.realm.shared` package should only contain tests we plan to eventually move to `commonTest`.


2) The `macosTest` shared tests would automatically be picked up from the `androidTests` as it is symlinked to `test/src/androidtest/kotlin/io/realm/shared`.


3) This allows us to run and debug unit tests on both macOS and Android. It is easier getting the imports correctly using the macOS sourceset as the Android code will default to using JUnit.
 

All platform specific tests should be placed outside the `io.realm.shared` package, the default being `io.realm`.

## Defining dependencies

All dependency versions and other constants we might want to share between projects are defined inside the file 
`buildSrc/src/main/kotlin/Config.kt`. Any new dependencies should be added to this file as well, so we only have one
location for these.

## Debugging Kotlin/Native Tests

- Location of the kexe file that contains this test - make sure to compile the test beforehand:
`test/build/bin/macos/debugTest/test.kexe`
- Open:
`lldb test/build/bin/macos/debugTest/test.kexe`
- Set breakpoints, e.g.:
`breakpoint set --file realm_coordinator.cpp --line 288`
- Run ONLY the test you want:
`r --gtest_filter="io.realm.MigrationTests.deleteOnMigration"`
- Step into:
`s`
- Step over:
`n`
- Step out:
`finish`

## Contributing

We love contributions to Realm! If you'd like to contribute code, documentation, or any other improvements, please [file a Pull Request](https://github.com/realm/realm-kotlin/pulls) on our GitHub repository. Make sure to accept our CLA:

### CLA

We welcomes all contributions! The only requirement we have is that, like many other projects, we need to have a [Contributor License Agreement](https://en.wikipedia.org/wiki/Contributor_License_Agreement) (CLA) in place before we can accept any external code. Our own CLA is a modified version of the Apache Software Foundation’s CLA.

[Please submit your CLA electronically using our Google form](https://docs.google.com/forms/d/e/1FAIpQLSeQ9ROFaTu9pyrmPhXc-dEnLD84DbLuT_-tPNZDOL9J10tOKQ/viewform) so we can accept your submissions. The GitHub username you file there will need to match that of your Pull Requests. If you have any questions or cannot file the CLA electronically, you can email help@realm.io.

## Code of Conduct

This project adheres to the [MongoDB Code of Conduct](https://www.mongodb.com/community-code-of-conduct).
By participating, you are expected to uphold this code. Please report
unacceptable behavior to [community-conduct@mongodb.com](mailto:community-conduct@mongodb.com).

