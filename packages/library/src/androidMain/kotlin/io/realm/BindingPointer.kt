package io.realm

import io.realm.runtimeapi.NativePointer

internal actual typealias BindingPointer = LongPointerWrapper

internal class LongPointerWrapper(val ptr: Long) : NativePointer
