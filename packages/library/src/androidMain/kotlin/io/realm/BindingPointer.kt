package io.realm

import io.realm.runtimeapi.NativePointer

actual typealias BindingPointer = LongPointerWrapper

class LongPointerWrapper(val ptr: Long) : NativePointer
