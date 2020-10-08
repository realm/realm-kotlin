package io.realm

import io.realm.runtimeapi.NativePointer
import kotlinx.cinterop.CPointer

actual typealias BindingPointer = CPointerWrapper

class CPointerWrapper(val ptr: CPointer<*>) : NativePointer // TODO maybe use <out CPointed> instead of *
