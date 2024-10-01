# Contributing to Realm Kotlin

## CLA

We welcomes all contributions! The only requirement we have is that, like many other projects, we need to have a [Contributor License Agreement](https://en.wikipedia.org/wiki/Contributor_License_Agreement) (CLA) in place before we can accept any external code. Our own CLA is a modified version of the Apache Software Foundation’s CLA.

[Please submit your CLA electronically using our Google form](https://docs.google.com/forms/d/e/1FAIpQLSeQ9ROFaTu9pyrmPhXc-dEnLD84DbLuT_-tPNZDOL9J10tOKQ/viewform) so we can accept your submissions. The GitHub username you file there will need to match that of your Pull Requests. If you have any questions or cannot file the CLA electronically, you can email help@realm.io.


## How to build locally

### Prerequisites

- Swig 4.2.0 or above. On Mac this can be installed using Homebrew: `brew install swig`.
- Ccache. On Mac this can be installed using Homebrew: `brew install ccache`.
- CMake 3.18.1 or above. Can be installed through the Android SDK Manager.
- Java 11.
- Define environment variables:
  - `ANDROID_HOME`
  - `JAVA_HOME`
  - `NDK_HOME`

### Obtaining the source code 

Checkout repo:
```sh
git clone --recursive  https://github.com/realm/realm-kotlin.git 
```

### Windows support

The repository can be built on Windows, although only for the JVM and Android targets. Beware of the following requirements:

- The repository contains symbolic links that needs to be preserved, see e.g.: https://stackoverflow.com/questions/5917249/git-symbolic-links-in-windows

### Linux support

This repository does currently not support building on Linux from the source code. 


### Building and running tests

The SDK and tests modules are located in the same Gradle project in the `packages` folder and can 
be developed and tested as a single project. For details on publishing and running tests against 
Maven artifacts see the [Running tests against Maven artifacts](#running-tests-against-maven-artifacts)-section.

The tests are triggered from the IDE or by triggering the specific test tasks across the various
platforms with:
```sh
cd packages
./gradlew :test-base:jvmTest :test-base:connectedAndroidTest :test-base:macosTest :test-base:iosTest

```
You can also the test across all modules on the various platforms with
```sh
cd packages
./gradlew jvmTest connectedAndroidTest macosTest iosTest
```
But this will also trigger tests in the SDK modules.

#### Triggering tests from Android Studio
* Use Android Studio Dolphin or a later version.
* Go to `Preferences > Build, Execution, Deployment > Build Tools > Gradle`.
* Under `Gradle JDK`, select the JDK 11 that you installed (not the embedded version).

#### Emulator
* Create a virtual device through Android Studio Device Manager.
* Select a system image that does **not** use a `Google APIs` target (usually found under `Arm Images` or `Other Images`). This is to allow root access to the file system.
* When verifying the configuration, select `Show Advanced Settings` and set `RAM` and `Internal Storage` to at least 2GB, and `SD card` to 1GB.
* Once created, enable root access from the terminal:
```sh
# Enable root acces
adb root

# Check if it works
# Enter file system of emulator
adb shell
<your_emulator>:/ > cd data/
<your_emulator>:/ > exit
```

### Running tests against Maven artifacts

When developing or running the test modules against Maven artifacts the SDK dependencies must first
be published and available through a Maven repository. You can publish the SDK modules to a Maven
repository in a local folder using the default local and test against these using the following 
commands:

```sh
cd packages
./gradlew publishAllPublicationsToTestRepository
./gradlew -PincludeSdkModules=false jvmTest connectedAndroidTest macosTest iosTest 
```

For a detailed description of the project setup see
the following [Advanced Project Setup](#advanced-project-setup)-section

### Advanced project setup

The overall setup of the project is done to support simultaneous development of the SDK and test,
while still selectively allowing to run tests against Maven artifacts. This is why the tests are
separated into separate Gradle modules. Due to [issues]((https://youtrack.jetbrains.com/issue/KTIJ-15775/MPP-IDE-Lots-of-red-code-unresolved-references-with-HMPP-and-composite-build)) with IntelliJ/Android Studio
not being able to resolve symbols in Kotlin Multiplatform projects in Composite Gradle projects
the various `test-X` modules are placed inside the `packages` projects and only applies the compiler
plugin to test modules instead of applying our top-level gradle plugin.

To support the various advanced scenarios, the project setup is controlled by the following Gradle 
properties:
```sh
includeSdkModules=true/false     # defaults to true
includeTestModules=true/false    # defaults to true
```
These will control whether or not the SDK (non-test) modules and `test-X` modules will be included 
in the top level `packages` Gradle project. 

The default is to include both the SDK modules and the test modules so that the SDK and test 
modules can be developed and tested continuously in one IDE/Gradle project. This uses project 
dependencies and supports incremental compilation without tedious steps to publish to local 
Maven repositories.

For testing against Maven artifacts you can publish the SDK artifacts to a local folder with 
```sh
./gradlew publishAllPublicationsToTestRepository
```
After which the tests can be executed against the Maven artifacts with 
```sh
./gradlew -PincludeSdkModules=false jvmTest connectedAndroidTest macosTest iosTest 
```
The location of the local Maven repository can be customized with the Gradle property
```sh
testRepository=<path relative to 'packages'>        # defaults to 'build/m2-buildrepo'
```

> **NOTE:** For the above schema to work all test modules should use full Maven coordinate for SDK
dependencies. These will be substituted with local project dependencies for any module included in
the project setup.

### Integration tests

Besides the normal SDK test the repository includes a number of integration test projects that acts
as full consuming test projects. They are located in:

```sh
./integration-tests
```

All these projects requires the SDK modules to be publish to the default local `testRepository` with

```sh
cd packages
./gradlew publishAllPublicationsToTestRepository
```

After that the various integration test projects can be tested with, ex.:

```sh
cd integration-tests/gradle-plugin-test
./gradlew integrationTest
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

A pre-push git hook that automatically will perform these checks is available. You can configure it with the following command:

```sh
git config core.hooksPath .githooks
```

> **Note:** ktlint does not allow group imports using `.*`. You can configure IntelliJ to disallow this by going to preferences `Editor > Code Style > Kotlin > Imports` and select "Use single name imports".

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

All platform differentiated implementations are kept in `platform`-packages with their current package hierarchy, to make it easier to keep track of the level of platform differentiation.


## Test organization 

Inside the various `packages/test-X/` modules there are 3 locations the files can be placed in:

* `packages/test-base/src/commonTest`
* `package/test-base/src/androidAndroidTest`
* `package/test-base/src/nativeDarwinTest` (macOS)

Ideally all shared tests should be in `commonTest` with specific platform tests in `androidAndroidTest`/`nativeDarwinTest`. However IntelliJ does [not yet allow you to run common tests on Android from within the IDE](https://youtrack.jetbrains.com/issue/KT-46452), so we
are using the following work-around:

1) All "common" tests should be placed in the `packages/test-X/src/androidAndroidTest/kotlin/io/realm/test/shared` folder. They should be written using only common API's. I.e. use Kotlin Test, not JUnit. This `io.realm.shared` package should only contain tests we plan to eventually move to `commonTest`.


2) The `nativeDarwinTest` (macOS) shared tests would automatically be picked up from the `androidAndroidTest` as it is symlinked to `packages/test-X/src/androidAndroidTest/kotlin/io/realm/test/shared`.


3) This allows us to run and debug unit tests on both macOS and Android. It is easier getting the imports correctly using the macOS sourceset as the Android code will default to using JUnit.
 

All platform specific tests should be placed outside the `io.realm.test.shared` package, the default being `io.realm.test`.


## Dependencies versions

All dependency versions and other constants we might want to share between projects are defined inside the file 
`buildSrc/src/main/kotlin/Config.kt`. Any new dependencies should be added to this file as well, so we only have one
location for these.


## Debugging Kotlin/Native Tests

- Location of the kexe file that contains this test - make sure to compile the test beforehand:
`packages/test-base/build/bin/macos/debugTest/test.kexe`
- Open:
`lldb packages/test-base/build/bin/macos/debugTest/test.kexe`
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
