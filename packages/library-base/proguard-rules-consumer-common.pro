# Keep all classes implemeting the RealmObject interface
-keep class io.realm.RealmObject
-keep class * implements io.realm.RealmObject { *; }
#-keep class **.$* implements io.realm.RealmObject { *; }

# Preserve all native method names and the names of their classes.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Notification callback
-keep class io.realm.internal.interop.NotificationCallback {
    *;
}

# Utils to convert core errors into Kotlin exceptions
-keep class io.realm.interop.CoreErrorUtils {
    *;
}

-keep class io.realm.internal.interop.JVMScheduler {
    *;
}

# Prevent all RealmObjects from having their companions stripped
-keep class ** implements io.realm.internal.RealmObjectCompanion {
    *;
}

# Interop, sync-specific classes
-keep class io.realm.internal.interop.sync.NetworkTransport {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.realm.internal.interop.sync.Response {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.realm.internal.interop.LongPointerWrapper {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.realm.mongodb.AppException {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.realm.mongodb.SyncException {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.realm.internal.interop.SyncLogCallback {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.realm.internal.interop.SyncErrorCallback {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.realm.internal.interop.sync.JVMSyncSessionTransferCompletionCallback {
    *;
}
-keep class io.realm.internal.interop.sync.ResponseCallbackImpl {
    *;
}
-keep class io.realm.internal.interop.AppCallback {
    *;
}
-keep class io.realm.internal.interop.CompactOnLaunchCallback {
    *;
}
-keep class io.realm.internal.interop.MigrationCallback {
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
