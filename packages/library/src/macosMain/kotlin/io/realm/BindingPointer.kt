package io.realm

import io.realm.runtimeapi.NativePointer
import kotlinx.cinterop.CPointer

// TODO Currently public to work around this being used in Android Integration Tests
public actual typealias BindingPointer = CPointerWrapper

// TODO Currently public to work around this being used in Android Integration Tests
internal class CPointerWrapper(val ptr: CPointer<*>) : NativePointer // TODO maybe use <out CPointed> instead of *
