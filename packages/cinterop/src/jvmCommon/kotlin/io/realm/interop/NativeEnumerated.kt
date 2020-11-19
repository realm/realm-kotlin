package io.realm.interop

// Interface to hold C API enumerated constant reference by SWIG constant
interface NativeEnumerated {
    val nativeValue: Int
}
