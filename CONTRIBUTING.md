# Contributing to Realm Kotlin

## CLA

We welcomes all contributions! The only requirement we have is that, like many other projects, we need to have a [Contributor License Agreement](https://en.wikipedia.org/wiki/Contributor_License_Agreement) (CLA) in place before we can accept any external code. Our own CLA is a modified version of the Apache Software Foundation’s CLA.

[Please submit your CLA electronically using our Google form](https://docs.google.com/forms/d/e/1FAIpQLSeQ9ROFaTu9pyrmPhXc-dEnLD84DbLuT_-tPNZDOL9J10tOKQ/viewform) so we can accept your submissions. The GitHub username you file there will need to match that of your Pull Requests. If you have any questions or cannot file the CLA electronically, you can email help@realm.io.


# How to build locally:


### Prerequisites

- Swig. On Mac this can be installed using Homebrew: `brew install swig`.
- CMake 3.18.1 or above. Can be installed through the Android SDK Manager.
- Java 11.


### Commands to build from source

Checkout repo:
```
git clone --recursive  https://github.com/realm/realm-kotlin.git 
```

Build library:
```
git submodule update --init --recursive
cd packages
./gradlew assemble
```

Publish packages to `mavenLocal()`. Default location is `~/.m2` on Mac:
```
cd packages
./gradlew publishToMavenLocal
```

In Android Studio open the `/test` project, which will open also all all required modules under `/packages`. 

You can also run tests from the commandline:

```
cd test
./gradlew connectedAndroidTest
./gradlew macosTest
```

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

Currently all unit tests should be place in the `test/` project instead of `packages/library-base`. The reason for this is that we need to apply the Realm Compiler Plugin to the tests and this introduces a circular dependency if the tests are in `library-base`.

Inside `test/` there are 3 locations the files can be placed in:

* `<base/sync>/src/commonTest`
* `<base/sync>/src/androidTest`
* `<base/sync>/src/macosTest`

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


