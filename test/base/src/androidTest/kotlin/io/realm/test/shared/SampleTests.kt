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
//  import io.realm.internal.platform.realmObjectCompanion
// And cannot only supresss that single import
@file:Suppress("invisible_member", "invisible_reference")

package io.realm.test.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmInstant
import io.realm.entities.Sample
import io.realm.internal.RealmObjectCompanion
import io.realm.internal.platform.realmObjectCompanionOrThrow
import io.realm.internal.realmObjectCompanionOrThrow
import io.realm.query
import io.realm.query.find
import io.realm.test.platform.PlatformUtils
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
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
                .path("$tmpDir/default.realm")
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
        assertIs<RealmObjectCompanion>(realmObjectCompanionOrThrow(Sample::class))
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
                timestampField = RealmInstant.fromEpochSeconds(42, 420)
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
                assertEquals(RealmInstant.fromEpochSeconds(42, 420), objects[0].timestampField)
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

        realm.query<Sample>("timestampField == $0", RealmInstant.fromEpochSeconds(42, 420))
            .find { objects ->
                assertEquals(1, objects.size)
            }
    }
}
