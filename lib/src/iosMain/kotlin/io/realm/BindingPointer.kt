package io.realm

import kotlinx.cinterop.CPointer

actual typealias BindingPointer = CPointerWrapper

class CPointerWrapper(val ptr : CPointer<*>)//TODO maybe use <out CPointed> instead of *