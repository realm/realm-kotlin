/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm

import io.realm.runtimeapi.Mediator
import io.realm.runtimeapi.RealmCompanion
import io.realm.runtimeapi.RealmModelInternal
import io.realm.runtimeapi.RealmModule
import test.A
import test.B
import test.C
import test.Entities
import test.Sample
import test.Subset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SampleTests {

    @RealmModule(Sample::class)
    class MySchema

    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        val configuration = RealmConfiguration.Builder(schema = MySchema()).build()
        realm = Realm.open(configuration)
        // FIXME Cleaning up realm to overcome lack of support for deleting actual files
        //  https://github.com/realm/realm-kotlin/issues/95
        realm.beginTransaction()
        realm.objects(Sample::class).delete()
        realm.commitTransaction()
        assertEquals(0, realm.objects(Sample::class).size, "Realm is not empty")
    }

    @Test
    fun testSyntheticSchemaMethodIsGenerated() {
        val expected = "{\"name\": \"Sample\", \"properties\": [" +
            "{\"stringField\": {\"type\": \"string\", \"nullable\": \"false\"}}," +
            "{\"byteField\": {\"type\": \"int\", \"nullable\": \"false\"}}," +
            "{\"charField\": {\"type\": \"int\", \"nullable\": \"false\"}}," +
            "{\"shortField\": {\"type\": \"int\", \"nullable\": \"false\"}}," +
            "{\"intField\": {\"type\": \"int\", \"nullable\": \"false\"}}," +
            "{\"longField\": {\"type\": \"int\", \"nullable\": \"false\"}}," +
            "{\"booleanField\": {\"type\": \"boolean\", \"nullable\": \"false\"}}," +
            "{\"floatField\": {\"type\": \"float\", \"nullable\": \"false\"}}," +
            "{\"doubleField\": {\"type\": \"double\", \"nullable\": \"false\"}}]}"
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
    fun createAndUpdate() {
        val s = "Hello, World!"

        realm.beginTransaction()
        val sample = realm.create(Sample::class)
        assertEquals("", sample.stringField)
        sample.stringField = s
        assertEquals(s, sample.stringField)
        realm.commitTransaction()
    }

    @Test
    fun delete() {
        val configuration = RealmConfiguration.Builder(schema = MySchema()).build()
        val realm = Realm.open(configuration)

        realm.beginTransaction()
        val sample = realm.create(Sample::class)
        Realm.delete(sample)
        assertFailsWith<IllegalArgumentException> {
            Realm.delete(sample)
        }
        assertFailsWith<IllegalStateException> {
            sample.stringField = "sadf"
        }
        realm.commitTransaction()
    }

    @Test
    fun query() {
        val s = "Hello, World!"

        realm.beginTransaction()
        realm.create(Sample::class).run { stringField = s }
        realm.create(Sample::class).run { stringField = "Hello, Realm!" }
        realm.commitTransaction()

        val objects1: RealmResults<Sample> = realm.objects(Sample::class)
        assertEquals(2, objects1.size)

        val objects2: RealmResults<Sample> =
            realm.objects(Sample::class).query("stringField == $0", s)
        assertEquals(1, objects2.size)
        for (sample in objects2) {
            assertEquals(s, sample.stringField)
        }
    }

    @Test
    fun query_parseErrorThrows() {
        val objects3: RealmResults<Sample> = realm.objects(Sample::class).query("name == str")
        // Will first fail when accessing the acutal elements as the query is lazily evaluated
        // FIXME Need appropriate error for syntax errors. Avoid UnsupportedOperationExecption as
        //  in realm-java ;)
        //  https://github.com/realm/realm-kotlin/issues/70
        assertFailsWith<RuntimeException> {
            println(objects3)
        }
    }

    @Test
    fun query_delete() {
        realm.beginTransaction()
        realm.create(Sample::class).run { stringField = "Hello, World!" }
        realm.create(Sample::class).run { stringField = "Hello, Realm!" }
        realm.commitTransaction()

        val objects1: RealmResults<Sample> = realm.objects(Sample::class)
        assertEquals(2, objects1.size)

        realm.beginTransaction()
        realm.objects(Sample::class).delete()
        realm.commitTransaction()

        assertEquals(0, realm.objects(Sample::class).size)
    }

    @Test
    fun testMediatorIsGeneratedForRealmModuleClasses() {
        val entities = Entities()
        var mediator = entities as? Mediator
            ?: error("Supertype Mediator was not added to Entities module")
        var schema = mediator.schema()
        assertEquals(4, schema.size) // all classes: Sample, A, B and C

        val instanceA = mediator.newInstance(A::class)
        val instanceB = mediator.newInstance(B::class)
        val instanceC = mediator.newInstance(C::class)
        val instanceSample = mediator.newInstance(Sample::class)

        assertTrue(instanceA is A)
        assertTrue(instanceB is B)
        assertTrue(instanceC is C)
        assertTrue(instanceSample is Sample)

        val subsetModule = Subset()
        mediator = subsetModule as? Mediator
            ?: error("Supertype Mediator was not added to Subset module")
        schema = mediator.schema()

        assertEquals(2, schema.size) // classes: A and C only

        val subsetInstanceA = mediator.newInstance(A::class)
        val subsetInstanceC = mediator.newInstance(C::class)
        // FIXME NH
        // 'IrTypeOperatorCallImpl with IMPLICIT_NOTNULL' IR instruction is not supported on K/N, it throws
        // (Java.lang.IllegalStateException: Not found Idx) consider fixing this to enable returning a
        // Null instance instead of throwing an NPE when making the below call. (test with recent version of Kotlin)
//        val subsetInstanceB = mediator.newInstance(B::class) // not part of the schema
//        assertNull(subsetInstanceB)

        assertTrue(subsetInstanceA is A)
        assertTrue(subsetInstanceC is C)

        assertNotEquals(subsetInstanceA, instanceA)
        assertNotEquals(subsetInstanceC, instanceC)
    }

    @Test
    fun primitiveTypes() {
        realm.beginTransaction()
        realm.create(Sample::class).apply {
            stringField = "Realm Kotlin"
            byteField = 0xb
            charField = 'b'
            shortField = 1
            intField = 2
            longField = 1024
            booleanField = false
            floatField = 1.99f
            doubleField = 1.19851106
        }
        realm.commitTransaction()

        var objects: RealmResults<Sample> = realm.objects(Sample::class)
        assertEquals(1, objects.size)

        assertEquals("Realm Kotlin", objects[0].stringField)
        assertEquals(0xb, objects[0].byteField)
        assertEquals('b', objects[0].charField)
        assertEquals(1, objects[0].shortField)
        assertEquals(2, objects[0].intField)
        assertEquals(1024, objects[0].longField)
        assertFalse(objects[0].booleanField)
        assertEquals(1.99f, objects[0].floatField)
        assertEquals(1.19851106, objects[0].doubleField)

        // querying on each type
        objects = realm.objects(Sample::class).query("stringField == $0", "Realm Kotlin") // string
        assertEquals(1, objects.size)

        objects = realm.objects(Sample::class).query("byteField == $0", 0xb) // byte
        assertEquals(1, objects.size)

        objects = realm.objects(Sample::class).query("charField == $0", 'b') // char
        assertEquals(1, objects.size)

        objects = realm.objects(Sample::class).query("shortField == $0", 1) // short
        assertEquals(1, objects.size)

        objects = realm.objects(Sample::class).query("intField == $0", 2) // int
        assertEquals(1, objects.size)

        objects = realm.objects(Sample::class).query("longField == $0", 1024) // long
        assertEquals(1, objects.size)

        objects = realm.objects(Sample::class).query("booleanField == false") // FIXME query("booleanField == $0", false) is not working
        assertEquals(1, objects.size)

        objects = realm.objects(Sample::class).query("floatField == $0", 1.99f)
        assertEquals(1, objects.size)

        objects = realm.objects(Sample::class).query("doubleField == $0", 1.19851106)
        assertEquals(1, objects.size)
    }
}
