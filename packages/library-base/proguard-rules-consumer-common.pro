## Keep Companion classes and class.Companion member of all classes that can be used in our API to
#  allow calling realmObjectCompanionOrThrow and realmObjectCompanionOrNull on the classes
-keep class io.realm.kotlin.types.ObjectId$Companion
-keepclassmembers class io.realm.kotlin.types.ObjectId {
    io.realm.kotlin.types.ObjectId$Companion Companion;
}
-keep class io.realm.kotlin.types.RealmInstant$Companion
-keepclassmembers class io.realm.kotlin.types.RealmInstant {
    io.realm.kotlin.types.RealmInstant$Companion Companion;
}
-keep class org.mongodb.kbson.BsonObjectId$Companion
-keepclassmembers class org.mongodb.kbson.BsonObjectId {
    org.mongodb.kbson.BsonObjectId$Companion Companion;
}
-keep class io.realm.kotlin.dynamic.DynamicRealmObject$Companion, io.realm.kotlin.dynamic.DynamicMutableRealmObject$Companion
-keepclassmembers class io.realm.kotlin.dynamic.DynamicRealmObject, io.realm.kotlin.dynamic.DynamicMutableRealmObject {
    **$Companion Companion;
}
-keep,allowobfuscation class ** implements io.realm.kotlin.types.BaseRealmObject
-keep class ** implements io.realm.kotlin.internal.RealmObjectCompanion
-keepclassmembers class ** implements io.realm.kotlin.types.BaseRealmObject {
    **$Companion Companion;
}

## Preserve all native method names and the names of their classes.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

## Preserve all classes that are looked up from native code
# Notification callback
-keep class io.realm.kotlin.internal.interop.NotificationCallback {
    *;
}
# Utils to convert core errors into Kotlin exceptions
-keep class io.realm.kotlin.internal.interop.CoreErrorUtils {
    *;
}
-keep class io.realm.kotlin.internal.interop.JVMScheduler {
    *;
}
# Interop, sync-specific classes
-keep class io.realm.kotlin.internal.interop.sync.NetworkTransport {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.realm.kotlin.internal.interop.sync.Response {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.realm.kotlin.internal.interop.LongPointerWrapper {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.realm.kotlin.internal.interop.sync.AppError {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.realm.kotlin.internal.interop.sync.SyncError {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.realm.kotlin.internal.interop.SyncLogCallback {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.realm.kotlin.internal.interop.SyncErrorCallback {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.realm.kotlin.internal.interop.sync.JVMSyncSessionTransferCompletionCallback {
    *;
}
-keep class io.realm.kotlin.internal.interop.sync.ResponseCallback {
    *;
}
-keep class io.realm.kotlin.internal.interop.sync.ResponseCallbackImpl {
    *;
}
-keep class io.realm.kotlin.internal.interop.AppCallback {
    *;
}
-keep class io.realm.kotlin.internal.interop.CompactOnLaunchCallback {
    *;
}
-keep class io.realm.kotlin.internal.interop.MigrationCallback {
    *;
}
-keep class io.realm.kotlin.internal.interop.DataInitializationCallback {
    *;
}
-keep class io.realm.kotlin.internal.interop.SubscriptionSetCallback {
    *;
}
-keep class io.realm.kotlin.internal.interop.SyncBeforeClientResetHandler {
    *;
}
-keep class io.realm.kotlin.internal.interop.SyncAfterClientResetHandler {
    *;
}
-keep class io.realm.kotlin.internal.interop.AsyncOpenCallback {
    *;
}
-keep class io.realm.kotlin.internal.interop.NativePointer {
    *;
}
-keep class io.realm.kotlin.internal.interop.ProgressCallback {
    *;
}
-keep class io.realm.kotlin.internal.interop.sync.ApiKeyWrapper {
    *;
}
-keep class io.realm.kotlin.internal.interop.ConnectionStateChangeCallback {
    *;
}
# Preserve Function<X> methods as they back various functional interfaces called from JNI
-keep class kotlin.jvm.functions.Function* {
    *;
}
-keep class kotlin.Unit {
    *;
}

# Un-comment for debugging
#-printconfiguration /tmp/full-r8-config.txt
#-keepattributes LineNumberTable,SourceFile
#-printusage /tmp/removed_entries.txt
#-printseeds /tmp/kept_entries.txt
