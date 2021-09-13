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

# Un-comment for debugging
#-printconfiguration /tmp/full-r8-config.txt
#-keepattributes LineNumberTable,SourceFile
#-printusage /tmp/removed_entries.txt
#-printseeds /tmp/kept_entries.txt
