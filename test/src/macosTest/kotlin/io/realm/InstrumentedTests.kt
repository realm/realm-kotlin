package io.realm

import io.realm.runtimeapi.RealmModelInternal
import kotlinx.cinterop.*
import test.Sample
import kotlin.test.Test
import kotlin.test.assertEquals

class InstrumentedTests {
    @Test
    fun testRealmModelInternalPropertiesGenerated() {
        val p = Sample()
        val realmModel: RealmModelInternal = p as? RealmModelInternal ?: error("Supertype RealmModelInternal was not added to Sample class")

        memScoped {
            val ptr1: COpaquePointerVar = alloc()
            val ptr2: COpaquePointerVar = alloc()

            // Accessing getters/setters
            realmModel.isManaged = true
            realmModel.realmObjectPointer = CPointerWrapper(ptr1.ptr)
            realmModel.realmPointer = CPointerWrapper(ptr2.ptr)
            realmModel.tableName = "Sample"

            assertEquals(true, realmModel.isManaged)
            assertEquals(ptr1.rawPtr.toLong(), (realmModel.realmObjectPointer as CPointerWrapper).ptr.toLong())
            assertEquals(ptr2.rawPtr.toLong(), (realmModel.realmPointer as CPointerWrapper).ptr.toLong())
            assertEquals("Sample", realmModel.tableName)
        }
    }
}