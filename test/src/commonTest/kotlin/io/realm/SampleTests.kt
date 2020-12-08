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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SampleTests {

    @RealmModule(Sample::class)
    class MySchema

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
    @Suppress("CAST_NEVER_SUCCEEDS")
    fun realmConfig() {
        val configuration = RealmConfiguration.Builder(schema = MySchema()).build()
        val realm = Realm.open(configuration)

        realm.beginTransaction()
        val sample = realm.create(Sample::class)
        assertEquals("", sample.name)
        sample.name = "Hello, World!"
        assertEquals("Hello, World!", sample.name)
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
            sample.name = "sadf"
        }
        realm.commitTransaction()
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
}
