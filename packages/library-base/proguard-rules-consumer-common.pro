# Keep all classes implemeting the RealmObject interface
-keep class io.realm.kotlin.RealmObject
-keep class * implements io.realm.kotlin.RealmObject { *; }
#-keep class **.$* implements io.realm.kotlin.RealmObject { *; }

# Preserve all native method names and the names of their classes.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Notification callback
-keep class io.realm.kotlin.internal.interop.NotificationCallback {
    *;
}

# Utils to convert core errors into Kotlin exceptions
-keep class io.realm.kotlin.interop.CoreErrorUtils {
    *;
}

-keep class io.realm.kotlin.internal.interop.JVMScheduler {
    *;
}

# Prevent all RealmObjects from having their companions stripped
-keep class ** implements io.realm.kotlin.internal.RealmObjectCompanion {
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

# Preserve Function<X> methods as they back various functional interfaces called from JNI
-keep class kotlin.jvm.functions.Function* {
    *;
}

# Un-comment for debugging
#-printconfiguration /tmp/full-r8-config.txt
#-keepattributes LineNumberTable,SourceFile
#-printusage /tmp/removed_entries.txt
#-printseeds /tmp/kept_entries.txt
