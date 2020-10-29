![Realm](logo.png)

[![License](https://img.shields.io/badge/License-Apache-blue.svg)](https://github.com/realm/realm-kotlin/blob/master/LICENSE)

Realm is a mobile database that runs directly inside phones, tablets or wearables.
This repository holds the source code for the Kotlin version of Realm, which runs on Kotlin Multiplatform and Android.


# How to build:

## Prerequisits
- Swig

## Commands to build

```
git submodule update --init --recursive
cd test
./gradlew assemble
```
In Android Studio open the `test` project, which will open also the `realm-library` and the compiler projects


# Repository Guidelines

## Code Style

We use the offical [style guide](https://kotlinlang.org/docs/reference/coding-conventions.html) from Kotlin which is enforced using [ktlint](https://github.com/pinterest/ktlint).

```sh
# Call from root folder to check if code is compliant.
./gradlew ktlintCheck

# Call from root folder to automatically format all Kotlin code according to the code style rules.
./gradlew ktlintFormat
```

Note: klint does not allow group imports using `.*`. You can configure IntelliJ to disallow this by going to preferences `Editor > Code Style > Kotlin > Imports` and select "Use single name imports".
