package io.realm

import android.support.test.runner.AndroidJUnit4
import junit.framework.TestCase.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import test.Sample
import io.realm.runtimeapi.RealmModelInternal
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class InstrumentedTests {

    // Smoke test of compiling with library
    @Test
    fun contextIsNotNull() {
        assertNotNull(RealmInitProvider.applicationContext)
    }

    @Test
    fun testRealmModelInternalPropertiesGenerated() {
        val p = Sample()
        val realmModel: RealmModelInternal = p as? RealmModelInternal ?: error("Supertype RealmModelInternal was not added to Sample class")

        // Accessing getters/setters
        realmModel.isManaged = true
        realmModel.realmObjectPointer = BindingPointer(0xCAFEBABE)
        realmModel.realmPointer = BindingPointer(0XCAFED00D)
        realmModel.tableName = "Sample"

        assertEquals(true, realmModel.isManaged)
        assertEquals(0xCAFEBABE, (realmModel.realmObjectPointer as BindingPointer).ptr)
        assertEquals(0XCAFED00D, (realmModel.realmPointer as BindingPointer).ptr)
        assertEquals("Sample", realmModel.tableName)
    }

}
