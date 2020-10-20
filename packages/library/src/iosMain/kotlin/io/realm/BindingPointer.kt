package io.realm

import io.realm.runtimeapi.NativePointer
import kotlinx.cinterop.CPointer

internal actual typealias BindingPointer = CPointerWrapper

internal class CPointerWrapper(val ptr: CPointer<*>) : NativePointer // TODO maybe use <out CPointed> instead of *
