package io.realm

import io.realm.runtimeapi.NativePointer

// TODO Currently public to work around this being used in Android Integration Tests
public actual typealias BindingPointer = LongPointerWrapper

// TODO Currently public to work around this being used in Android Integration Tests
public class LongPointerWrapper(val ptr: Long) : NativePointer
