## 1.1.0 (YYYY-MM-DD)

### Breaking Changes
* [Sync] Changed default recovery mode from `DiscardUnsyncedChangesStrategy` to `RecoverOrDiscardUnsyncedChangesStrategy`

### Enhancements
* [Sync] Introduced `RecoverUnsyncedChangesStrategy`, an alternative automatic client reset strategy that tries to automatically recover any unsynced data from the client.
* [Sync] Introduced `RecoverOrDiscardUnsyncedChangesStrategy`, an alternative automatic client reset strategy that tries to automatically recover any unsynced data from the client, and discards any unsynced data if recovery is not possible. This is now the default policy.

### Compatibility
* This release is compatible with:
  * Kotlin 1.6.10 and above.
  * Coroutines 1.6.0-native-mt. Also compatible with Coroutines 1.6.0 but requires enabling of the new memory model and disabling of freezing, see https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility for details on that.
  * AtomicFu 0.17.0.
* Minimum Gradle version: 6.1.1.  
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* Updated to Realm Core 12.3.0, commit c4e3f87fe6a93171590462ad985b4974bb5a9c26.


## 1.0.1 (2022-07-07)

### Breaking Changes
* None.

### Enhancements
* Added support for `ByteArray`. ([#584](https://github.com/realm/realm-kotlin/issues/584))

### Fixed
* Fixed JVM memory leak when passing string to C-API. (Issue [#890](https://github.com/realm/realm-kotlin/issues/890))
* Fixed crash present on release-mode apps using Sync due to missing Proguard exception for `ResponseCallback`.
* The compiler plugin did not set the generic parameter correctly for an internal field inside model classes. This could result in other libraries that operated on the source code throwing an error of the type: `undeclared type variable: T`. (Issue [#901](https://github.com/realm/realm-kotlin/issues/901))
* String read from a realm was mistakenly treated as zero-terminated, resulting in strings with `\0`-characters to be truncated when read. Inserting data worked correctly. (Issue [#911](https://github.com/realm/realm-kotlin/issues/911))
* [Sync] Fix internal ordering of `EmailPasswordAuth.resetPassword(...)` arguments. (Issue [#885](https://github.com/realm/realm-kotlin/issues/885))
* [Sync] Sync error events not requiring a Client Reset incorrectly assumed they had to include a path to a recovery Realm file. (Issue [#895](https://github.com/realm/realm-kotlin/issues/895))

### Compatibility
* This release is compatible with:
  * Kotlin 1.6.10 and above.
  * Coroutines 1.6.0-native-mt. Also compatible with Coroutines 1.6.0 but requires enabling of the new memory model and disabling of freezing, see https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility for details on that.
  * AtomicFu 0.17.0.
* Minimum Gradle version: 6.1.1.  
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* None.


## 1.0.0 (2022-06-07)

### Breaking Changes
* Move all classes from package `io.realm` to `io.realm.kotlin`. This allows Realm Java and Realm Kotlin to be included in the same app without having class name conflicts. *WARNING:* While both libraries can be configured to open the same file, doing so concurrently is currently not supported and can lead to corrupted realm files.
* Updated default behavior for implicit import APIs (realm objects setters and list add/insert/set-operations) to update existing objects with similar primary key instead of throwing. (Issue [#849](https://github.com/realm/realm-kotlin/issues/849))
* Introduced `BaseRealmObject` as base interface of `RealmObject` and `DynamicRealmObject` to prepare for future embedded object support.
  * Most APIs accepts `BaseRealmObject` instead of `RealmObject`.
  * `DynamicRealmObject` no longer implements `RealmObject` but only `BaseRealmObject`
  * Besides the changes of base class of `DynamicRealmObject`, this should not require and code changes.
* Moved all modeling defining types to `io.realm.kotlin.types`
  * Moved `BaseRealmObject`, `RealmObject`, `EmbeddedObject`, `RealmList`, `RealmInstant` and `ObjectId` from `io.realm` to `io.realm.kotlin.types`
* Moved `RealmResults` from `io.realm` to `io.realm.kotlin.query`
* Reworked API for dynamic objects.
  * Support for unmanaged dynamic objects through `DynamicMutableRealmObject.create()`.
  * Replaced `DynamicMutableRealm.create()` with `DynamicMutableRealm.copyToRealm()` similar to `MutableRealm.copyToRealm()`.
* Moved `io.realm.MutableRealm.UpdatePolicy` to top-level class `io.realm.kotlin.UpdatePolicy` as it now also applies to `DynamicMutableRealm.copyToRealm()`.
* Deleted `Queryable`-interface and removed it from `RealmResults`.
* Moved extension methods on `BaseRealmObject`, `MutableRealm`, `TypedRealm`, `Realm` and `Iterable` from `io.realm` to `io.realm.kotlin.ext`
* Moved `io.realm.MutableRealm.UpdatePolicy` to top-level class `io.realm.UpdatePolicy` as it now also applies to `DynamicMutableRealm.copyToRealm()`
* All exceptions from Realm now has `RealmException` as their base class instead of `RealmCoreException` or `Exception`.
* Aligned factory methods naming. (Issue [#835](https://github.com/realm/realm-kotlin/issues/835))
  * Renamed `RealmConfiguration.with(...)` to `RealmConfiguration.create(...)`
  * Renamed `SyncConfiguration.with(...)` to `SyncConfiguration.create(...)`
  * Renamed `RealmInstant.fromEpochSeconds(...)` to `RealmInstant.from(...)`
* Reduced `DynamicMutableRealm` APIs (`copyToRealm()` and `findLatest()`) to only allow import and lookup of `DynamicRealmObject`s.

### Enhancements
* [Sync] Support for Flexible Sync through `Realm.subscriptions`. (Issue [#824](https://github.com/realm/realm-kotlin/pull/824))
* [Sync] Added support for `ObjectId` ([#652](https://github.com/realm/realm-kotlin/issues/652)). `ObjectId` can be used as a primary key in model definition.
* [Sync] Support for `SyncConfiguration.Builder.InitialData()`. (Issue [#422](https://github.com/realm/realm-kotlin/issues/422))
* [Sync] Support for `SyncConfiguration.Builder.initialSubscriptions()`. (Issue [#831](https://github.com/realm/realm-kotlin/issues/831))
* [Sync] Support for `SyncConfiguration.Builder.waitForInitialRemoteData()`. (Issue [#821](https://github.com/realm/realm-kotlin/issues/821))
* [Sync] Support for accessing and controlling the session state through `SyncSession.state`, `SyncSession.pause()` and `SyncSession.resume()`.
* [Sync] Added `SyncConfiguration.syncClientResetStrategy` which enables support for client reset via `DiscardUnsyncedChangesStrategy` for partition-based realms and `ManuallyRecoverUnsyncedChangesStrategy` for Flexible Sync realms.
* [Sync] Support `ObjectId` as a partition key.
* Support for embedded objects. (Issue [#551](https://github.com/realm/realm-kotlin/issues/551))
* Support for `RealmConfiguration.Builder.initialData()`. (Issue [#579](https://github.com/realm/realm-kotlin/issues/579))
* Preparing the compiler plugin to be compatible with Kotlin `1.7.0-RC`. (Issue [#843](https://github.com/realm/realm-kotlin/issues/843))
* Added `AppConfiguration.create(...)` as convenience method for `AppConfiguration.Builder(...).build()` (Issue [#835](https://github.com/realm/realm-kotlin/issues/835))

### Fixed
* Fix missing symbol (`___bid_IDEC_glbround`) on Apple silicon
* Creating a `RealmConfiguration` off the main thread on Kotlin Native could crash with `IncorrectDereferenceException`. (Issue [#799](https://github.com/realm/realm-kotlin/issues/799))
* Compiler error when using cyclic references in compiled module. (Issue [#339](https://github.com/realm/realm-kotlin/issues/339))

### Compatibility
* This release is compatible with:
  * Kotlin 1.6.10 and above.
  * Coroutines 1.6.0-native-mt. Also compatible with Coroutines 1.6.0 but requires enabling of the new memory model and disabling of freezing, see https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility for details on that.
  * AtomicFu 0.17.0.
* Minimum Gradle version: 6.1.1.  
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* Updated to Realm Core 12.1.0, commit f8f6b3730e32dcc5b6564ebbfa5626a640cdb52a.


## 0.11.1 (2022-05-05)

### Breaking Changes
* None.

### Enhancements
* None.

### Fixed
* Fix crash in list notification listener (Issue [#827](https://github.com/realm/realm-kotlin/issues/827), since 0.11.0)

### Compatibility
* This release is compatible with:
  * Kotlin 1.6.10 and above.
  * Coroutines 1.6.0-native-mt. Also compatible with Coroutines 1.6.0 but requires enabling of the new memory model and disabling of freezing, see https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility for details on that.
  * AtomicFu 0.17.0.
* Minimum Gradle version: 6.1.1.  
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* None.


## 0.11.0 (2022-04-29)

### Breaking Changes
* [Sync] `SyncConfiguration` and `SyncSession` have been moved to `io.realm.mongodb.sync`.
* [Sync] `EmailPasswordAuth` has been movedto `io.realm.mongodb.auth`.
* [Sync] Improved exception hierarchy for App and Sync exceptions. All sync/app exceptions now use `io.realm.mongodb.exceptions.AppException` as their top-level exception type. Many methods have more specialized exceptions for common errors that can be caught and reacted to. See `AppException` documentation for more details.
* [Sync] `SyncConfiguration.directory` is no longer available.
* [Sync] Removed `SyncConfiguration.partitionValue` as it exposed internal implementation details. It will be reintroduced at a later date.

### Enhancements
* [Sync] `EmailPasswordAuth` has been extended with support for: `confirmUser()`, `resendConfirmationEmail()`, `retryCustomConfirmation()`, `sendResetPasswordEmail()` and `resetPassword()`.
* [Sync] Support for new types of `Credentials`: `apiKey`, `apple`, `facebook`, `google` and `jwt`.
* [Sync] Support for the extension property `Realm.syncSession`, which returns the sync session associated with the realm.
* [Sync] Support for `SyncSession.downloadAllServerChanges()` and `SyncSession.uploadAllLocalChanges()`.
* [Sync] Support for `App.allUsers()`.
* [Sync] Support for `SyncConfiguration.with()`.
* [Sync] Support for `null` and `Integer` (along side already existing `String` and `Long`) partition values when using Partion-based Sync.
* [Sync] Support for `User.remove()`.
* [Sync] `AppConfiguration.syncRootDirectory` has been added to allow users to set the root folder containing all files used for data synchronization between the device and MongoDB Realm. (Issue [#795](https://github.com/realm/realm-kotlin/issues/795))
* Encrypted Realms now use OpenSSL 1.1.1n, up from v1.1.1g.

### Fixed
* Fix duplication of list object references when importing existing objects with `copyToRealm(..., updatePolicy = UpdatePolicy.ALL)` (Issue [#805](https://github.com/realm/realm-kotlin/issues/805))
* Bug in the encryption layer that could result in corrupted Realm files. (Realm Core Issue [#5360](https://github.com/realm/realm-core/issues/5360), since 0.10.0)

### Compatibility
* This release is compatible with:
  * Kotlin 1.6.10 and above.
  * Coroutines 1.6.0-native-mt. Also compatible with Coroutines 1.6.0 but requires enabling of the new memory model and disabling of freezing, see https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility for details on that.
  * AtomicFu 0.17.0.
* Minimum Gradle version: 6.1.1.  
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* Updated to Realm Core 11.15.0, commit 9544b48e52c49e0267c3424b0b92c2f5efd5e2b9.
* Updated to Ktor 1.6.8.
* Updated to Ktlint 0.45.2.
* Rename internal synthetic variables prefix to `io_realm_kotlin_`, so deprecated prefix `$realm$` is avoided.
* Using latest Kotlin version (EAP) for the `kmm-sample` app to test compatibility with the latest/upcoming Kotlin version.


## 0.10.2 (2022-04-01)

### Breaking Changes
* None.

### Enhancements
* None.

### Fixed
* Fix query syntax errors of seemingly correct query (Issue [#683](https://github.com/realm/realm-kotlin/issues/683))
* Fix error when importing lists with existing objects through `copyToRealm` with `UpdatePolicy.ALL` (Issue [#771](https://github.com/realm/realm-kotlin/issues/771))

### Compatibility
* This release is compatible with:
  * Kotlin 1.6.10.
  * Coroutines 1.6.0-native-mt. Also compatible with Coroutines 1.6.0 but requires enabling of the new memory model and disabling of freezing, see https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility for details on that.
  * AtomicFu 0.17.0.
* Minimum Gradle version: 6.1.1.  
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* None.

## 0.10.1 (2022-03-24)

### Breaking Changes
* None.

### Enhancements
* Reducing the binary size for Android dependency. (Issue [#216](https://github.com/realm/realm-kotlin/issues/216)).
* Using static c++ runtime library (stl) for Android. (Issue [#694](https://github.com/realm/realm-kotlin/issues/694)).

### Fixed
* Fix assignments to `RealmList`-properties on managed objects (Issue [#718](https://github.com/realm/realm-kotlin/issues/718))
* `iosSimulatorArm64` and `iosX64` cinterop dependencies were compiled with unnecessary additional architectures, causing a fat framework to fail with (Issue [#722](https://github.com/realm/realm-kotlin/issues/722))

### Compatibility
* This release is compatible with:
  * Kotlin 1.6.10.
  * Coroutines 1.6.0-native-mt. Also compatible with Coroutines 1.6.0 but requires enabling of the new memory model and disabling of freezing, see https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility for details on that.
  * AtomicFu 0.17.0.
* Minimum Gradle version: 6.1.1.  
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* None.


## 0.10.0 (2022-03-04)

### Breaking Changes
* `RealmConfiguration.Builder.path()` has been replaced by `RealmConfiguration.Builder.directory()`, which can be combined with `RealmConfiguration.Builder.name()` to form the full path. (Issue [#346](https://github.com/realm/realm-kotlin/issues/346))
* `Realm.observe()` and `RealmObject.observe()` have been renamed to `asFlow()`.
* `RealmObject.asFlow` will throw `UnsupportedOperationException` instead of `IllegalStateException` if called on a live or dynamic object in a write transaction or in a migration.
* `RealmObject.asFlow` will throw `UnsupportedOperationException` instead of `IllegalStateException` if called on a live or dynamic object in a write transaction or in a migration.
* Removed `RealmObject.delete()` and `RealmResults.delete()`. All objects, objects specified by queries and results must be delete through `MutableRealm.delete(...)` and `DynamicMutableRealm.delete(...).
* Removed default empty schema argument for `RealmConfiguration.Builder(schema = ... )` and `SyncConfiguration.Builder(..., schema= ... )` as all configuraitons require a non-empty schema.
* Removed `RealmConfiguration.Builder.schema()`. `RealmConfiguration.Builder(schema = ...)` should be used instead.

### Enhancements
* Add support for Gradle Configuration Cache.
* Improved exception message when attempting to delete frozen objects. (Issue [#616](https://github.com/realm/realm-kotlin/issues/616))
* Added `RealmConfiguration.Builder.compactOnLaunch()`, which can be used to control if a Realm file should be compacted when opened.
* A better error message if a data class was used as model classes. (Issue [#684](https://github.com/realm/realm-kotlin/issues/684))
* A better error message if the Realm plugin was not applied to the module containing model classes. (Issue [#676](https://github.com/realm/realm-kotlin/issues/676))
* A better error message if a class is used that is not part of the schema. (Issue [#680](https://github.com/realm/realm-kotlin/issues/680))
* Add support for fine-grained notification on Realm instances. `Realm.asFlow()` yields `RealmChange` that represent the `InitialRealm` or `UpdatedRealm` states.
* Add support for fine-grained notification on Realm objects. `RealmObject.asFlow()` yields `ObjectChange` that represent the `InitialObject`, `UpdatedObject` or `DeletedObject` states.
* Add support for fine-grained notification on Realm lists. `RealmList.asFlow()` yields `ListChange` that represent the `InitialList`, `UpdatedList` or `DeletedList` states.
* Add support for fine-grained notifications on Realm query results. `RealmResults.asFlow()` yields `ResultsChange` that represent the `InitialResults` or `UpdatedResults` states.
* Add support for fine-grained notifications on `RealmSingleQuery`. `RealmSingleQuery.asFlow()` yields `SingleQueryChange` that represent the `PendingObject`, `InitialObject`, `UpdatedObject` or `DeletedObject` states.
* Add support for data migration as part of an automatic schema upgrade through `RealmConfiguration.Builder.migration(RealmMigration)` (Issue [#87](https://github.com/realm/realm-kotlin/issues/87))
* Added ability to delete objects specified by a `RealmQuery` or `RealmResults` through `MutableRealm.delete(...)` and `DynamicMutableRealm.delete(...).
* Add support for updating existing objects through `copyToRealm`. This requires them having a primary key. (Issue [#564](https://github.com/realm/realm-kotlin/issues/564))
* Added `Realm.deleteRealm(RealmConfiguration)` function that deletes the Realm files from the filesystem (Issue [#95](https://github.com/realm/realm-kotlin/issues/95)).


### Fixed
* Intermittent `ConcurrentModificationException` when running parallel builds. (Issue [#626](https://github.com/realm/realm-kotlin/issues/626))
* Refactor the compiler plugin to use API's compatible with Kotlin `1.6.20`. (Issue ([#619](https://github.com/realm/realm-kotlin/issues/619)).
* `RealmConfiguration.path` should report the full Realm path. (Issue ([#605](https://github.com/realm/realm-kotlin/issues/605)).
* Support multiple constructors in model definition (one zero arg constructor is required though). (Issue ([#184](https://github.com/realm/realm-kotlin/issues/184)).
* Boolean argument substitution in queries on iOS/macOS would crash the query. (Issue [#691](https://github.com/realm/realm-kotlin/issues/691))
* Support 32-bit Android (x86 and armeabi-v7a). (Issue ([#109](https://github.com/realm/realm-kotlin/issues/109)).
* Make updates of primary key properties throw IllegalStateException (Issue [#353](https://github.com/realm/realm-kotlin/issues/353))


### Compatibility
* This release is compatible with:
  * Kotlin 1.6.10.
  * Coroutines 1.6.0-native-mt. Also compatible with Coroutines 1.6.0 but requires enabling of the new memory model and disabling of freezing, see https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility for details on that.
  * AtomicFu 0.17.0.
* Minimum Gradle version: 6.1.1.  
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* Downgraded to Gradle 7.2 as a work-around for https://youtrack.jetbrains.com/issue/KT-51325.
* Updated to Realm Core 11.10.0, commit: ad2b6aeb1fd58135a2d9bf463011e26f934390ea.


## 0.9.0 (2022-01-28)

### Breaking Changes
* `RealmResults.observe()` and `RealmList.observe()` have been renamed to `asFlow()`.
* Querying via `Realm.objects(...)` is no longer supported. Use `Realm.query(...)` instead.

### Enhancements
* Added API for inspecting the schema of the realm with `BaseRealm.schema()` ([#238](https://github.com/realm/realm-kotlin/issues/238)).
* Added support for `RealmQuery` through `Realm.query(...)` ([#84](https://github.com/realm/realm-kotlin/issues/84)).
* Added source code link to model definition compiler errors. ([#173](https://github.com/realm/realm-kotlin/issues/173))
* Support Kotlin's new memory model. Enabled in consuming project with the following gradle properties `kotlin.native.binary.memoryModel=experimental`.
* Add support for JVM on M1 (in case we're running outside Rosetta compatibility mode, example when using Azul JVM which is compiled against `aarch64`) [#629](https://github.com/realm/realm-kotlin/issues/629).

### Fixed
* Sync on jvm targets on Windows/Linux crashes with unavailable scheduler ([#655](https://github.com/realm/realm-kotlin/issues/655)).

### Compatibility
* This release is compatible with:
  * Kotlin 1.6.10.
  * Coroutines 1.6.0-native-mt. Also compatible with Coroutines 1.6.0 but requires enabling of the new memory model and disabling of freezing, see https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility for details on that.
  * AtomicFu 0.17.0.
* Minimum Gradle version: 6.1.1.  
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* Updated to Gradle 7.3.3.
* Updated to Android Gradle Plugin 7.1.0.
* Updated to AndroidX JUnit 1.1.3.
* Updated to AndroidX Test 1.4.0.


## 0.8.2 (2022-01-20)

### Breaking Changes
* None.

### Enhancements
* None.

### Fixed
* The `library-base` module would try to initialize a number of `library-sync` classes for JNI lookups. These and `RealmObjectCompanion` were not being excluded from Proguard obfuscation causing release builds to crash when initializing JNI [#643](https://github.com/realm/realm-kotlin/issues/643).

### Compatibility
* This release is compatible with:
  * Kotlin 1.6.10.
  * Coroutines 1.5.2-native-mt.
  * AtomicFu 0.17.0.
* Minimum Gradle version: 6.1.1.
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* None.


## 0.8.1 (2022-01-18)

### Breaking Changes
* None.

### Enhancements
* None.

### Fixed
* Using a custom module name to fix [#621](https://github.com/realm/realm-kotlin/issues/621).
* Synchronously process project configurations to avoid exceptions when running parallel builds [#626](https://github.com/realm/realm-kotlin/issues/626).
* Update to Kotlin 1.6.10. The `Compatibility` entry for 0.8.0 stating that the project had been updated to Kotlin 1.6.10 was not correct [#640](https://github.com/realm/realm-kotlin/issues/640).

### Compatibility
* This release is compatible with:
  * Kotlin 1.6.10.
  * Coroutines 1.5.2-native-mt.
  * AtomicFu 0.17.0.
* Minimum Gradle version: 6.1.1.  
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* Updated to Kotlin 1.6.10.


## 0.8.0 (2021-12-17)

### Breaking Changes
* Reworked configuration hierarchy:
  * Separated common parts of `RealmConfiguraion` and `SyncConfiguration` into `io.realm.Configuration` to avoid polluting the base configuration with local-only options.
  * Changed `Realm.open(RealmConfiguration)` to accept new base configuration with `Realm.open(Configuration)`.
  * Removed option to build `SyncConfiguration`s with `deleteRealmIfMigrationNeeded` option.

### Enhancements
* [Sync] Added support for `User.logOut()` ([#245](https://github.com/realm/realm-kotlin/issues/245)).
* Added support for dates through a new property type: `RealmInstant`.
* Allow to pass schema as a variable containing the involved `KClass`es and build configurations non-fluently ([#389](https://github.com/realm/realm-kotlin/issues/389)).
* Added M1 support for `library-base` variant ([#483](https://github.com/realm/realm-kotlin/issues/483)).

### Fixed
* Gradle metadata for pure Android projects. Now using `io.realm.kotlin:library-base:<VERSION>` should work correctly.
* Compiler plugin symbol lookup happens only on Sourset using Realm ([#544](https://github.com/realm/realm-kotlin/issues/544)).
* Fixed migration exception when opening a synced realm that is already stored in the backend for the first time ([#601](https://github.com/realm/realm-kotlin/issues/604)).

### Compatibility
* This release is compatible with:
  * Kotlin 1.6.10.
  * Coroutines 1.5.2-native-mt.
  * AtomicFu 0.17.0.
* Minimum Gradle version: 6.1.1.  
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* Updated to Ktor 1.6.5.
* Updated to AndroidX Startup 1.1.0.
* Updated to Gradle 7.2.
* Updated to Android Gradle Plugin 7.1.0-beta05.
* Updated to NDK 23.1.7779620.
* Updated to Android targetSdk 31.
* Updated to Android compileSdk 31.
* Updated to Android Build Tools 31.0.0.
* Updated to Ktlint version 0.43.0.
* Updated to Ktlint Gradle Plugin 10.2.0.
* Updated to Kotlin Serialization 1.3.0.
* Updated to Detekt 1.19.0-RC1.
* Updated to Dokka 1.6.0.
* Updated to AtomicFu 0.17.0.
* Updated to Realm Core 11.7.0, commit: 5903577608d202ad88f375c1bb2ceedb831f6d7b.


## 0.7.0 (2021-10-31)

### Breaking Changes
* None.

### Enhancements
* Basic MongoDB Realm sync support:
  * Enabled by using library dependency `io.realm.kotlin:library-sync:<VERSION>`
  * Build `AppConfiguration`s through `AppConfiguration.Builder(appId).build()`
  * Linking your app with a MongoDB Realm App through `App.create(appConfiguration)`
  * Log in to a MongoDB Realm App through `App.login(credentials)`. Currently only supports `Credentials.anonymous()` and `Credentials.emailPassword(...)`
  * Create `SyncConfiguration`s through `SyncConfiguration.Builder(user, partitionValue, schema).build()`
  * Create synchronized realm by `Realm.open(syncConfiguration)`

### Fixed
* None.

### Compatibility
* This release is compatible with:
  * Kotlin 1.5.31
  * Coroutines 1.5.2-native-mt
  * AtomicFu 0.16.3

### Internal
* Updated to Realm Core commit: ecfc1bbb734a8520d08f04f12f083641309799b3
* Updated to Ktor 1.6.4.


## 0.6.0 (2021-10-15)

### Breaking Changes
* Rename library dependency from `io.realm.kotlin:library:<VERSION>` to `io.realm.kotlin:library-base:<VERSION>`
* Abstracted public API into interfaces. The interfaces have kept the name of the previous classes so only differences are:
  - Opening a realm: `Realm(configuration)` has changed to `Realm.open(configuration)`
  - Easy construction of simple configurations: `RealmConfiguration(schema = ...)` has changed to `RealmConfiguration.with(schema = ...)`
  - Instantiating a `RealmList` is now done through `realmListOf(...)` or by `Iterable<T>.toRealmList()`
* Make argument to `findLatest` non-nullable: `MutableRealm.findLatest(obj: T?): T?` has changed to `MutableRealm.findLatest(obj: T): T?`
* Allow query arguments to be `null`: `RealmResult.query(query: String = "TRUEPREDICATE", vararg args: Any): RealmResults<T>` has change to `RealmResult.query(query: String = "TRUEPREDICATE", vararg args: Any?): RealmResults<T>`
* Moved `objects(KClass<T>)` and `<reified T> objects()` methods from `BaseRealm` to `TypedRealm`
* Changed `RealmObject.version` into method `RealmObject.version()`.
* Replaced `RuntimeException`s by the explicit exceptions: `IllegalArgumentException`, `IllegalStateException` and `IndexOutOfBoundsException`.
* Throw `Error` an unrecoverable Realm problem happen in the underlying storage engine.
* Removed optional arguments to `RealmConfiguration.with(...)` and `RealmConfiguration.Builder(...)`. Name and path can now only be set through the builder methods.

### Enhancements
* Add support for [JVM target](https://github.com/realm/realm-kotlin/issues/62) supported platforms are: Linux (since Centos7 x86_64), Windows (since 8.1 x86_64) and Macos (x86_64).
* Added support for marking a field as indexed with `@Index`

### Fixed
* Fixed null pointer exceptions when returning an unmanaged object from `MutableRealm.write/writeBlocking`.
* Fixed premature closing of underlying realm of frozen objects returned from `MutableRealm.write/writeBlocking`. (Issue [#477](https://github.com/realm/realm-kotlin/issues/477))

### Compatibility
* This release is compatible with:
  * Kotlin 1.5.31
  * Coroutines 1.5.2-native-mt
  * AtomicFu 0.16.3

### Internal
* Updated to Realm Core commit: 028626880253a62d1c936eed4ef73af80b64b71
* Updated to Kotlin 1.5.31.


## 0.5.0 (2021-08-20)

### Breaking Changes
* Moved `@PrimaryKey` annotation from `io.realm.PrimaryKey` to `io.realm.annotations.PrimaryKey`.

### Enhancements
* Add support for excluding properties from the Realm schema. This is done by either using JVM `@Transient` or the newly added `@io.realm.kotlin.Ignore` annotation. (Issue [#278](https://github.com/realm/realm-kotlin/issues/278)).
* Add support for encrypted Realms. Encryption can be enabled by passing a 64-byte encryption key to the configuration builder. (Issue [#227](https://github.com/realm/realm-kotlin/issues/227))
* Add support for `RealmList` notifications using Kotlin `Flow`s. (Issue [#359](https://github.com/realm/realm-kotlin/issues/359))
* Unmanaged `RealmObject`s can now be added directly to `RealmList`s without having to copy them to Realm beforehand.

### Fixed
* Throw exception when violating primary key uniqueness constraint when importing objects with `copyToRealm`.
* Fix crash caused by premature release of frozen versions (`java.lang.RuntimeException: [18]: Access to invalidated Results objects`)
* Fix optimizations bypassing our custom getter and setter from within a class (Issue [#375](https://github.com/realm/realm-kotlin/issues/375)).

### Compatibility
* This release is compatible with Kotlin 1.5.21 and Coroutines 1.5.0.

### Internal
* Updated to Kotlin 1.5.21.
* Updated Gradle to 7.1.1.
* Updated Android Gradle Plugin to 4.1.0.
* Updated to Android Build Tools 30.0.2.
* Updated to targetSdk 30 for Android.
* Now uses Java 11 to build the project.


## 0.4.1 (2021-07-16)

### Breaking Changes
* None.

### Enhancements
* None.

### Fixed
* Throw exception when violating primary key uniqueness constraint when importing objects with `copyToRealm`.
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
