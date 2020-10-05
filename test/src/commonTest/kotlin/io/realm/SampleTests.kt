package io.realm

import io.realm.runtimeapi.RealmModelInternal
import test.Sample
import kotlin.test.Test
import kotlin.test.assertEquals

class SampleTests {
    @Test
    fun testSyntheticSchemaMethodIsGenerated() {
        val expected = "{\"name\": \"Sample\", \"properties\": [{\"name\": {\"type\": \"string\", \"nullable\": \"true\"}}]}"
        assertEquals(expected, Sample.schema())
    }

    @Suppress("UNREACHABLE_CODE")
    @Test
    fun testRealmModelInternalIsImplemented() {
        val p = Sample()
        val realmModel: RealmModelInternal = p as? RealmModelInternal ?: error("Supertype RealmModelInternal was not added to Sample class")

        // Accessing getters/setters
        realmModel.isManaged = true
        realmModel.realmObjectPointer = 0xCAFEBABE
        realmModel.realmPointer = 0XCAFED00D
        realmModel.tableName = "Sample"

        assertEquals(true, realmModel.isManaged)
        assertEquals(0xCAFEBABE, realmModel.realmObjectPointer)
        assertEquals(0XCAFED00D, realmModel.realmPointer)
        assertEquals("Sample", realmModel.tableName)
    }
}
