package io.realm

import io.realm.runtimeapi.RealmCompanion
import io.realm.runtimeapi.RealmModelInternal
import test.Sample
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SampleTests {

    @Test
    fun testSyntheticSchemaMethodIsGenerated() {
        val expected = "{\"name\": \"Sample\", \"properties\": [{\"name\": {\"type\": \"string\", \"nullable\": \"false\"}}]}"
        assertEquals(expected, Sample.`$realm$schema`())
        @Suppress("CAST_NEVER_SUCCEEDS")
        val actual: RealmCompanion = Sample.Companion as RealmCompanion
        assertEquals(expected, actual.`$realm$schema`())
    }

    @Test
    fun testRealmModelInternalAndMarkerAreImplemented() {
        val p = Sample()
        @Suppress("CAST_NEVER_SUCCEEDS")
        p as? RealmModelInternal
            ?: error("Supertype RealmModelInternal was not added to Sample class")
    }

    @Test
    fun realmConfig() {
        @Suppress("CAST_NEVER_SUCCEEDS")
        val configuration = RealmConfiguration.Builder()
            // FIXME MEDIATOR Should be removed once we have module generation in place
            .factory { kClass ->
                when (kClass) {
                    Sample::class -> Sample()
                    else -> TODO()
                }
            }
            // FIXME MEDIATOR Should be removed once we have module generation in place
            .classes(
                listOf(
                    Sample.Companion as RealmCompanion
                )
            )
            .build()
        val realm = Realm.open(configuration)
        realm.beginTransaction()
        val sample = realm.create(Sample::class)
        kotlin.test.assertEquals("", sample.name)
        sample.name = "Hello, World!"
        kotlin.test.assertEquals("Hello, World!", sample.name)
        realm.commitTransaction()

        val objects1: RealmResults<Sample> = realm.objects(Sample::class)
        val sample1 = objects1[0]

        val objects2: RealmResults<Sample> = realm.objects(Sample::class).query("name == $0", "Hello, World!")
        val sample2 = objects2[0]

        val objects3: RealmResults<Sample> = realm.objects(Sample::class).query("name == str")
        // Will first fail when accessing the acutal elements as the query is lazily evaluated
        assertFailsWith<RuntimeException> {
            println(objects3)
        }
    }
}
