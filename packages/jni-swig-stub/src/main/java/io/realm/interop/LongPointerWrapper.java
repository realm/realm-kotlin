package io.realm.interop;
import io.realm.runtimeapi.NativePointer;

// JVM/Android specific pointer wrapper
public class LongPointerWrapper implements NativePointer {
    public final long ptr;
    public LongPointerWrapper(long pointer) {
        ptr = pointer;
    }
}
