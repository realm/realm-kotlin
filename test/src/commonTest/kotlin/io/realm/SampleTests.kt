package io.realm

import io.realm.runtimeapi.RealmModel
import io.realm.runtimeapi.RealmModelInternal
import test.Sample
import kotlin.test.Test
import kotlin.test.assertEquals

class SampleTests {
    @Test
    fun testSyntheticSchemaMethodIsGenerated() {
        val expected = "{\"name\": \"Sample\", \"properties\": [{\"name\": {\"type\": \"string\", \"nullable\": \"false\"}}]}"
        assertEquals(expected, Sample.`$realm$schema`())
    }

    @Test
    fun testRealmModelInternalAndMarkerAreImplemented() {
        val p = Sample()
        p as? RealmModelInternal ?: error("Supertype RealmModelInternal was not added to Sample class")
    }
}
