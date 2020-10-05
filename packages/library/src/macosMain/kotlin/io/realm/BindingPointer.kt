package io.realm

import kotlinx.cinterop.CPointer
import io.realm.runtimeapi.NativePointer

actual typealias BindingPointer = CPointerWrapper

class CPointerWrapper(val ptr : CPointer<*>) : NativePointer//TODO maybe use <out CPointed> instead of *
