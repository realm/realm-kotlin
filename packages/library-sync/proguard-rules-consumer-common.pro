# Add sync-exclusive proguard exceptions here
# DO NOT PUT THEM IN THIS CONSUMER IN CASE THEY ARE UNDER 'cinterop'
# in which case they should be added to the 'library-base' proguard consumer instead.
-keep class io.realm.kotlin.internal.interop.sync.ResponseCallbackImpl {
    # TODO OPTIMIZE Only keep actually required symbols
    *;
}
