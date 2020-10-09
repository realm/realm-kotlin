package io.realm

import android.support.test.runner.AndroidJUnit4
import io.realm.runtimeapi.RealmModelInternal
import junit.framework.TestCase.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import test.Sample
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
        realmModel.`$realm$IsManaged` = true
        realmModel.`$realm$ObjectPointer` = BindingPointer(0xCAFEBABE)
        realmModel.`$realm$Pointer` = BindingPointer(0XCAFED00D)
        realmModel.`$realm$TableName` = "Sample"

        assertEquals(true, realmModel.`$realm$IsManaged`)
        assertEquals(0xCAFEBABE, (realmModel.`$realm$ObjectPointer` as BindingPointer).ptr)
        assertEquals(0XCAFED00D, (realmModel.`$realm$Pointer` as BindingPointer).ptr)
        assertEquals("Sample", realmModel.`$realm$TableName`)
    }
}
