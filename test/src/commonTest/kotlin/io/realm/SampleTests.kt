package io.realm

import io.realm.runtimeapi.RealmCompanion
import io.realm.runtimeapi.RealmModelInternal
import test.Sample
import kotlin.test.Test
import kotlin.test.assertEquals

class SampleTests {

    @Test
    fun testSyntheticSchemaMethodIsGenerated() {
        val expected = "{\"name\": \"Sample\", \"properties\": [{\"name\": {\"type\": \"string\", \"nullable\": \"false\"}}]}"
        assertEquals(expected, Sample.`$realm$schema`())
        val actual: RealmCompanion = Sample.Companion as RealmCompanion
        assertEquals(expected, actual.`$realm$schema`())
    }

    @Test
    fun testRealmModelInternalAndMarkerAreImplemented() {
        val p = Sample()
        p as? RealmModelInternal ?: error("Supertype RealmModelInternal was not added to Sample class")
    }
}
