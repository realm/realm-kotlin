### Open questions

* Changes from Realm Java
    * Lazy-evaluated queries with String syntax. `RealmConfiguration.allowQueriesOnUiThread()` can be used to 
      prevent accidentially running them on the UI thread.
    * Automatic migration support but API is open for later support for manual migrations.
    * All async functions has been converted to coroutines. Blocking behavior can be accomplished by using `runBlocking { }`
    * More heavily use of packages due to Kotlin module scope. 
    * DynamicRealm now gets its own class hierarchy.
    * RealmResults can now be created as unmanaged, helping with field initializing for e.g. @LinkingObjects
    * We are still using named interfaces since SAM conversion is now good and it improves the interop with Java.
    * ChangeListeners have been removed completely pending review. Mostly for interop with Java
    * With lazy-loaded queries. `isLoaded` as a state is gone.
    
        
* Should we let our classes be `open`, e.g. so they can be mocked. This was the reason for not making them final in Java.
* Should we have interfaces for all our classes?
* Should we use packages a lot more to seperate functionality?
    * Does `io.realm.base` make sense? If `io.realm.dynamic.DynamicRealm` is a thing, would it be akward with `io.realm.base.BaseRealm`
* Should it be `DynamicResults` or `DynamicRealmResults`
* Introduces `MutableRealmSchema` as a separate class putting it inline with other Kotlin API's like `MutableArrayList`
* `Dynamic<X>` classes have been introduced to cleanup the type system and implementation details of those classes.
  The original reason for having something like `RealmResults<DynamicRealmObject>` was a concern about the method count.
  With MultiDex and ProGuard being widely available and used, this is no longer deemed a concern. 
* How to handle manual init on JVM? What kind of init is requried on iOS? and JVM?
* Should we use `Realm.open(config = getDefaultConfiguration())` instead of `Realm.openDefault()`
* If we can simplify how objects are created to simply `add()` that would be very helpful.
* There is no "File" abstraction in Kotlin common.
* How to query from the Realm class is a somewhat open question.
* How to support Dates?
* How to support BSON types in Kotlin Common?