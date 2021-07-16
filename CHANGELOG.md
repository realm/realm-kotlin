## 0.4.1 (2021-07-16)

### Breaking Changes
* None.

### Enhancements
* None.

### Fixed
* Throw exception when violating primary key uniqeness constraint when importing objects with `copyToRealm`.
* Fix crash caused by premature release of frozen versions (`java.lang.RuntimeException: [18]: Access to invalidated Results objects`)

### Compatibility
* This release is compatible with Kotlin 1.5.10 and Coroutines 1.5.0.

### Internal
* None.


## 0.4.0 (2021-07-13)

This release contains a big departure in the architectural design of how Realm is currently implemented. At a high level it moves from "Thread-confined, Live Objects" to "Frozen Objects". The reasons for this shift are discussed [here](https://docs.google.com/document/d/1bGfjbKLD6DSBpTiVwyorSBcMqkUQWedAmmS_VAhL8QU/edit#heading=h.fzlh39twuifc).

At a high level this has a number of implications:

    1. Only one Realm instance (per `RealmConfiguration`) is needed across the entire application.
    2. The only reason for closing the Realm instance is if the Realm file itself needs to be deleted or compacted.
    3. Realm objects can be freely moved and read across threads.
    4. Changes to objects can only be observed through Kotlin Flows. Standard change listener support will come in a future release.
    5. In order to modify Realm Objects, they need to be "live". It is possible to convert a frozen object to a live object inside a
       write transaction using the `MutableRealm.findLatest(obj)` API. Live objects are not accessible outside write transactions.

This new architecture is intended to make it easier to consume and work with Realm, but at the same time, it also introduces a few caveats:

    1. Storing a strong reference to a Realm Object can cause an issue known as "Version pinning". Realm tracks the "distance" between the oldest known version and the latest. So if you store a reference for too long, when other writes are happening, Realm might run out of native memory and crash, or it can lead to an increased file size. It is possible to detect this problem by setting `RealmConfiguration.Builder.maxNumberOfActiveVersions()`. It can be worked around by copying the data out of the Realm and store that instead.

    2. With multiple versions being accessible across threads, it is possible to accidentally compare data from different versions. This could be a potential problem for certain business logic if two objects do not agree on a particular state. If you suspect this is an issue, a `version()` method has been added to all Realm Objects, so it can be inspected for debugging. Previously, Realms thread-confined objects guaranteed this would not happen.

    3. Since the threading model has changed, there is no longer a guarantee that running the same query twice in a row will return the same result. E.g. if a background write is executed between them, the result might change. Previously, this would have resulted in the same result as the Realm state for a particular thread would only update as part of the event loop.


### Breaking Changes
* The Realm instance itself is now thread safe and can be accessed from any thread.
* Objects queried outside write transactions are now frozen by default and can be freely read from any thread.
* As a consequence of the above, when a change listener fires, the changed data can only be observed in the new object received, not in the original, which was possible before this release.
* Removed `Realm.open(configuration: RealmConfiguration)`. Use the interchangeable `Realm(configuration: RealmConfiguration)`-constructor instead.
* Removed all `MutableRealm.create(...)`-variants. Use `MutableRealm.copyToRealm(instance: T): T` instead.

### Enhancements
* A `version()` method has been added to `Realm`, `RealmResults` and `RealmObject`. This returns the version of the data contained. New versions are obtained by observing changes to the object.
* `Realm.observe()`, `RealmResults.observe()` and `RealmObject.observe()` have been added and expose a Flow of updates to the object.
* Add support for suspending writes executed on the Realm Write Dispatcher with `suspend fun <R> write(block: MutableRealm.() -> R): R`
* Add support for setting background write and notification dispatchers with `RealmConfigruation.Builder.notificationDispatcher(dispatcher: CoroutineDispatcher)` and `RealmConfiguration.Builder.writeDispatcher(dispatcher: CoroutineDispatcher)`
* Add support for retrieving the latest version of an object inside a write transaction with `<T : RealmObject> MutableRealm.findLatests(obj: T?): T?`

### Fixed
* None.

### Compatibility
* This release is compatible with Kotlin 1.5.10 and Coroutines 1.5.0.

### Internal
* Updated `com.gradle.plugin-publish` to 0.15.0.
* Updated to Realm Core commit: 4cf63d689ba099057345f122265cbb880a8eb19d.
* Updated to Android NDK: 22.1.7171670.
* Introduced usage of `kotlinx.atomicfu`: 0.16.1.


## 0.3.2 (2021-07-06)

### Breaking Changes
* None.

### Enhancements
* None.

### Fixed
* [Bug](https://github.com/realm/realm-kotlin/issues/334) in `copyToRealm` causing a `RealmList` not to be saved as part of the model.

### Compatibility
* This release is compatible with Kotlin 1.5.10 and Coroutines 1.5.0.

### Internal
* None.


## 0.3.1 (2021-07-02)

### Breaking Changes
* None.

### Enhancements
* None.

### Fixed
* Android Release build variant (AAR) was stripped from all classes due to presence of `isMinifyEnabled` flag in the library module. The flag is removed now.


### Compatibility
* This release is compatible with Kotlin 1.5.10 and Coroutines 1.5.0.

### Internal
* None.


## 0.3.0 (2021-07-01)

### Breaking Changes
* None.

### Enhancements
* [Support Apple Release builds](https://github.com/realm/realm-kotlin/issues/142).
* Enabling [shrinker](https://github.com/realm/realm-kotlin/issues/293) for Android Release builds.
* Added support for `RealmList` as supported field in model classes. A `RealmList` is used to model one-to-many relationships in a Realm object.
* Schema migration is handled automatically when adding or removing a property or class to the model without specifying a `schemaVersion`.
If a class or column is renamed you need to set a greater `schemaVersion` to migrate the Realm (note: currently renaming will not copy data to the new column). Alternatively `deleteRealmIfMigrationNeeded` could be set to (without setting `schemaVersion`) to delete the Realm file if an automatic migration is not possible. Fixes [#284](https://github.com/realm/realm-kotlin/issues/284).

### Fixed
* None.

### Compatibility
* This release is compatible with Kotlin 1.5.10 and Coroutines 1.5.0.

### Internal
* None.


## 0.2.0 (2021-06-09)

### Breaking Changes
* The Realm Kotlin Gradle plugin has changed name from `realm-kotlin` to `io.realm.kotlin` to align with Gradle Plugin Portal requirements.

### Enhancements
* The Realm Kotlin Gradle plugin is now available on Gradle Plugin Portal and can be used with the Plugin DSL and `gradlePluginPortal()` as the buildscript repository. A minimal setup of using this approach can be found [here](https://plugins.gradle.org/plugin/io.realm.kotlin).

### Fixed
* None.

### Compatibility
* This release is compatible with Kotlin 1.5.10 and Coroutines 1.5.0.

### Internal
* Updated to Realm Core commit: ed9fbb907e0b5e97e0e2d5b8efdc0951b2eb980c.


## 0.1.0 (2021-05-07)

This is the first public Alpha release of the Realm Kotlin SDK for Android and Kotlin Multiplatform.

A minimal setup for including this library looks like this:

```
// Top-level build.gradle file
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("io.realm.kotlin:gradle-plugin:0.1.0")
    }
}

allprojects {
    repositories {
    	mavenCentral()
    }
}

// Project build.gradle file
// Only include multiplatform if building a multiplatform project.
plugins {
	kotlin("multiplatform")
	id("com.android.library")
	id("realm-kotlin")
}
```

See the [README](https://github.com/realm/realm-kotlin#readme) for more information.

Please report any issues [here](https://github.com/realm/realm-kotlin/issues/new).
