## Changes from Realm Java

    * API is now entirely frozen except for inside write transactions.
    * It is only required to open/close a Realm once across all threads.
    * Objects are only live when fetched from inside a write transaction. Will be frozen,
      when the transaction completes. Properties and links are still lazily accessed.
    * Frozen objects can now be observered for changes, i.e. you still get a new copy when the
      underlying data is update.
    * Automatic migration support but API is open for later support for manual migrations.
    * All async functions has been converted to coroutines/Flows. Blocking behavior can be
      accomplished by using `runBlocking { }`.
    * More heavily use of packages due to Kotlin module scope.
    * DynamicRealm now gets its own class hierarchy.
    * RealmResults can now be created as unmanaged, helping with field initializing for e.g. @LinkingObjects
    * With observable queries. `isLoaded` as a state is gone.
    * In general the class hierarchy has been split in Mutable<X> vs. not. This is inline with Kotlin
      collection API's. Impacted classes are Realm/MutableRealm, RealmCollection/MutableRealmCollection,
      RealmSchema/MutableRealmSchema.

## Design guidelines

 1. Have an API that is seen as Kotlin-first instead of Java-first-with-Kotlin-bolted-on-top. This
    means using Kotlin idiomatic API’s and practices. The API should be compatible with Kotlin
    Multiplatform.
 2. API should interop nicely with common Android libraries and language features in Kotlin. Examples
    being RxJava, Jetpack libraries, Android Compose, Sealed/Data classes, Coroutines, Flow.
 3. It should be easy for end users to test their apps if they include Realm. This means designing
    the API with testability in mind. E.g. JVM tests and injecting Schedulers/Threadpools/Dispatchers.
    This has been a recurring complaint for Realm Java.
 4. Should behave in a similar way to other binding SDK’s, i.e. naming and concepts like queries and
    migrations.
 5. Should behave similar to Realm Java to ease the transition.
 6. Should be a pleasant API experience if called from Java.

### Working guidelines

 * New design criteria: Find minimal building blocks for API’s in Kotlin and start with those to
   get experience. We can use extension functions to see what other use cases make sense. Example,
   just having `observe(): Flow<T>` for observability instead of findAll/findFirst/etc.
 * Ignore Java interop for now. Trying to focus on Java interop will just be a distraction. Support
   will be evaluated later.
 * We should try to make it clear that the API is primarily frozen, so having e.g.
   `object.update { }` would blur that line.


### Open questions


* Should we let our classes be `open`, e.g. so they can be mocked. This was the reason for not making
  them final in Java.
    - ANSWER: No, use the all-open compiler plugin if this need is there.

* Should we have interfaces for all our public classes so they can easily be mocked?

* Should we use packages a lot more to seperate functionality?
  akward with `io.realm.base.BaseRealm`

* Should it be `DynamicResults` or `DynamicRealmResults`
    * ANSWER: DynamicResults/List for now. It is shorter and DynamicRealms only have niche use cases.

* `Dynamic<X>` classes have been introduced to cleanup the type system and implementation details of
  those classes. The original reason for having something like `RealmResults<DynamicRealmObject>`
  was a concern about the method count. With MultiDex and ProGuard being widely available and used,
  this is no longer deemed a concern.

* How to handle manual init on JVM? What kind of init is requried on iOS? and JVM?

* Should we use `Realm.open(config = getDefaultConfiguration())` instead of `Realm.openDefault()`
    - ANSWER: Yes, for an initial API. Reason it being simpler and reducing API surface.

* There is no "File" abstraction in Kotlin common. How to handle this?

* How to query from the Realm class is a somewhat open question.

* How to support Dates? Especially in the Kotlin Common API?

* How to support BSON types in Kotlin Common?