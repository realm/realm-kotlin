/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// FIXME Don't know how to call Sample::class.realmObjectCompanion() with
//  import io.realm.kotlin.internal.platform.realmObjectCompanion
// And cannot only supresss that single import
@file:Suppress("invisible_member", "invisible_reference")

package io.realm.kotlin.test.common

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.internal.RealmObjectCompanion
import io.realm.kotlin.internal.realmObjectCompanionOrThrow
import io.realm.kotlin.query.find
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.types.RealmInstant
import org.mongodb.kbson.Decimal128
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class SampleTests {

    lateinit var tmpDir: String
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration.Builder(schema = setOf(Sample::class))
                .directory(tmpDir)
                .build()
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        if (this::realm.isInitialized && !realm.isClosed()) {
            realm.close()
        }
        PlatformUtils.deleteTempDir(tmpDir)
    }

    // Tests that we can resolve RealmObjectCompanion from KClass<out RealmObject>
    @Test
    fun realmObjectCompanion() {
        assertIs<RealmObjectCompanion>(Sample::class.realmObjectCompanionOrThrow())
        // Needs fully qualified reference otherwise it will somehow overlap with the above and
        // generated the following compilation error:
        //   Caused by: java.lang.AssertionError: Unexpected IR element found during code generation. Either code generation for it is not implemented, or it should have been lowered:
        //   ERROR_CALL 'Cannot bind 1 arguments to 'FUN IR_EXTERNAL_DECLARATION_STUB name:realmObjectCompanionOrThrow visibility:internal modality:FINAL <T> ($receiver:kotlin.reflect.KClass<T of io.realm.kotlin.internal.realmObjectCompanionOrThrow>) returnType:io.realm.kotlin.internal.RealmObjectCompanion [inline]' call with 0 parameters' type=io.realm.kotlin.internal.RealmObjectCompanion
        // The issue goes away if the symbols are publicly available from the library, so related
        // to accessing invisible members/references, thus didn't investigate further
        assertIs<RealmObjectCompanion>(io.realm.kotlin.internal.platform.realmObjectCompanionOrThrow(Sample::class))
    }

    @Test
    fun createAndUpdate() {
        val s = "Hello, World!"

        realm.writeBlocking {
            val sample = copyToRealm(Sample())
            assertEquals("Realm", sample.stringField)
            sample.stringField = s
            assertEquals(s, sample.stringField)
        }
    }

    @Test
    fun validateInternalGetterAndSetter() {
        realm.writeBlocking {
            val s = copyToRealm(Sample())
            val value = "UPDATE"
            s.stringFieldSetter(value)
            assertEquals(value, s.stringField)
            assertEquals(value, s.stringFieldGetter())
        }
    }

    @Test
    fun updateOutsideTransactionThrows() {
        val s = "Hello, World!"
        val sample: Sample = realm.writeBlocking {
            val sample = copyToRealm(Sample())
            sample.stringField = s
            assertEquals(s, sample.stringField)
            sample
        }

        assertFailsWith<IllegalStateException> {
            sample.stringField = "ASDF"
        }
    }

    @Test
    fun delete() {
        realm.writeBlocking {
            val sample = copyToRealm(Sample())
            delete(sample)
            assertFailsWith<IllegalArgumentException> {
                delete(sample)
            }
            assertFailsWith<IllegalStateException> {
                sample.stringField = "sadf"
            }
        }
    }

    @Test
    fun query() {
        val s = "Hello, World!"

        realm.writeBlocking {
            copyToRealm(Sample()).run { stringField = s }
            copyToRealm(Sample()).run { stringField = "Hello, Realm!" }
        }

        realm.query<Sample>()
            .find { results ->
                assertEquals(2, results.size)
            }

        realm.query<Sample>("stringField == $0", s)
            .find { results ->
                assertEquals(1, results.size)
                for (sample in results) {
                    assertEquals(s, sample.stringField)
                }
            }
    }

    @Test
    fun query_parseErrorThrows() {
        realm.query<Sample>()
            .find { results ->
                assertFailsWith<IllegalArgumentException> {
                    results.query("name == str")
                }
            }
    }

    @Test
    fun query_delete() {
        realm.writeBlocking {
            copyToRealm(Sample()).run { stringField = "Hello, World!" }
            copyToRealm(Sample()).run { stringField = "Hello, Realm!" }
        }

        realm.query<Sample>()
            .find { results ->
                assertEquals(2, results.size)
            }

        realm.writeBlocking {
            delete(query<Sample>())
        }

        assertEquals(0, realm.query<Sample>().find().size)
    }

    @Test
    @Suppress("LongMethod")
    fun primitiveTypes() {
        val oid = org.mongodb.kbson.ObjectId()
        realm.writeBlocking {
            copyToRealm(Sample()).apply {
                stringField = "Realm Kotlin"
                byteField = 0xb
                charField = 'b'
                shortField = 1
                intField = 2
                longField = 1024
                booleanField = false
                floatField = 1.99f
                doubleField = 1.19851106
                decimal128Field = Decimal128("2.155544073709551618E-6157")
                timestampField = RealmInstant.from(42, 420)
                bsonObjectIdField = oid
            }
        }

        realm.query<Sample>()
            .find { objects ->
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
                assertEquals(Decimal128("2.155544073709551618E-6157"), objects[0].decimal128Field)
                assertEquals(RealmInstant.from(42, 420), objects[0].timestampField)
                assertEquals(oid, objects[0].bsonObjectIdField)
            }

        // querying on each type
        realm.query<Sample>("stringField == $0", "Realm Kotlin") // string
            .find { objects ->
                assertEquals(1, objects.size)
            }

        realm.query<Sample>("byteField == $0", 0xb) // byte
            .find { objects ->
                assertEquals(1, objects.size)
            }

        realm.query<Sample>("charField == $0", 'b') // char
            .find { objects ->
                assertEquals(1, objects.size)
            }

        realm.query<Sample>("shortField == $0", 1) // short
            .find { objects ->
                assertEquals(1, objects.size)
            }

        realm.query<Sample>("intField == $0", 2) // int
            .find { objects ->
                assertEquals(1, objects.size)
            }

        realm.query<Sample>("longField == $0", 1024) // long
            .find { objects ->
                assertEquals(1, objects.size)
            }

        realm.query<Sample>("booleanField == false") // FIXME query("booleanField == $0", false) is not working
            .find { objects ->
                assertEquals(1, objects.size)
            }

        realm.query<Sample>("floatField == $0", 1.99f)
            .find { objects ->
                assertEquals(1, objects.size)
            }

        realm.query<Sample>("doubleField == $0", 1.19851106)
            .find { objects ->
                assertEquals(1, objects.size)
            }

        realm.query<Sample>("decimal128Field >= $0", Decimal128("2.155544073709551618E-6157"))
            .find { objects ->
                assertEquals(1, objects.size)
                assertEquals(Decimal128("2.155544073709551618E-6157"), objects[0].decimal128Field)
            }

        realm.query<Sample>("timestampField == $0", RealmInstant.from(42, 420))
            .find { objects ->
                assertEquals(1, objects.size)
            }
    }

    @Test
    fun objectAssignmentDetectsDuplicates() {
        val leaf = Sample().apply { intField = 1 }
        val child = Sample().apply {
            intField = 2
            nullableObject = leaf
            objectListField = realmListOf(leaf, leaf)
        }
        realm.writeBlocking {
            copyToRealm(Sample()).apply {
                nullableObject = child
            }
        }
        assertEquals(3, realm.query<Sample>().find().size)
    }

    // Exhaustive test for all types are done in RealmListTest.assignField to leverage on the
    // RealmListTest infrastructure
    // @Test
    // fun list_assign_allTypes() {}

    @Test
    fun list_assign_unmanaged() {
        realm.writeBlocking {
            val objectList: List<Sample> = (0..9).map { Sample().apply { intField = it } }
            val sample = copyToRealm(Sample()).apply { objectListField = objectList.toRealmList() }
        }
        assertEquals(11, realm.query<Sample>().count().find())
    }

    @Test
    fun list_assign_managed() {
        realm.writeBlocking {
            val objectList: List<Sample> = (0..9).map { Sample().apply { intField = it } }
            val sample1 = copyToRealm(Sample()).apply {
                stringField = "1"
                objectListField = objectList.toRealmList()
            }
            copyToRealm(Sample()).apply {
                stringField = "2"
                objectListField = sample1.objectListField
            }
        }
        assertEquals(12, realm.query<Sample>().count().find())
        val sample2 = realm.query<Sample>("stringField = '2'").find().single()
        assertEquals(10, sample2.objectListField.size)
    }

    @Test
    fun list_assign_selfAssignment() {
        realm.writeBlocking {
            val intList: List<Int> = (0..9).toList()
            val sample = copyToRealm(Sample()).apply { intListField = intList.toRealmList() }
            val list = sample.intListField
            sample.intListField = list
            assertContentEquals(intList, sample.intListField)
        }
    }
}
