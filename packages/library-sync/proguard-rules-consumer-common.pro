# Add sync-related proguard exceptions here

-keep class io.realm.internal.interop.CinteropCallback {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.realm.internal.interop.NetworkTransport {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
-keep class io.realm.internal.interop.Response {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
