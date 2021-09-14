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
package io.realm.shared

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmResults
import io.realm.delete
import io.realm.test.platform.PlatformUtils
import io.realm.util.Utils.createRandomString
import test.Sample
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SampleTests {

    lateinit var tmpDir: String
    lateinit var realm: Realm

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.with(path = "$tmpDir/${createRandomString(16)}.realm", schema = setOf(Sample::class))
        realm = Realm.open(configuration)
    }

    @AfterTest
    fun tearDown() {
        realm.close()
        PlatformUtils.deleteTempDir(tmpDir)
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
                sample.delete()
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
        val objects: RealmResults<Sample> = realm.objects(Sample::class)
        assertFailsWith<IllegalArgumentException> {
            objects.query("name == str")
        }
    }

    @Test
    fun query_delete() {
        realm.writeBlocking {
            copyToRealm(Sample()).run { stringField = "Hello, World!" }
            copyToRealm(Sample()).run { stringField = "Hello, Realm!" }
        }

        val objects1: RealmResults<Sample> = realm.objects(Sample::class)
        assertEquals(2, objects1.size)

        realm.writeBlocking {
            objects(Sample::class).delete()
        }

        assertEquals(0, realm.objects(Sample::class).size)
    }

    @Test
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
            }
        }

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
