## We cannot discard unused symbols for the non-test configurations as it might  all symbols not used
-dontoptimize
-dontshrink

# io.realm.kotlin.test.mongodb.shared.FlexibleSyncConfigurationTests.toString_nonEmpty,
# SyncConfigTests.unsupportedSchemaTypesThrowException_flexibleSync and
# SyncConfigTests.unsupportedSchemaTypesThrowException_partitionBasedSync verifies exception
# messages with explicit class names in them
-keep class io.realm.kotlin.mongodb.internal.SyncConfigurationImpl
-keep class io.realm.kotlin.dynamic.DynamicRealmObject

## Serialization related rules
-keepclasseswithmembers class **.*$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
# If a companion has the serializer function, keep the companion field on the original type so that
# the reflective lookup succeeds.
-if class **.*$Companion {
  kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class <1>.<2> {
  <1>.<2>$Companion Companion;
}

## Required to make introspection by reflection in NullabilityTests work
-keep class io.realm.kotlin.types.MutableRealmInt
-keep class io.realm.kotlin.types.RealmUUID
-keep class io.realm.kotlin.types.RealmList
-keep class org.mongodb.kbson.*
