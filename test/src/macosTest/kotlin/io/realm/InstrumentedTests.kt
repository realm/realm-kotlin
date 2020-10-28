package io.realm

import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmModelInternal
import kotlinx.cinterop.*
import test.Sample
import kotlin.test.Test
import kotlin.test.assertEquals

class InstrumentedTests {

    // TODO Remove when 'library' and 'cinterop' with actual platform API is in place.
    class CPointerWrapper(val ptr: CPointer<*>) : NativePointer

    @Test
    fun testRealmModelInternalPropertiesGenerated() {
        val p = Sample()
        val realmModel: RealmModelInternal = p as? RealmModelInternal ?: error("Supertype RealmModelInternal was not added to Sample class")

        memScoped {
            val ptr1: COpaquePointerVar = alloc()
            val ptr2: COpaquePointerVar = alloc()

            // Accessing getters/setters
            realmModel.`$realm$IsManaged` = true
            realmModel.`$realm$ObjectPointer` = CPointerWrapper(ptr1.ptr)
            realmModel.`$realm$Pointer` = CPointerWrapper(ptr2.ptr)
            realmModel.`$realm$TableName` = "Sample"

            assertEquals(true, realmModel.`$realm$IsManaged`)
            assertEquals(ptr1.rawPtr.toLong(), (realmModel.`$realm$ObjectPointer` as CPointerWrapper).ptr.toLong())
            assertEquals(ptr2.rawPtr.toLong(), (realmModel.`$realm$Pointer` as CPointerWrapper).ptr.toLong())
            assertEquals("Sample", realmModel.`$realm$TableName`)
        }
    }
}
