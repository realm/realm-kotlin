# We cannot discard unused symbols for the non-test configurations as it might  all symbols not used
-dontoptimize
-dontshrink

## Required to make assertions on incorrect type messages in dynamic realm object tests pass
-keep class io.realm.kotlin.types.BaseRealmObject
-keep class io.realm.kotlin.types.RealmUUID

## Required to make introspection by reflection in NullabilityTests work
-keep class io.realm.kotlin.types.MutableRealmInt
-keep class io.realm.kotlin.entities.Nullability {
   *;
}

## Required to make introspection by reflection in PrimaryKeyTests work
-keepclassmembers class io.realm.kotlin.entities.primarykey.* {
  *;
}
