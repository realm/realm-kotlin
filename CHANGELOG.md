
## 3.0.0 (2024-10-03)

### Breaking Changes
* [Sync] Atlas Device Sync related functionality has been removed from the project.

### Enhancements
* None.

### Fixed
* None.

### Compatibility
* File format: Generates Realms with file format v24 (reads and upgrades file format v10 or later).
* Realm Studio 15.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 2.0.20 and above. Support for experimental K2-compilation with `kotlin.experimental.tryK2=true`.
  * Ktor 2.1.2 and above.
  * Coroutines 1.7.0 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Kbson 0.4.0.
* Minimum Gradle version: 7.2.
* Minimum Android Gradle Plugin version: 7.1.3.
* Minimum Android SDK: 16.
* Minimum R8: 8.0.34.

### Internal
* None.


## 2.3.0 (2024-09-16)

### Breaking Changes
* None.

### Enhancements
* None.

### Fixed
* Via https://github.com/realm/realm-kotlin/pull/1826. Fix compiler crash caused by a change in Kotlin 2.0.20. (Issue [#1825](https://github.com/realm/realm-kotlin/issues/1825)). Thanks @KitsuneAlex.

### Compatibility
* File format: Generates Realms with file format v24 (reads and upgrades file format v10 or later).
* Realm Studio 15.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 2.0.20 and above. Support for experimental K2-compilation with `kotlin.experimental.tryK2=true`.
  * Ktor 2.1.2 and above.
  * Coroutines 1.7.0 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Kbson 0.4.0.
* Minimum Gradle version: 7.2.
* Minimum Android Gradle Plugin version: 7.1.3.
* Minimum Android SDK: 16.
* Minimum R8: 8.0.34.

### Internal
* None.


## 2.2.0 (2024-09-13)

### Breaking Changes
* None.

### Enhancements
* Support Android 15 page size 16 KB. (Issue [#1787](https://github.com/realm/realm-kotlin/issues/1787) [JIRA](https://jira.mongodb.org/browse/RKOTLIN-1105)).
* Reduce the size of the local transaction log produced by creating objects, improving the performance of insertion-heavy transactions (Core issue [realm/realm-core#7734](https://github.com/realm/realm-core/pull/7734)).
* Performance has been improved for range queries on integers and timestamps. Requires that you use the "BETWEEN" operation in RQL or the Query::between() method when you build the query. (Core issue [realm/realm-core#7785](https://github.com/realm/realm-core/pull/7785))
* Updated bundled OpenSSL version to 3.3.1 (Core issue [realm/realm-core#7947](https://github.com/realm/realm-core/pull/7947)).
* [Sync] Add support for switching users with `App.switchUser(User)`. (Issue [#1813](https://github.com/realm/realm-kotlin/issues/1813)/[RKOTLIN-1115](https://jira.mongodb.org/browse/RKOTLIN-1115)).
* [Sync] Report the originating error that caused a client reset to occur. (Core issue [realm/realm-core#6154](https://github.com/realm/realm-core/issues/6154)).
* [Sync] It is no longer an error to set a base url for an App with a trailing slash - for example, `https://services.cloud.mongodb.com/` instead of `https://services.cloud.mongodb.com` - before this change that would result in a 404 error from the server (Core issue [realm/realm-core#7791](https://github.com/realm/realm-core/pull/7791)).
* [Sync] On Windows devices Device Sync will additionally look up SSL certificates in the Windows Trusted Root Certification Authorities certificate store when establishing a connection. (Core issue [realm/realm-core#7882](https://github.com/realm/realm-core/pull/7882))
* [Sync] Role and permissions changes no longer require a client reset to update the local realm. (Core issue [realm/realm-core#7440](https://github.com/realm/realm-core/pull/7440)).
* [Sync] Sync log statements now include the app services connection id in their prefix (e.g `Connection[1:<connection id>] Session[1]: log message`) to make correlating sync activity to server logs easier during troubleshooting (Core issue [realm/realm-core#7849)](https://github.com/realm/realm-core/pull/7849)).
* [Sync] Improve sync bootstrap performance by reducing the number of table selections in the replication logs for embedded objects (Core issue [realm/realm-core#7945](https://github.com/realm/realm-core/issues/7945)).
* [Sync] Released a read lock which was pinned for the duration of a mutable subscription even after commit. This frees resources earlier, and may improve performance of sync bootstraps where the starting state is large (Core issue [realm/realm-core#7946](https://github.com/realm/realm-core/issues/7946)).
* [Sync] Client reset cycle detection now checks if the previous recovery attempt was made by the same core version, and if not attempts recovery again (Core issue [realm/realm-core#7944](https://github.com/realm/realm-core/pull/7944)).

### Fixed
* Comparing a numeric property with an argument list containing a string would throw. (Core issue [realm/realm-core#7714](https://github.com/realm/realm-core/issues/7714), since v2.0.0).
* After compacting, a file upgrade would be triggered. This could cause loss of data if schema mode is SoftResetFile (Core issue [realm/realm-core#7747](https://github.com/realm/realm-core/issues/7747), since v1.15.0).
* Encrypted files on Windows had a maximum size of 2GB even on x64 due to internal usage of `off_t`, which is a 32-bit type on 64-bit Windows (Core issue [realm/realm-core#7698](https://github.com/realm/realm-core/pull/7698)).
* The encryption code no longer behaves differently depending on the system page size, which should entirely eliminate a recurring source of bugs related to copying encrypted Realm files between platforms with different page sizes. One known outstanding bug was ([RNET-1141](https://github.com/realm/realm-dotnet/issues/3592)), where opening files on a system with a larger page size than the writing system would attempt to read sections of the file which had never been written to (Core issue [realm/realm-core#7698](https://github.com/realm/realm-core/pull/7698)).
* There were several complicated scenarios which could result in stale reads from encrypted files in multiprocess scenarios. These were very difficult to hit and would typically lead to a crash, either due to an assertion failure or DecryptionFailure being thrown (Core issue [realm/realm-core#7698](https://github.com/realm/realm-core/pull/7698), since v1.8.0).
* Encrypted files have some benign data races where we can memcpy a block of memory while another thread is writing to a limited range of it. It is logically impossible to ever read from that range when this happens, but Thread Sanitizer quite reasonably complains about this. We now perform a slower operations when running with TSan which avoids this benign race (Core issue [realm/realm-core#7698](https://github.com/realm/realm-core/pull/7698)).
* Tokenizing strings for full-text search could pass values outside the range [-1, 255] to `isspace()`, which is undefined behavior (Core issue [realm/realm-core#7698](https://github.com/realm/realm-core/pull/7698), since the introduction of FTS).
* Clearing a List of RealmAnys in an upgraded file would lead to an assertion failing (Core issue [realm/realm-core#7771](https://github.com/realm/realm-core/issues/7771), since v1.15.0)
* You could get unexpected merge results when assigning to a nested collection (Core issue [realm/realm-core#7809](https://github.com/realm/realm-core/issues/7809), since v1.15.0)
* Fixed removing backlinks from the wrong objects if the link came from a nested list, nested dictionary, top-level dictionary, or list of mixed, and the source table had more than 256 objects. This could manifest as `array_backlink.cpp:112: Assertion failed: int64_t(value >> 1) == key.value` when removing an object. (Core issue [realm/realm-core#7594](https://github.com/realm/realm-core/issues/7594), since Core v11 for dictionaries)
* Fixed the collapse/rejoin of clusters which contained nested collections with links. This could manifest as `array.cpp:319: Array::move() Assertion failed: begin <= end [2, 1]` when removing an object. (Core issue [realm/realm-core#7839](https://github.com/realm/realm-core/issues/7839), since the introduction of nested collections in v1.15.0)
* Fixed an "invalid column key" exception when using a RQL "BETWEEN" query on an int or timestamp property across links (Core issue [realm/realm-core#7935](https://github.com/realm/realm-core/issues/7935), since v2.0.0)
* [Sync] Platform networking was not enabled even if setting `AppConfiguration.Builder.usePlatformNetworking`. (Issue [#1811](https://github.com/realm/realm-kotlin/issues/1811)/[RKOTLIN-1114](https://jira.mongodb.org/browse/RKOTLIN-1114)).
* [Sync] Fix some client resets (such as migrating to flexible sync) potentially failing with AutoClientResetFailed if a new client reset condition (such as rolling back a flexible sync migration) occurred before the first one completed. (Core issue [realm/realm-core#7542](https://github.com/realm/realm-core/pull/7542), since v1.9.0)
* [Sync] Fixed a change of mode from Strong to All when removing links from an embedded object that links to a tombstone. This affects sync apps that use embedded objects which have a `Lst<Mixed>` that contains a link to another top level object which has been deleted by another sync client (creating a tombstone locally). In this particular case, the switch would cause any remaining link removals to recursively delete the destination object if there were no other links to it. (Core issue [realm/realm-core#7828](https://github.com/realm/realm-core/issues/7828), since v1.15.0)
* [Sync] `SyncSession.uploadAllLocalChanges` was inconsistent in how it handled commits which did not produce any changesets to upload. Previously it would sometimes complete immediately if all commits waiting to be uploaded were empty, and at other times it would wait for a server roundtrip. It will now always complete immediately. (Core issue [realm/realm-core#7796](https://github.com/realm/realm-core/pull/7796)).
* [Sync] Sync client can crash if a session is resumed while the session is being suspended. (Core issue [realm/realm-core#7860](https://github.com/realm/realm-core/issues/7860), since v1.0.0)
* [Sync] When a sync session is interrupted by a disconnect or restart while downloading a bootstrap, stale data from the previous bootstrap may be included when the session reconnects and downloads the bootstrap. This can lead to objects stored in the database that do not match the actual state of the server and potentially leading to compensating writes. (Core issue [realm/realm-core#7827](https://github.com/realm/realm-core/issues/7827), since v1.0.0).
* [Sync] App subscription callback was getting fired before the user profile was retrieved on login, leading to an empty user profile when using the callback. (Core issue [realm/realm-core#7889](https://github.com/realm/realm-core/issues/7889), since v2.0.0).
* [Sync] Fixed conflict resolution bug related to ArrayErase and Clear instructions, which could sometimes cause an "Invalid prior_size" exception to prevent synchronization (Core issue [realm/realm-core#7893](https://github.com/realm/realm-core/issues/7893), since v2.0.0).
* [Sync] Fixed bug which would prevent eventual consistency during conflict resolution. Affected clients would experience data divergence and potentially consistency errors as a result (Core issue [realm/realm-core#7955](https://github.com/realm/realm-core/pull/7955), since v2.0.0).
* [Sync] Fixed issues loading the native Realm libraries on Linux ARMv7 systems when they linked against our bundled OpenSSL resulting in errors like `unexpected reloc type 0x03` (Core issue [realm/realm-core#7947](https://github.com/realm/realm-core/issues/7947), since Realm Core v14.1.0).

### Compatibility
* File format: Generates Realms with file format v24 (reads and upgrades file format v10 or later).
* Realm Studio 15.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 2.0.20 and above. Support for experimental K2-compilation with `kotlin.experimental.tryK2=true`.
  * Ktor 2.1.2 and above.
  * Coroutines 1.7.0 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Kbson 0.4.0.
* Minimum Gradle version: 7.2.
* Minimum Android Gradle Plugin version: 7.1.3.
* Minimum Android SDK: 16.
* Minimum R8: 8.0.34.

### Internal
* Updated to Realm Core 14.12.0 commit c2552e1d36867cb42b28130e894a81fc17081062.
* Updated to Sync protocol version 14 to support server intiated bootstraps and role change updates without a client reset. (Core issue [realm/realm-core#7440](https://github.com/realm/realm-core/pull/7440)).


## 2.1.0 (2024-07-12)

### Breaking Changes
- None.

### Enhancements
* Avoid exporting Core's symbols so we can statically build the Kotlin SDK with other SDKs like Swift in the same project. (Issue [JIRA](https://jira.mongodb.org/browse/RKOTLIN-877)).
* Improved mechanism for unpacking of JVM native libs suitable for local development. (Issue [#1715](https://github.com/realm/realm-kotlin/issues/1715) [JIRA](https://jira.mongodb.org/browse/RKOTLIN-1065)).
* [Sync] Add `SyncException.isFatal` to signal fatal unrecoverable exceptions. (Issue [#1767](https://github.com/realm/realm-kotlin/issues/1767) [RKOTLIN-1096](https://jira.mongodb.org/browse/RKOTLIN-1096)).

### Fixed
* Fix crashes when core tries to log invalid utf-8 messages. (Issue [#1760](https://github.com/realm/realm-kotlin/issues/1760) [RKOTLIN-1089](https://jira.mongodb.org/browse/RKOTLIN-1089)).
* [Sync] Fix `NullPointerException` in `SubscriptionSet.waitForSynchronization`. (Issue [#1777](https://github.com/realm/realm-kotlin/issues/1777) [RKOTLIN-1102](https://jira.mongodb.org/browse/RKOTLIN-1102)).

### Compatibility
* File format: Generates Realms with file format v24 (reads and upgrades file format v10 or later).
* Realm Studio 15.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 2.0.0 and above. Support for experimental K2-compilation with `kotlin.experimental.tryK2=true`.
  * Ktor 2.1.2 and above.
  * Coroutines 1.7.0 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Kbson 0.4.0.
* Minimum Gradle version: 7.2.
* Minimum Android Gradle Plugin version: 7.1.3.
* Minimum Android SDK: 16.
* Minimum R8: 8.0.34.

### Internal
* Reworked test app initializer framework.


## 2.0.0 (2024-06-03)

> [!NOTE]
> This release will bump the Realm file format 24. Opening a file with an older format will automatically upgrade it from file format v10. If you want to upgrade from an earlier file format version you will have to use Realm Kotlin v1.13.1 or earlier. Downgrading to a previous file format is not possible.

### Breaking changes
* Removed property `RealmLog.level`. Log levels can be set with `RealmLog.setLevel`. (Issue [#1691](https://github.com/realm/realm-kotlin/issues/1691) [JIRA](https://jira.mongodb.org/browse/RKOTLIN-1038))
* Removed `LogConfiguration`. Log levels and custom loggers can be set with `RealmLog`. (Issue [#1691](https://github.com/realm/realm-kotlin/issues/1691) [JIRA](https://jira.mongodb.org/browse/RKOTLIN-1038))
* Removed deprecated `io.realm.kotlin.types.ObjectId`. Use `org.mongodb.kbson.BsonObjectId` or its type alias `org.mongodb.kbson.ObjectId` instead. (Issue [#1749](https://github.com/realm/realm-kotlin/issues/1749) [JIRA](https://jira.mongodb.org/browse/RKOTLIN-1082))
* Removed deprecated `RealmClass.isEmbedded`. Class embeddeness can be check with `RealmClassKind.EMBEDDED`. (Issue [#1753](https://github.com/realm/realm-kotlin/issues/1753) [JIRA](https://jira.mongodb.org/browse/RKOTLIN-1080))
* Some authentication related operations will no longer throw specialized `InvalidCredentialsException` and `CredentialsCannotBeLinkedException` but the more general `AuthException` and `ServiceException`. (Issue [#1763](https://github.com/realm/realm-kotlin/issues/1763)/[RKOTLIN-1091](https://jira.mongodb.org/browse/RKOTLIN-1091))
* [Sync] Removed deprecated methods `User.identity` and `User.provider`, user identities can be accessed with the already existing `User.identities`. (Issue [#1751](https://github.com/realm/realm-kotlin/issues/1751) [JIRA](https://jira.mongodb.org/browse/RKOTLIN-1083))
* [Sync] `App.allUsers` does no longer return a map, but only a list of users known locally. (Issue [#1751](https://github.com/realm/realm-kotlin/issues/1751) [JIRA](https://jira.mongodb.org/browse/RKOTLIN-1083))
* [Sync] Removed deprecated `DiscardUnsyncedChangesStrategy.onError`. (Issue [#1755](https://github.com/realm/realm-kotlin/issues/1755) [JIRA](https://jira.mongodb.org/browse/RKOTLIN-1085))
* [Sync] Sync progress notifications now reports an estimate ranged from `0.0` to `1.0` with `Progress.estimate` instead of `transferredBytes` and `totalBytes`. (Issue [#1744](https://github.com/realm/realm-kotlin/issues/1744)/[RKOTLIN-1079](https://jira.mongodb.org/browse/RKOTLIN-1079)).

### Enhancements
* Support for RealmLists and RealmDictionaries in `RealmAny`. (Issue [#1434](https://github.com/realm/realm-kotlin/issues/1434))
* Optimized `RealmList.indexOf()` and `RealmList.contains()` using Core implementation of operations instead of iterating elements and comparing them in Kotlin. (Issue [#1625](https://github.com/realm/realm-kotlin/pull/1666) [RKOTLIN-995](https://jira.mongodb.org/browse/RKOTLIN-995)).
* Add support for filtering logs by category. (Issue [#1691](https://github.com/realm/realm-kotlin/issues/1691) [JIRA](https://jira.mongodb.org/browse/RKOTLIN-1038))
* [Sync] Add Mongo Client API to access Atlas App Service collections. It can be accessed through `User.mongoClient`. (Issue [#972](https://github.com/realm/realm-kotlin/issues/972)/[RKOTLIN-612](https://jira.mongodb.org/browse/RKOTLIN-612))
* [Sync] Sync progress notifications is now also supported for flexible sync configurations. (Issue [#1744](https://github.com/realm/realm-kotlin/issues/1744) [RKOTLIN-1079](https://jira.mongodb.org/browse/RKOTLIN-1079)).

### Fixed
* Inserting the same typed link to the same key in a dictionary more than once would incorrectly create multiple backlinks to the object. This did not appear to cause any crashes later, but would have affecting explicit backlink count queries (eg: `...@links.@count`) and possibly notifications (Core Issue [realm/realm-core#7676](https://github.com/realm/realm-core/issues/7676) since v1.16.0).
* [Sync] Automatic client reset recovery would crash when recovering AddInteger instructions on a Mixed property if its type was changed to non-integer (Core issue [realm/realm-core#7683](https://github.com/realm/realm-core/pull/7683), since v0.11.0).
* [Sync] Typos [SubscriptionSetState.SUPERSEDED], [SyncTimeoutOptions.pingKeepalivePeriod] and [SyncTimeoutOptions.pongKeepalivePeriod]. (Issue [#1754](https://github.com/realm/realm-kotlin/pull/1754)

### Compatibility
* File format: Generates Realms with file format v24 (reads and upgrades file format v10 or later).
* Realm Studio 15.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 2.0.0 and above. Support for experimental K2-compilation with `kotlin.experimental.tryK2=true`.
  * Ktor 2.1.2 and above.
  * Coroutines 1.7.0 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Kbson 0.4.0.
* Minimum Gradle version: 7.2.
* Minimum Android Gradle Plugin version: 7.1.3.
* Minimum Android SDK: 16.
* Minimum R8: 8.3.37.

### Internal
* Updated to Realm Core 14.7.0 commit c280bdb17522323d5c30dc32a2b9efc9dc80ca3b.
* Changed Kotlin compiler testing framework to https://github.com/zacsweers/kotlin-compile-testing
* Updated to Detekt 1.23.6.


## 1.16.0 (2024-05-01)

> [!NOTE]
> This release will bump the Realm file format from version 23 to 24. Opening a file with an older format will automatically upgrade it from file format v10. If you want to upgrade from an earlier file format version you will have to use Realm Kotlin v1.13.1 or earlier. Downgrading to a previous file format is not possible.

### Breaking changes
* None.

### Enhancements
* Add support for changing the App Services base URL. It allows to roam between Atlas and Edge Server. Changing the url would trigger a client reset. (Issue [#1659](https://github.com/realm/realm-kotlin/issues/1659)/[RKOTLIN-1013](https://jira.mongodb.org/browse/RKOTLIN-1023))

### Fixed
* Fixed a bug when running a IN query (or a query of the pattern `x == 1 OR x == 2 OR x == 3`) when evaluating on a string property with an empty string in the search condition. Matches with an empty string would have been evaluated as if searching for a null string instead. (Core issue [realm/realm-core#7628](https://github.com/realm/realm-core/pull/7628) since Core v10.0.0-beta.9)
* Fixed several issues around encrypted file portability (copying a "bundled" encrypted Realm from one device to another). (Core issues [realm/realm-core#7322](https://github.com/realm/realm-core/issues/7322) and [realm/realm-core#7319](https://github.com/realm/realm-core/issues/7319))
* Queries using query paths on Mixed values returns inconsistent results (Core issue [realm/realm-core#7587](https://github.com/realm/realm-core/issues/7587), since Core v14.0.0)
* [Sync] `App.allUsers()` included logged out users only if they were logged out while the App instance existed. It now always includes all logged out users. (Core issue [realm/realm-core#7300](https://github.com/realm/realm-core/pull/7300))
* [Sync] Deleting the active user left the active user unset rather than selecting another logged-in user as the active user like logging out and removing users did. (Core issue [realm/realm-core#7300](https://github.com/realm/realm-core/pull/7300))
* [Sync] Schema initialization could hit an assertion failure if the sync client applied a downloaded changeset while the Realm file was in the process of being opened (Core issue [realm/realm-core#7041](https://github.com/realm/realm-core/issues/7041), since Core v11.4.0).

### Known issues
* Missing initial download progress notification when there is no active downloads. (Issue [realm/realm-core#7627](https://github.com/realm/realm-core/issues/7627), since 1.15.1)

### Compatibility
* File format: Generates Realms with file format v24 (reads and upgrades file format v10 or later).
* Realm Studio 15.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.9.0 and above. Support for experimental K2-compilation with `kotlin.experimental.tryK2=true`.
  * Ktor 2.1.2 and above.
  * Coroutines 1.7.0 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Kbson 0.3.0.
* Minimum Gradle version: 6.8.3.
* Minimum Android Gradle Plugin version: 4.1.3.
* Minimum Android SDK: 16.
* Minimum R8: 8.0.34.

### Internal
* Updated to Realm Core 14.6.1 commit cde3adb7649d3361806dbbae0cf353b8fdc4d54e.


## 1.15.0 (2024-04-17)

> [!NOTE]
> This release will bump the Realm file format from version 23 to 24. Opening a file with an older format will automatically upgrade it from file format v10. If you want to upgrade from an earlier file format version you will have to use Realm Kotlin v1.13.1 or earlier. Downgrading to a previous file format is not possible.

### Breaking changes
* If you want to query using `@type` operation, you must use 'objectlink' to match links to objects. 'object' is reserved for dictionary types.
* Binary data and String data are now strongly typed for comparisons and queries. This change is especially relevant when querying for a string constant on a RealmAny property, as now only strings will be returned. If searching for Binary data is desired, then that type must be specified by the constant. In RQL the new way to specify a binary constant is to use `mixed = bin('xyz')` or `mixed = binary('xyz')`. (Core issue [realm/realm-core#6407](https://github.com/realm/realm-core/issues/6407)).

### Enhancements
* Add support for using aggregate operations on RealmAny properties in queries  (Core issue [realm/realm-core#7398](https://github.com/realm/realm-core/pull/7398))
* Property keypath in RQL can be substituted with value given as argument. Use `$P<i>` in query string. (Core issue [realm/realm-core#7033](https://github.com/realm/realm-core/issues/7033))
* You can now use query substitution for the @type argument (Core issue [realm/realm-core#7289](https://github.com/realm/realm-core/issues/7289))
* Storage of Decimal128 properties has been optimised so that the individual values will take up 0 bits (if all nulls), 32 bits, 64 bits or 128 bits depending on what is needed. (Core issue [realm/realm-core#6111](https://github.com/realm/realm-core/pull/6111))
* Querying a specific entry in a collection (in particular 'first and 'last') is supported. (Core issue [realm/realm-core#4269](https://github.com/realm/realm-core/issues/4269))
* Index on list of strings property now supported (Core issue [realm/realm-core#7142](https://github.com/realm/realm-core/pull/7142))
* Improved performance of RQL (parsed) queries on a non-linked string property using: >, >=, <, <=, operators and fixed behaviour that a null string should be evaulated as less than everything, previously nulls were not matched. (Core issue [realm/realm-core#3939](https://github.com/realm/realm-core/issues/3939).
* Updated bundled OpenSSL version to 3.2.0 (Core issue [realm/realm-core#7303](https://github.com/realm/realm-core/pull/7303))
* [Sync] The default base url in `AppConfiguration` has been updated to point to `services.cloud.mongodb.com`. See https://www.mongodb.com/docs/atlas/app-services/domain-migration/ for more information. (Issue [#1685](https://github.com/realm/realm-kotlin/issues/1685))

### Fixed
* Sorting order of strings has changed to use standard unicode codepoint order instead of grouping similar english letters together. A noticeable change will be from "aAbBzZ" to "ABZabz". (Core issue [realm/realm-core#2573](https://github.com/realm/realm-core/issues/2573))
* `@count`/`@size` is now supported for `RealmAny` properties (Core issue [realm/realm-core#7280](https://github.com/realm/realm-core/issues/7280), since v10.0.0)
* Fixed equality queries on a `RealmAny` property with an index possibly returning the wrong result if values of different types happened to have the same StringIndex hash. (Core issue [realm/realm-core6407](https://github.com/realm/realm-core/issues/6407), since v11.0.0-beta.5).
* If you have more than 8388606 links pointing to one specific object, the program will crash. (Core issue [realm/realm-core#6577](https://github.com/realm/realm-core/issues/6577), since v6.0.0)
* Query for NULL value in `RealmAny<RealmAny>` would give wrong results (Core issue [realm/realm-core6748])(https://github.com/realm/realm-core/issues/6748), since v10.0.0)
* Fixed queries like `indexed_property == NONE {x}` which mistakenly matched on only x instead of not x. This only applies when an indexed property with equality (==, or IN) matches with `NONE` on a list of one item. If the constant list contained more than one value then it was working correctly. (Core issue [realm/realm-core#7777](https://github.com/realm/realm-java/issues/7862), since v12.5.0)
* Uploading the changesets recovered during an automatic client reset recovery may lead to 'Bad server version' errors and a new client reset. (Core issue [realm/realm-core7279](https://github.com/realm/realm-core/issues/7279), since v13.24.1)
* Fixed crash in fulltext index using prefix search with no matches (Core issue [realm/realm-core#7309](https://github.com/realm/realm-core/issues/7309), since v13.18.0)
* Fix a minor race condition when backing up Realm files before a client reset which could have lead to overwriting an existing file. (Core issue [realm/realm-core#7341](https://github.com/realm/realm-core/pull/7341)).
* Fix opening realm with cached user while offline results in fatal error and session does not retry connection. (Core issue [realm/realm-core#7349](https://github.com/realm/realm-core/issues/7349), since v13.26.0)
* Fixed conflict resolution bug which may result in an crash when the AddInteger instruction on Mixed properties is merged against updates to a non-integer type (Core issue [realm/realm-code#7353](https://github.com/realm/realm-core/pull/7353))
* Fix a spurious crash related to opening a Realm on background thread while the process was in the middle of exiting (Core issue [realm/realm-core#7420](https://github.com/realm/realm-core/issues/7420))


### Compatibility
* File format: Generates Realms with file format v24 (reads and upgrades file format v10 or later).
* Realm Studio 15.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.9.0 and above. Support for experimental K2-compilation with `kotlin.experimental.tryK2=true`.
  * Ktor 2.1.2 and above.
  * Coroutines 1.7.0 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Kbson 0.3.0.
* Minimum Gradle version: 6.8.3.
* Minimum Android Gradle Plugin version: 4.1.3.
* Minimum Android SDK: 16.
* Minimum R8: 8.0.34.

### Internal
* Updated to Realm Core 14.5.1 commit 316889b967f845fbc10b4422f96c7eadd47136f2.
* Deprecated Jenkins and switching to Github Action ([JIRA]https://jira.mongodb.org/browse/RKOTLIN-825).
- Remove CMake required version.
* Updated URL to documentation.
* Refactored to allow compilation with Kotlin 2.0



## 1.14.1 (2024-03-19)

### Breaking Changes
- None.

### Enhancements
- Fixes missing binaries files for Windows and Linux platforms when releasing. (Issue [#1671](https://github.com/realm/realm-kotlin/issues/1690) [JIRA](https://jira.mongodb.org/browse/RKOTLIN-1037))

### Fixed
- None.

### Compatibility
- File format: Generates Realms with file format v23.
- Realm Studio 13.0.0 or above is required to open Realms created by this version.
- This release is compatible with the following Kotlin releases:
  - Kotlin 1.9.0 and above. Support for experimental K2-compilation with `kotlin.experimental.tryK2=true`.
  - Ktor 2.1.2 and above.
  - Coroutines 1.7.0 and above.
  - AtomicFu 0.18.3 and above.
  - The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
- Minimum Kbson 0.3.0.
- Minimum Gradle version: 6.8.3.
- Minimum Android Gradle Plugin version: 4.1.3.
- Minimum Android SDK: 16.
- Minimum R8: 8.0.34.

### Internal
- Deprecated Jenkins and switching to Github Action ([JIRA]https://jira.mongodb.org/browse/RKOTLIN-825).


## 1.14.0 (2024-03-08)

### Breaking Changes
* None.

### Enhancements
* The Unpacking of JVM native library will use the current library version instead of a calculated hash for the path. (Issue [#1617](https://github.com/realm/realm-kotlin/issues/1617)).
* [Sync] Added option to use managed WebSockets via OkHttp instead of Realm's built-in WebSocket client for Sync traffic (Only Android and JVM targets for now). Managed WebSockets offer improved support for proxies and firewalls that require authentication. This feature is currently opt-in and can be enabled by using `AppConfiguration.usePlatformNetworking()`. Managed WebSockets will become the default in a future version. (PR [#1528](https://github.com/realm/realm-kotlin/pull/1528)).
* [Sync] `AutoClientResetFailed` exception now reports as the throwable cause any user exceptions that might occur during a client reset. (Issue [#1580](https://github.com/realm/realm-kotlin/issues/1580))

### Fixed
* Cache notification callback JNI references at startup to ensure that symbols can be resolved in core callbacks. (Issue [#1577](https://github.com/realm/realm-kotlin/issues/1577))
* Using `Realm.asFlow()` could miss an update if a write was started right after opening the Realm. (Issue [#1582](https://github.com/realm/realm-kotlin/issues/1582))
* Guarded analytic errors so that they do not fail user builds.
* Using keypaths in Flows could sometimes throw `java.lang.IllegalStateException: [RLM_ERR_WRONG_THREAD]: Realm accessed from incorrect thread.`. (Issue [#1594](https://github.com/realm/realm-kotlin/pull/1594, since 1.13.0)
* Non-`IllegalStateExceptions` in a `write`-block would not cancel transactions, but leave it open. (Issue [#1615](https://github.com/realm/realm-kotlin/issues/1615)).
* [Sync] `NullPointerException` while waiting for the synchronization of a subscription set if the client was set in `AwaitingMark` state. (Issue [#1671](https://github.com/realm/realm-kotlin/issues/1671) [JIRA](https://jira.mongodb.org/browse/RKOTLIN-1027))
* Github Action: Snapshot publishing with Github Action. (Issue [#1654](https://github.com/realm/realm-kotlin/issues/1654) [JIRA](https://jira.mongodb.org/browse/RKOTLIN-1018))
* Github Action: automate release process to Maven Central. (Issue [JIRA](https://jira.mongodb.org/browse/RKOTLIN-709))

### Compatibility
* File format: Generates Realms with file format v23.
* Realm Studio 13.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.9.0 and above. Support for experimental K2-compilation with `kotlin.experimental.tryK2=true`.
  * Ktor 2.1.2 and above.
  * Coroutines 1.7.0 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Kbson 0.3.0.
* Minimum Gradle version: 6.8.3.
* Minimum Android Gradle Plugin version: 4.1.3.
* Minimum Android SDK: 16.
* Minimum R8: 8.0.34.

### Internal
* Update to Ktor 2.3.4.
* Updated to CMake 3.27.7
* Updated to Realm Core 13.26.0, commit 5533505d18fda93a7a971d58a191db5005583c92.
* Adding Sync tests via Github Action.
* Updated to Swig 4.2.0. (Issue [GitHub #1632](https://github.com/realm/realm-kotlin/issues/1632) [JIRA RKOTLIN-1001](https://jira.mongodb.org/browse/RKOTLIN-1001))


## 1.13.0 (2023-12-01)

### Breaking Changes
* None.

### Enhancements
* Support for experimental K2-compilation with `kotlin.experimental.tryK2=true`. (Issue [#1483](https://github.com/realm/realm-kotlin/issues/1483))
* Added support for keypaths in `asFlow()` methods on objects and queries. This makes it possible to control which properties will trigger change events, including properties on objects below the default nested limit of 4. (Issue [#661](https://github.com/realm/realm-kotlin/issues/661))
* [Sync] Added support for multiplexing sync connections. When enabled, a single
  connection is used per sync user rather than one per synchronized Realm. This
  reduces resource consumption when multiple Realms are opened and will
  typically improve performance. The behavior can be controlled through [AppConfiguration.Builder.enableSessionMultiplexing]. It will be made the default
  in a future release. (Issue [#1578](https://github.com/realm/realm-kotlin/pull/1578))
* [Sync] Various sync timeout options can now be configured through `AppConfiguration.Builder.syncTimeouts()`. (Issue [#971](https://github.com/realm/realm-kotlin/issues/971)).

### Fixed
* `RealmInstant.now` used an API (`java.time.Clock.systemUTC().instant()`) introduced in API 26, current minSDK is 16. (Issue [#1564](https://github.com/realm/realm-kotlin/issues/1564))
* Fix compiler crash caused by a change in Kotlin 1.9.20 ((toIrConst moved under common IrUtils)[https://github.com/JetBrains/kotlin/commit/ca8db7d0b83f6dfd6afcea7a5fe7556d38f325d8]). (Issue [#1566](https://github.com/realm/realm-kotlin/issues/1566))
* Fix craches caused by posting to a released scheduler. (Issue [#1543](https://github.com/realm/realm-kotlin/issues/1543))
* Fix NPE when applying query aggregators on classes annotated with `@PersistedName`. (Issue [1569](https://github.com/realm/realm-kotlin/pull/1569))
* [Sync] Fix crash when syncing data if the log level was set to `LogLevel.TRACE` or `LogLevel.ALL`. (Issue [#1560](https://github.com/realm/realm-kotlin/pull/1560))

### Compatibility
* File format: Generates Realms with file format v23.
* Realm Studio 13.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.9.0 and above. Support for experimental K2-compilation with `kotlin.experimental.tryK2=true`.
  * Ktor 2.1.2 and above.
  * Coroutines 1.7.0 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Kbson 0.3.0.
* Minimum Gradle version: 6.8.3.
* Minimum Android Gradle Plugin version: 4.1.3.
* Minimum Android SDK: 16.
* Minimum R8: 8.0.34.

### Internal
* Updated to Realm Core 13.24.0, commit e593a5f19d0dc205db931ec5618a8c10c95cac90.


## 1.12.0 (2023-11-02)

This release upgrades the Sync metadata in a way that is not compatible with older versions. To downgrade a Sync app from this version, you'll need to manually delete the metadata folder located at `$[SYNC-ROOT-DIRECTORY]/mongodb-realm/[APP-ID]/server-utility/metadata/`. This will log out all users.

### Breaking Changes
* None.

### Enhancements
* Realm will no longer set the JVM bytecode to 1.8 when applying the Realm plugin. (Issue [#1513](https://github.com/realm/realm-kotlin/issues/1513))
* The Realm Gradle Plugin no longer has a dependency on KAPT. (Issue [#1513](https://github.com/realm/realm-kotlin/issues/1513))

### Fixed
* `Realm.getNumberOfActiveVersions` now returns the actual number of active versions. (Core issue [#6960](https://github.com/realm/realm-core/pull/6960))
* Fixed memory leak on Darwin caused by a reference cycle between resources and the GC cleaner. (Issue [#1530](https://github.com/realm/realm-kotlin/pull/1530))
* Fixed memory leaks on the JVM platform, see PR for more information. (Issue [#1526](https://github.com/realm/realm-kotlin/pull/1526))
* Removed pin on the initial realm version after opening a Realm. (Issue [#1519](https://github.com/realm/realm-kotlin/pull/1519))
* `Realm.close()` is now idempotent.
* Fix error in `RealmAny.equals` that would sometimes return `true` when comparing RealmAnys wrapping same type but different values. (Issue [#1523](https://github.com/realm/realm-kotlin/pull/1523))
* [Sync] If calling a function on App Services that resulted in a redirect, it would only redirect for GET requests. (Issue [#1517](https://github.com/realm/realm-kotlin/pull/1517))
* [Sync] Manual client reset on Windows would not trigger correctly when run inside `onManualResetFallback`. (Issue [#1515](https://github.com/realm/realm-kotlin/pull/1515))
* [Sync] `ClientResetRequiredException.executeClientReset()` now returns a boolean indicating if the manual reset fully succeeded or not. (Issue [#1515](https://github.com/realm/realm-kotlin/pull/1515))
* [Sync] If calling a function on App Services that resulted in a redirect, it would only redirect for
GET requests. (Issue [#1517](https://github.com/realm/realm-kotlin/pull/1517))
* [Sync] If calling a function on App Services that resulted in a redirect, it would only redirect for GET requests. (Issue [#1517](https://github.com/realm/realm-kotlin/pull/1517))

### Compatibility
* File format: Generates Realms with file format v23.
* Realm Studio 13.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.8.20 and above. The K2 compiler is not supported yet.
  * Ktor 2.1.2 and above.
  * Coroutines 1.7.0 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Kbson 0.3.0.
* Minimum Gradle version: 6.8.3.
* Minimum Android Gradle Plugin version: 4.1.3.
* Minimum Android SDK: 16.

### Internal
* Updated to Realm Core 13.23.2, commit e6271d72308b40399890060f58a88cf568c2ee22.


## 1.11.1 (2023-09-07)

### Enhancements
* None.

### Fixed
* Opening a Realm would crash with `No built-in scheduler implementation for this platform` on Linux (JVM) and Windows. (Issue [#1502](https://github.com/realm/realm-kotlin/issues/1502), since 1.11.0)

### Compatibility
* File format: Generates Realms with file format v23.
* Realm Studio 13.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.8.0 and above. The K2 compiler is not supported yet.
  * Ktor 2.1.2 and above.
  * Coroutines 1.7.0 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Kbson 0.3.0.
* Minimum Gradle version: 6.8.3.
* Minimum Android Gradle Plugin version: 4.1.3.
* Minimum Android SDK: 16.

### Internal
* None.


## 1.11.0 (2023-09-01)

### Breaking Changes
* `BaseRealmObject.equals()` has changed from being identity-based only (===) to instead return `true` if two objects come from the same Realm version. This e.g means that reading the same object property twice will now be identical. Note, two Realm objects, even with identical values will not be considered equal if they belong to different versions.

```
val childA: Child = realm.query<Child>().first().find()!!
val childB: Child = realm.query<Child>().first().find()!!

// This behavior is the same both before 1.11.0 and before
childA === childB // false

// This will return true in 1.11.0 and onwards. Before it will return false
childA == childB

realm.writeBlocking { /* Do a write */ }
val childC = realm.query<Child>().first().find()!!

// This will return false because childA belong to version 1, while childC belong to version 2.
// Override equals/hashCode if value semantics are wanted.
childA == childC
```

### Enhancements
* Fulltext queries now support prefix search by using the * operator, like `description TEXT 'alex*'`. (Core issue [#6860](https://github.com/realm/realm-core/issues/6860))
* Realm model classes now generate custom `toString`, `equals` and `hashCode` implementations. This makes it possible to compare by object reference across multiple collections. Note that two objects at different versions will not be considered equal, even
if the content is the same. Custom implementations of these methods will be respected if they are present. (Issue [#1097](https://github.com/realm/realm-kotlin/issues/1097))
* Support for performing geospatial queries using the new classes: `GeoPoint`, `GeoCircle`, `GeoBox`, and `GeoPolygon`. See `GeoPoint` documentation on how to persist locations. (Issue [#1403](https://github.com/realm/realm-kotlin/pull/1403))
* Support for automatic resolution of embedded object constraints during migration through `RealmConfiguration.Builder.migration(migration: AutomaticSchemaMigration, resolveEmbeddedObjectConstraints: Boolean)`. (Issue [#1464](https://github.com/realm/realm-kotlin/issues/1464)
* [Sync] Add support for customizing authorization headers and adding additional custom headers to all Atlas App service requests with `AppConfiguration.Builder.authorizationHeaderName()` and `AppConfiguration.Builder.addCustomRequestHeader(...)`. (Issue [#1453](https://github.com/realm/realm-kotlin/pull/1453))
* [Sync] Added support for manually triggering a reconnect attempt for Device Sync. This is done through a new `App.Sync.reconnect()` method. This method is also now called automatically when a mobile device toggles off airplane mode. (Issue [#1479](https://github.com/realm/realm-kotlin/issues/1479))

### Fixed
* Rare corruption causing 'Invalid streaming format cookie'-exception. Typically following compact, convert or copying to a new file. (Issue [#1440](https://github.com/realm/realm-kotlin/issues/1440))
* Compiler error when using Kotlin 1.9.0 and backlinks. (Issue [#1469](https://github.com/realm/realm-kotlin/issues/1469))
* Leaking `JVMScheduler` instances. In certain circumstances, it could lead to a JNI crash. (Issue [#1463](https://github.com/realm/realm-kotlin/pull/1463))
* [Sync] Changing a subscriptions query type or query itself will now trigger the `WaitForSync.FIRST_TIME` behaviour, rather than only checking changes to the name. (Issues [#1466](https://github.com/realm/realm-kotlin/issues/1466))

### Compatibility
* File format: Generates Realms with file format v23.
* Realm Studio 13.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.8.0 and above. The K2 compiler is not supported yet.
  * Ktor 2.1.2 and above.
  * Coroutines 1.7.0 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Kbson 0.3.0.
* Minimum Gradle version: 6.8.3.
* Minimum Android Gradle Plugin version: 4.1.3.
* Minimum Android SDK: 16.

### Internal
* Updated to Realm Core 13.20.0, commit c258e2681bca5fb33bbd23c112493817b43bfa86.


## 1.10.2 (2023-07-21)

### Breaking Changes
* None.

### Enhancements
* None.

### Fixed
* `RealmInstant` could be instantiated with invalid arguments. (Issue [#1443](https://github.com/realm/realm-kotlin/issues/1443))
* `equals` and `hashCode` on unmanaged `RealmList` and `RealmSet` resulted in incorrect values. (Issue [#1454](https://github.com/realm/realm-kotlin/pull/1454))
* [Sync] HTTP requests were not logged when the log level was set in `RealmLog.level`. (Issue [#1456](https://github.com/realm/realm-kotlin/pull/1456))
* [Sync] `RealmLog.level` is set to `WARN` after creating an `App` or `Realm` configuration. (Issue [#1456](https://github.com/realm/realm-kotlin/pull/1459))

### Compatibility
* File format: Generates Realms with file format v23.
* Realm Studio 13.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.8.0 and above. The K2 compiler is not supported yet.
  * Ktor 2.1.2 and above.
  * Coroutines 1.7.0 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Kbson 0.3.0.
* Minimum Gradle version: 6.8.3.
* Minimum Android Gradle Plugin version: 4.1.3.
* Minimum Android SDK: 16.

### Internal
* Updated to Realm Core 13.17.0, commit f1e962cd447f8b69f8f7cf46a188b1c6246923c5.


## 1.10.1 (2023-06-30)

### Breaking Changes
* None.

### Enhancements
* [Sync] Optimized the opening of Flexible Sync Realms when `waitForInitialRemoteData` is used. (Issue [#1438](https://github.com/realm/realm-kotlin/issues/1438))

### Fixed
* [Sync] Using `SyncConfiguration.waitForInitialRemoteData()` would require a network connection, even after opening the realm file for the first time. (Issue [#1439](https://github.com/realm/realm-kotlin/pull/1439))

### Compatibility
* File format: Generates Realms with file format v23.
* Realm Studio 13.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.8.0 and above. The K2 compiler is not supported yet.
  * Ktor 2.1.2 and above.
  * Coroutines 1.7.0 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Kbson 0.3.0.
* Minimum Gradle version: 6.8.3.
* Minimum Android Gradle Plugin version: 4.1.3.
* Minimum Android SDK: 16.

### Internal
* None.


## 1.10.0 (2023-06-28)

### Breaking Changes
* Generic arguments have been cleaned up. In a lot of places, `BaseRealmObject` was accepted as input. This was too broad and could result in runtime exceptions. In those places the argument has been restricted to the correct `TypedRealmObject`.

### Enhancements
* Loading the native library on Android above API 22 is no longer using Relinker, but now uses the normal `System.loadLibrary()`.
* Running Android Unit tests on the JVM is now supported instead of throwing `java.lang.NullPointerException`. This includes both pure Android projects (in the `/test` directory) and common tests in Multiplatform projects.
* Support for passing list, sets or iterable arguments to queries with `IN`-operators, e.g. `query<TYPE>("<field> IN $0", listOf(1,2,3))`. (Issue [#929](https://github.com/realm/realm-kotlin/issues/929))
* [Sync] Support for `RealmQuery.subscribe()` and `RealmResults.subscribe()` as an easy way to create subscriptions in the background while continuing to use the query result. This API is experimental. (Issue [#1363](https://github.com/realm/realm-kotlin/issues/1363))
* [Sync] Support for "write-only" objects which can be written to MongoDB time-series collections. This can be useful for e.g. telemetry data. Use this by creating a model classes that inherit from the new `AsymmetricRealmObject` base class. See this class for more information. (Issue [#1420](https://github.com/realm/realm-kotlin/pull/1420))

### Fixed
* None

### Compatibility
* File format: Generates Realms with file format v23.
* Realm Studio 13.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.8.0 and above. The K2 compiler is not supported yet.
  * Ktor 2.1.2 and above.
  * Coroutines 1.7.0 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Kbson 0.3.0.
* Minimum Gradle version: 6.8.3.
* Minimum Android Gradle Plugin version: 4.1.3.
* Minimum Android SDK: 16.

### Internal
* Updated to Realm Core 13.15.2, commit b8f3244a316f512ad48c761e11e4a135f729ad23.
* Bumped Android Gradle Version to 7.3.1.
* Add bundle ID sync connection parameter.
* Enabled profiling for unit test modules.


## 1.9.1 (2023-06-08)

### Breaking Changes
* None.

### Enhancements
* None.

### Fixed
* Deleting `RealmResults` created by `by backlinks()` would crash with `Cannot delete custom Deleteable objects: ObjectBoundRealmResults`. (Issue [#1413](https://github.com/realm/realm-kotlin/issues/1413))
* Incremental compilation in combination with `@PersistedName` on model class names could result in schema errors when opening the Realm (Issue [#1401](https://github.com/realm/realm-kotlin/issues/1401)).
* [Sync] Native crash if a server error was reported while using `SyncConfiguration.waitForInitialRemoteData()`. (Issue [#1401](https://github.com/realm/realm-kotlin/issues/1401))

### Compatibility
* File format: Generates Realms with file format v23.
* Realm Studio 13.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.8.0 and above. The K2 compiler is not supported yet.
  * Ktor 2.1.2 and above.
  * Coroutines 1.6.4 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Kbson 0.3.0.
* Minimum Gradle version: 6.8.3.
* Minimum Android Gradle Plugin version: 4.1.3.
* Minimum Android SDK: 16.

### Internal
* None.


## 1.9.0 (2023-05-23)

This release bumps the minimum supported version of Kotlin from 1.7.20 to 1.8.0. This also impact the minimum supported version of the Android Gradle Plugin and Gradle. See the Compatibility seection for more information.

### Breaking Changes
* None.

### Enhancements
* Realm objects now support ignoring delegated properties. (Issue [#1377](https://github.com/realm/realm-kotlin/pull/1386))
* Support for simple token full-text search using `@FullText` on `String` properties. Read the documentation on `@FullText` for more info. (Issue [#1368](https://github.com/realm/realm-kotlin/pull/1368))
* Support for initialization of a realm file with a bundled realm through `RealmConfiguration.Builder(...).initialRealmFile(...)` and `SyncConfiguration.Builder(...).initialRealmFile(...)`. (Issue [#577](https://github.com/realm/realm-kotlin/issues/577))
* [Sync] The new sync exception `CompensatingWriteException` will be thrown in the `SyncSession.ErrorHandler` when the server undoes one or more client writes. (Issue [#1372](https://github.com/realm/realm-kotlin/issues/1372))
* [Sync] Added experimental full document serialization support on Credentials with a Custom Function, App Services Function calls, user profile, and custom data. (Issue [#1355](https://github.com/realm/realm-kotlin/pull/1355))

### Fixed
* User exceptions now propagate correctly out from `RealmMigration` and `CompactOnLaunchCallback` instead of just resulting in a generic *User-provided callback failed* `RuntimeException`. (Issue [#1228](https://github.com/realm/realm-kotlin/issues/1228))
* The default compact-on-launch callback trigger 50% or more of the space could be reclaimed was reversed. (Issue [#1380](https://github.com/realm/realm-kotlin/issues/1380))
* Objects that were renamed using `@PersistedName` couldn't be referenced as a direct link in a model class. (Issue [#1377](https://github.com/realm/realm-kotlin/issues/1377))
* [Sync] `BsonEncoder` now allows converting numerical values with precision loss.

### Compatibility
* File format: Generates Realms with file format v23.
* Realm Studio 13.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.8.0 and above. The K2 compiler is not supported yet.
  * Ktor 2.1.2 and above.
  * Coroutines 1.6.4 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Kbson 0.3.0.
* Minimum Gradle version: 6.8.3.
* Minimum Android Gradle Plugin version: 4.1.3.
* Minimum Android SDK: 16.

### Internal
* Updated to Realm Core 13.11.0, commit d8721d7baec39571e7e5373c3f407a50d144307e.
* Updated to Sync Protocol version 9.
* Updated BAAS test server to v2023-05-15.
* Updated R8 used by tests to 4.0.48.

### Contributors
*  [Tim Klingeleers](https://github.com/Mardaneus86) for fixing the default `compactOnLaunch` logic.


## 1.8.0 (2023-05-01)

### Breaking Changes
* `RealmLog` is now a global singleton shared between all Realm API's. Previously log configuration happened using the `log` builder method on `AppConfiguration`, `SyncConfiguration` or `RealmConfiguration`. These API's are still present and for apps only using a single Atlas App ID, the behaviour is the same. For apps that have configured multiple Atlas App ID's, it will no longer be possible to configure different log levels and loggers for each app. Instead, the last `AppConfiguration` created will override the logger configuration from other `AppConfiguration`s.

### Enhancements
* Multiple processes can now access the same encrypted Realm instead of throwing `Encrypted interprocess sharing is currently unsupported`. (Core Issue [#1845](https://github.com/realm/realm-core/issues/1845))
* Added a public `RealmLog` class that replaces `AppConfiguration.Builder.log()`. (Issue [#1347](https://github.com/realm/realm-kotlin/pull/1347))
* Realm logs will now contain more debug information from the underlying database when `LogLevel.DEBUG` or below is enabled.
* Avoid tracking unreferenced realm versions through the garbage collector. (Issue [#1234](https://github.com/realm/realm-kotlin/issues/1234))
* `Realm.compactRealm(configuration)` has been added as way to compact a Realm file without having to open it. (Issue [#571](https://github.com/realm/realm-kotlin/issues/571))
* `@PersistedName` is now also supported on model classes. (Issue [#1138](https://github.com/realm/realm-kotlin/issues/1138))
* [Sync] All tokens, passwords and custom function arguments are now obfuscated by default, even if `LogLevel` is set to DEBUG, TRACE or ALL. (Issue [#410](https://github.com/realm/realm-kotlin/issues/410))
* [Sync] Add support for `App.authenticationChangeAsFlow()` which make it possible to listen to authentication changes like "LoggedIn", "LoggedOut" and "Removed" across all users of the app. (Issue [#749](https://github.com/realm/realm-kotlin/issues/749)).
* [Sync] Support for migrating from Partition-based to Flexible Sync automatically on the device if the server has migrated to Flexible Sync. ([Core Issue #6554](https://github.com/realm/realm-core/issues/6554))

### Fixed
* Querying a `RealmList` or `RealmSet` with more than eight entries with a list of values could result in a SIGABRT. (Issue [#1183](https://github.com/realm/realm-kotlin/issues/1183))

### Compatibility
* File format: Generates Realms with file format v23.
* Realm Studio 13.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.7.20 and above.
  * Ktor 2.1.2 and above.
  * Coroutines 1.6.4 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Gradle version: 6.7.1.
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* Updated to Realm Core 13.10.0, commit 7b9ab24d631437364dbe955ac3ea1f550b26cf10.


## 1.7.1 (2023-04-19)

### Breaking Changes
* None.

### Enhancements
* None.

### Fixed
* Fix compilation issue with Kotlin 1.8.20. (Issue [1346](https://github.com/realm/realm-kotlin/issues/1346))
* [Sync] Client Reset on JVM on Linux would crash with `No built-in scheduler implementation for this platform. Register your own with Scheduler::set_default_factory()`
* [Sync] Return correct provider for JWT-authenticated users. (Issue [#1350](https://github.com/realm/realm-kotlin/issues/1350))

### Compatibility
* File format: Generates Realms with file format v23.
* Realm Studio 13.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.7.20 and above.
  * Ktor 2.1.2 and above.
  * Coroutines 1.6.4 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Gradle version: 6.7.1.
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* None.


## 1.7.0 (2023-03-15)

### Breaking Changes
* None.

### Enhancements
* Upgrade OpenSSL from 3.0.7 to 3.0.8.
* Model classes with types not supported by Realm will now fail at compile time instead of logging a debug message. This error can be suppressed by using the `@Ignore` annotation. (Issue [#1226](https://github.com/realm/realm-kotlin/issues/1226))
* Wrong use of `val` for persisted properties will now throw a compiler time error, instead of crashing at runtime. (Issue [#1306](https://github.com/realm/realm-kotlin/issues/1306))
* Add support for querying on RealmSets containing objects with `RealmSet.query(...)`.  (Issue [#1037](https://github.com/realm/realm-kotlin/issues/1258))
* Added support for `RealmDictionary` in model classes. `RealmDictionary` is a `Map` of strings to values. Contrary to `RealmSet` and `RealmList` it is possible to store nullable objects/embedded objects in this data structure. See the class documentation for more details. (Issue [#537](https://github.com/realm/realm-kotlin/issues/537))
* Add Realm datatypes serialization support with `Kserializer`. Serializers can be found in `io.realm.kotlin.serializers`. (Issue [#1283](https://github.com/realm/realm-kotlin/pull/1283))
* [Sync] Add support for setting App Services connection identifiers through `AppConfiguration.appName` and `AppConfiguration.appVersion`, making it easier to identify connections in the server logs. (Issue (#407)[https://github.com/realm/realm-kotlin/issues/407])
* [Sync] Added `RecoverUnsyncedChangesStrategy`, an alternative automatic client reset strategy that tries to automatically recover any unsynced data from the client.
* [Sync] Added `RecoverOrDiscardUnsyncedChangesStrategy`, an alternative automatic client reset strategy that tries to automatically recover any unsynced data from the client, and discards any unsynced data if recovery is not possible. This is now the default policy.

### Fixed
* Fixed implementation of `RealmSet.iterator()` to throw `ConcurrentModificationException`s when the underlying set has been modified while iterating over it. (Issue [#1220](https://github.com/realm/realm-kotlin/issues/1220))
* Accessing an invalidated `RealmResults` now throws an `IllegalStateException` instead of a `RealmException`. (Issue [#1188](https://github.com/realm/realm-kotlin/pull/1188))
* Opening a Realm with a wrong encryption key or corrupted now throws an `IllegalStateException` instead of a `IllegalArgumentException`. (Issue [#1188](https://github.com/realm/realm-kotlin/pull/1188))
* Trying to convert to a Flexible Sync Realm with Flexible Sync disabled throws a `IllegalStateException` instead of a `IllegalArgumentException`. (Issue [#1188](https://github.com/realm/realm-kotlin/pull/1188))
* Fix missing initial flow events when registering for events while updating the realm. (Issue [#1151](https://github.com/realm/realm-kotlin/issues/1151))
* Emit deletion events and terminate flow when registering for notifications on outdated entities instead of throwing. (Issue [#1233](https://github.com/realm/realm-kotlin/issues/1233))
* [Sync] Close the thread associated with the Device Sync connection when closing the Realm. (Issue (https://github.com/realm/realm-kotlin/issues/1290)[#1290])

### Compatibility
* File format: Generates Realms with file format v23.
* Realm Studio 13.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.7.20 and above.
  * Ktor 2.1.2 and above.
  * Coroutines 1.6.4 and above.
  * AtomicFu 0.18.3 and above.
  * Kotlin Serialization 1.4.0 and above
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Gradle version: 6.7.1.
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* Updated to Realm Core 13.5.0, commit 37cc58865648f343f7d6e538d45980e7f2351211.


## 1.6.2 (2023-03-14)

### Breaking Changes
* None.

### Enhancements
* None.

### Fixed
* Returning invalid objects from `Realm.write` would throw an `IllegalStateException`. (Issue [#1300](https://github.com/realm/realm-kotlin/issues/1300))
* Compatibility with Realm Java when using the `io.realm.RealmObject` abstract class. (Issue [#1278](https://github.com/realm/realm-kotlin/issues/1278))
* Compiler error when multiple fields have `@PersistedName`-annotations that match they Kotlin name. (Issue [#1240](https://github.com/realm/realm-kotlin/issues/1240))
* RealmUUID would throw an `ClassCastException` when comparing with an object instance of a different type. (Issue [#1288](https://github.com/realm/realm-kotlin/issues/1288))
* Compiler error when using Kotlin 1.8.0 and Compose for desktop 1.3.0. (Issue [#1296](https://github.com/realm/realm-kotlin/issues/1296))
* [Sync] `SyncSession.downloadAllServerChange()` and `SyncSession.uploadAllLocalChanges()` was reversed.

### Compatibility
* File format: Generates Realms with file format v23.
* Realm Studio 13.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.7.20 and above.
  * Ktor 2.1.2 and above.
  * Coroutines 1.6.4 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Gradle version: 6.7.1.
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* None.


## 1.6.1 (2023-02-02)

### Breaking Changes
* None.

### Enhancements
* None.

### Fixed
* Allow defining properties with the field name as the persisted name. ([#1240](https://github.com/realm/realm-kotlin/issues/1240))
* Fix compilation error when accessing Realm Kotlin model classes from Java code. ([#1256](https://github.com/realm/realm-kotlin/issues/1256))

### Compatibility
* File format: Generates Realms with file format v23.
* Realm Studio 13.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.7.20 and above.
  * Ktor 2.1.2 and above.
  * Coroutines 1.6.4 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Gradle version: 6.7.1.
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* None.


## 1.6.0 (2023-01-25)

This release will bump the Realm file format from version 22 to 23. Opening a file with an older format will automatically upgrade it. Downgrading to a previous file format is not possible.

### Breaking Changes
* None.

### Enhancements
* OpenSSL has been upgraded from from 1.1.1n to 3.0.7.
* Added support for `RealmAny` as supported field in model classes. A `RealmAny` is used to represent a polymorphic Realm value or Realm Object, is indexable but cannot be used as a primary key.
* Add support for `Decimal128` as supported field in model classes. (Issue [#653](https://github.com/realm/realm-kotlin/issues/653))
* Realm will now use a lot less memory and disk space when different versions of realm objects are used. ([Core Issue #5440](https://github.com/realm/realm-core/pull/5440))
* Realm will now continuously track and reduce the size of the Realm file when it is in use rather that only when opening the file with `Configuration.compactOnLaunch` enabled. ([Core Issue #5754](https://github.com/realm/realm-core/issues/5754))
* Add support for `Realm.copyFromRealm()`. All RealmObjects, RealmResults, RealmList and RealmSets now also have a `copyFromRealm()` extension method.
* Add support for querying on RealmLists containing objects with `RealmList.query(...)`.  (Issue [#1037](https://github.com/realm/realm-kotlin/issues/1037))
* Add better error messages when inheriting `RealmObject` with unsupported class types. (Issue [#1086](https://github.com/realm/realm-kotlin/issues/1086))
* Added support for reverse relationships on Embedded objects through the `EmbeddedRealmObject.parent()` extension function. (Issue [#1141](https://github.com/realm/realm-kotlin/pull/1141))
* Added support for reverse relationships through the `backlinks` delegate on `EmbeddedObjects`. See the function documentation for more details. (Issue [#1134](https://github.com/realm/realm-kotlin/issues/1134))
* Added support for `@PersistedName` annotations for mapping a Kotlin field name to the underlying field name persisted in the Realm. (Issue [#590](https://github.com/realm/realm-kotlin/issues/590))
* [Sync] `App.close()` have been added so it is possible to close underlying ressources used by the app instance.
* [Sync] Add support for progress listeners with `SyncSession.progressAsFlow(...)`. (Issue [#428](https://github.com/realm/realm-kotlin/issues/428))lin/issues/1086))
* [Sync] `Realm.writeCopyTo(syncConfig)` now support copying a Flexible Sync Realm to another Flexible Sync Realm.
* [Sync] Added support for App functions, see documentation for more details. (Issue [#1110](https://github.com/realm/realm-kotlin/pull/1110))
* [Sync] Added support for custom App Services Function authentication. (Issue [#741](https://github.com/realm/realm-kotlin/issues/741))
* [Sync] Add support for accessing user auth profile metadata and custom data through the extension functions 'User.profileAsBsonDocument()' and 'User.customDataAsBsonDocument()'. (Issue [#750](https://github.com/realm/realm-kotlin/pull/750))
* [Sync] Add support for `App.callResetPasswordFunction` (Issue [#744](https://github.com/realm/realm-kotlin/issues/744))
* [Sync] Add support for connection state and connection state change listerners with `SyncSession.connectionState` and `SyncSession.connectionStateAsFlow(). (Issue [#429](https://github.com/realm/realm-kotlin/issues/429))

### Fixed
* Fix missing `Realm.asFlow()`-events from remote updates on synced realms. (Issue [#1070](https://github.com/realm/realm-kotlin/issues/1070))
* Windows binaries for JVM did not statically link the C++ runtime, which could lead to crashes if it wasn't preinstalled. (Issue [#1211](https://github.com/realm/realm-kotlin/pull/1211))
* Internal dispatcher threads would leak when closing Realms. (Issue [#818](https://github.com/realm/realm-kotlin/issues/818))
* Realm finalizer thread would prevent JVM main thread from exiting. (Issue [#818](https://github.com/realm/realm-kotlin/issues/818))
* `RealmUUID` did not calculate the correct `hashCode`, so putting it in a `HashSet` resulted in duplicates.
* JVM apps on Mac and Linux would use a native file built in debug mode, making it slower than needed. The correct native binary built in release mode is now used. Windows was not affected. (Issue [#1124](https://github.com/realm/realm-kotlin/pull/1124))
* `RealmUUID.random()` would generate the same values when an app was re-launched from Android Studio during development. (Issue [#1123](https://github.com/realm/realm-kotlin/pull/1123))
* Complete flows with an IllegalStateException instead of crashing when notifications cannot be delivered due to insufficient channel capacity (Issue [#1147](https://github.com/realm/realm-kotlin/issues/1147))
* Prevent "Cannot listen for changes on a deleted Realm reference"-exceptions when notifier is not up-to-date with newest updates from write transaction.
* [Sync] Custom loggers now correctly see both normal and sync events. Before, sync events were just logged directly to LogCat/StdOut.
* [Sync] When a `SyncSession` was paused using `SyncSession.pause()`, it would sometimes automatically resume the session. `SyncSession.State.PAUSED` has been added, making it explicit when a session is paused. (Core Issue [#6085](https://github.com/realm/realm-core/issues/6085))

### Compatibility
* File format: Generates Realms with file format v23.
* Realm Studio 13.0.0 or above is required to open Realms created by this version.
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.7.20 and above.
  * Ktor 2.1.2 and above.
  * Coroutines 1.6.4 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Gradle version: 6.7.1.
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* Updated to Realm Core 13.2.0, commit 5a119d8cb2eaac60c298532af2c9ae789af0c9e6.
* Updated to require Swig 4.1.0.
* Updated AndroidxStartup to 1.1.1.
* Updated to Kbson 0.2.0.
* `io.realm.kotlin.types.ObjectId` now delegates all responsibility to `org.mongodb.kbson.ObjectId` while maintaining the interface.
* Added JVM test wrapper as a workaround for https://youtrack.jetbrains.com/issue/KT-54634
* Use Relinker when loading native libs on Android.


## 1.5.2 (2023-01-10)

### Breaking Changes
* None.

### Enhancements
* None.

### Fixed
* Fixed various proguard issues. (Issue [#1150](https://github.com/realm/realm-kotlin/issues/1150))
* Fixed bug when creating `RealmInstant` instaces with `RealmInstant.now()` in Kotlin Native. (Issue [#1182](https://github.com/realm/realm-kotlin/issues/1182))
* Allow `@Index` on `Boolean` fields. (Issue [#1193](https://github.com/realm/realm-kotlin/issues/1193))
* Fixed issue with spaces in realm file path on iOS (Issue [#1194](https://github.com/realm/realm-kotlin/issues/1194))

### Compatibility
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.7.20 and above.
  * Ktor 2.1.2 and above.
  * Coroutines 1.6.4 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Gradle version: 6.7.1.
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* Updated to Gradle 7.6.


## 1.5.1 (2022-12-12)

### Breaking Changes
* None.

### Enhancements
* None.

### Fixed
* Fixed problem with KBSON using reservered keywords in Swift. (Issue [#1153](https://github.com/realm/realm-kotlin/issues/))
* Fixed database corruption and encryption issues on apple platforms. (Issue [#5076](https://github.com/realm/realm-js/issues/5076))
* Fixed 1.8.0-Beta/RC compatibility. (Issue [#1159](https://github.com/realm/realm-kotlin/issues/1159)
* [Sync] Bootstraps will not be applied in a single write transaction - they will be applied 1MB of changesets at a time. (Issue [#5999](https://github.com/realm/realm-core/pull/5999)).
* [Sync] Fixed a race condition which could result in operation cancelled errors being delivered to `Realm.open` rather than the actual sync error which caused things to fail. (Issue [#5968](https://github.com/realm/realm-core/pull/5968)).

### Compatibility
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.7.20 and above.
  * Ktor 2.1.2 and above.
  * Coroutines 1.6.4 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Gradle version: 6.7.1.
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* Updated to Realm Core 12.12.0, commit 292f534a8ae687a86d799b14e06a94985e49c3c6.
* Updated to KBSON 0.2.0
* Updated to require Swig 4.1.0.


## 1.5.0 (2022-11-11)

### Breaking Changes
* None.

### Enhancements
* Fixed error when using Realm object as query argument. Issue[#1098](https://github.com/realm/realm-kotlin/issues/1098)
* Realm will now use `System.loadLibrary()` first when loading native code on JVM, adding support for 3rd party JVM installers. If this fails, it will fallback to the current method of extracting and loading the native library from the JAR file. (Issue [#1105](https://github.com/realm/realm-kotlin/issues/1105)).
* Added support for in-memory Realms.
* Added support for reverse relationships through the `backlinks` delegate. See the function documentation for more details. (Issue [#1021](https://github.com/realm/realm-kotlin/pull/1021))
* Added support for `BsonObjectId` and its typealias `org.mongodb.kbson.ObjectId` as a replacement for `ObjectId`. `io.realm.kotlin.types.ObjectId` is still functional but has been marked as deprecated.
* [Sync] Added support for `BsonObjectId` as partition value.
* [Sync] Exposed `configuration` and `user` on `SyncSession`. (Issue [#431](https://github.com/realm/realm-kotlin/issues/431))
* [Sync] Added support for encrypting the user metadata used by Sync. (Issue [#413](https://github.com/realm/realm-kotlin/issues/413))
* [Sync] Added support for API key authentication. (Issue [#432](https://github.com/realm/realm-kotlin/issues/432))

### Fixed
* Close underlying realm if it is no longer referenced by any Kotlin object. (Issue [#671](https://github.com/realm/realm-kotlin/issues/671))
* Fixes crash during migration if Proguard was enabled. (Issue [#1106](https://github.com/realm/realm-kotlin/issues/1106))
* Adds missing Proguard rules for Embedded objects. (Issue [#1106](https://github.com/realm/realm-kotlin/issues/1107))

### Compatibility
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.7.20 and above.
  * Ktor 2.1.2 and above.
  * Coroutines 1.6.4 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Gradle version: 6.7.1.
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* Added dependency Kbson 0.1.0.
* Updated to use hierarchical multi platform project structure.
* Updated to Realm Core 12.11.0, commit 3d5ff9b5e47c5664c4c5611cdfd22fd15e451b55.
* Updated to Detekt 1.22.0-RC2.


## 1.4.0 (2022-10-17)

### Breaking Changes
* Minimum Kotlin version has been raised from 1.6.10 to 1.7.20.
* Support for the original (old) memory model on Kotlin Native has been dropped. Only the new Kotlin Native memory model is supported.
* Minimum Gradle version has been raised from 6.1.1 to 6.7.1.
* Minimum Ktor version has been raised from 1.6.8 to 2.1.2.

### Enhancements
* [Sync] The sync variant `io.realm.kotlin:library-sync:1.4.0`, now support Apple Silicon targets, ie. `macosArm64()`, `iosArm64()` and `iosSimulatorArm64`.

### Fixed
* [Sync] Using the SyncSession after receiving changes from the server would sometimes crash. Issue [#1068](https://github.com/realm/realm-kotlin/issues/1068)

### Compatibility
* This release is compatible with the following Kotlin releases:
  * Kotlin 1.7.20 and above.
  * Ktor 2.1.2 and above.
  * Coroutines 1.6.4 and above.
  * AtomicFu 0.18.3 and above.
  * The new memory model only. See https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility
* Minimum Gradle version: 6.7.1.
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* Updated to Kotlin 1.7.20.
* Updated to Coroutines 1.6.4.
* Updated to AtomicFu 0.18.3.
* Updated to Kotlin Serialization 1.4.0.
* Updated to KotlinX DateTime 0.4.0.
* Updated to okio 3.2.0.
* Ktor now uses the OkHttp engine on Android/JVM.
* Ktor now uses the Darwin engine on Native.


## 1.3.0 (2022-10-10)

### Breaking Changes
* None.

### Enhancements
* Support for `MutableRealm.deleteAll()`.
* Support for `MutableRealm.delete(KClass)`.
* Support for `DynamicMutableRealm.deleteAll()`.
* Support for `DynamicMutableRealm.delete(className)`.
* Support for `RealmInstant.now()`
* [Sync] Support for `User.getProviderType()`.
* [Sync] Support for `User.getAccessToken()`.
* [Sync] Support for `User.getRefreshToken()`.
* [Sync] Support for `User.getDeviceId()`.

### Fixed
* [Sync] Using `SyncConfiguration.Builder.waitForInitialRemoteDataOpen()` is now much faster if the server realm contains a lot of data. Issue [])_

### Compatibility
* This release is compatible with:
  * Kotlin 1.6.10 - 1.7.10. 1.7.20 support is tracked here: https://github.com/realm/realm-kotlin/issues/1024
  * Ktor 1.6.8. Ktor 2 support is tracked here: https://github.com/realm/realm-kotlin/issues/788
  * Coroutines 1.6.0-native-mt. Also compatible with Coroutines 1.6.0 but requires enabling of the new memory model and disabling of freezing, see https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility for details on that.
  * AtomicFu 0.17.0 and above.
* Minimum Gradle version: 6.1.1.
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* None.


## 1.2.0 (2022-09-30)

### Breaking Changes
* `RealmResults.query()` now returns a `RealmQuery` instead of a `RealmResults`.

### Enhancements
* Added support for `MutableRealmInt` in model classes. The new type behaves like a reference to a `Long`, but also supports `increment` and `decrement` methods. These methods implement a conflict-free replicated data type, whose value will converge even when changed across distributed devices with poor connections.
* [Sync] Support for `User.linkCredentials()`.
* [Sync] Support for `User.identities`, which will return all login types available to the user.
* [Sync] `User.id` as a replacement for `User.identity`. `User.identity` has been marked as deprecated.

### Fixed
* Classes using `RealmObject` or `EmbeddedRealmObject` as a generics type would be modified by the compiler plugin causing compilation errors. (Issue [981] (https://github.com/realm/realm-kotlin/issues/981))
* Ordering not respected for `RealmQuery.first()`. (Issue [#953](https://github.com/realm/realm-kotlin/issues/953))
* Sub-querying on a RealmResults ignored the original filter. (Issue [#998](https://github.com/realm/realm-kotlin/pull/998))
* `RealmResults.query()` semantic returning `RealmResults` was wrong, the return type should be a `RealmQuery`. (Issue [#1013](https://github.com/realm/realm-kotlin/pull/1013))
* Crash when logging messages with formatting specifiers. (Issue [#1034](https://github.com/realm/realm-kotlin/issues/1034))

### Compatibility
* This release is compatible with:
  * Kotlin 1.6.10 - 1.7.10. 1.7.20 support is tracked here: https://github.com/realm/realm-kotlin/issues/1024
  * Ktor 1.6.8. Ktor 2 support is tracked here: https://github.com/realm/realm-kotlin/issues/788
  * Coroutines 1.6.0-native-mt. Also compatible with Coroutines 1.6.0 but requires enabling of the new memory model and disabling of freezing, see https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility for details on that.
  * AtomicFu 0.17.0 and above.
* Minimum Gradle version: 6.1.1.
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* Updated to Realm Core 12.7.0, commit 18abbb4e9dc268620fa499923a92921bf26db8c6.
* Updated to Kotlin Compile Testing 1.4.9.


## 1.1.0 (2022-08-23)

### Breaking Changes
* None.

### Enhancements
* Added support for `RealmSet` in model classes. `RealmSet` is a collection of unique elements. See the class documentation for more details.
* Added support for `UUID` through a new property type: `RealmUUID`.
* Support for `Realm.writeCopyTo(configuration)`.
* [Sync] Add support for `User.delete()`, making it possible to delete user data on the server side (Issue [#491](https://github.com/realm/realm-kotlin/issues/491)).
* [Sync] It is now possible to create multiple anonymous users by specifying `Credentials.anonymous(reuseExisting = false)` when logging in to an App.

### Fixed
* `Realm.deleteRealm(config)` would throw an exception if the file didn't exist.
* Returning deleted objects from `Realm.write` and `Realm.writeBlocking` threw a non-sensical `NullPointerException`. Returning such a value is not allowed and now throws an `IllegalStateException`. (Issue [#965](https://github.com/realm/realm-kotlin/issues/965))
* [Sync] AppErrors and SyncErrors with unmapped category or error codes caused a crash. (Issue [951] (https://github.com/realm/realm-kotlin/pull/951))

### Compatibility
* This release is compatible with:
  * Kotlin 1.6.10 and above.
  * Coroutines 1.6.0-native-mt. Also compatible with Coroutines 1.6.0 but requires enabling of the new memory model and disabling of freezing, see https://github.com/realm/realm-kotlin#kotlin-memory-model-and-coroutine-compatibility for details on that.
  * AtomicFu 0.17.0.
* Minimum Gradle version: 6.1.1.
* Minimum Android Gradle Plugin version: 4.0.0.
* Minimum Android SDK: 16.

### Internal
* Updated to Realm Core 12.5.1, commit 6f6a0f415bd33cf2ced4467e36a47f7c84f0a1d7.
* Updated to Gradle 7.5.1.
* Updated to Android Gradle Plugin 7.2.2.
* Updated to CMake 3.22.1
* Updated to Android targetSdk 33.
* Updated to Android compileSdkVersion 33.
* Updated to Android Build Tools 33.0.0.
* Updated to Android NDK 23.2.8568313.


## 1.0.2 (2022-08-05)

### Breaking Changes
* None.

### Enhancements
* None.

### Fixed
* Missing proguard configuration for `CoreErrorUtils`. (Issue [#942](https://github.com/realm/realm-kotlin/issues/942))
* [Sync] Embedded Objects could not be added to the schema for `SyncConfiguration`s. (Issue [#945](https://github.com/realm/realm-kotlin/issues/945)).

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
