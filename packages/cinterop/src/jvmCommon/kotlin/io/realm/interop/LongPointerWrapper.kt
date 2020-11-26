 package io.realm.interop

 import io.realm.runtimeapi.NativePointer

 // JVM/Android specific pointer wrapper
 // FIXME Should ideally be moved to jni-swig-stub as we would be able to use Swig to wrap/unwrap
 //  all pointers going in and out of the JNI layer, handling transferring ownership, etc. But,
 //  doing so currently renders Android studio unable to resolve the NativePointer from the
 //  runtiem-api mpp-module, which ruins IDE solving of the while type hierarchy around the
 //  pointers, which makes in annoying to work with.
 //  https://issuetracker.google.com/issues/174162078
 class LongPointerWrapper(val ptr: Long) : NativePointer
