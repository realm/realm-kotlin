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

package io.realm.kotlin.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.Sample
import io.realm.kotlin.internal.platform.RealmInitializer
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.find
import io.realm.kotlin.test.platform.PlatformUtils
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class InstrumentedTests {

    val context = InstrumentationRegistry.getInstrumentation().context
    lateinit var tmpDir: String
    lateinit var realm: Realm

    @Before
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .build()
        realm = Realm.open(configuration)
    }

    @After
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    // Smoke test of compiling with library
    @Test
    fun contextIsNotNull() {
        assertNotNull(RealmInitializer.filesDir)
    }

    // This could be a common test, but included here for convenience as there is no other easy
    // way to trigger individual common test on Android
    // https://youtrack.jetbrains.com/issue/KT-34535
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
    fun query() {
        val s = "Hello, World!"

        realm.writeBlocking {
            copyToRealm(Sample()).run { stringField = s }
            copyToRealm(Sample()).run { stringField = "Hello, Realm!" }
        }

        realm.query(Sample::class)
            .find {
                assertEquals(2, it.size)
            }

        realm.query(Sample::class)
            .query("stringField == $0", s)
            .find { results ->
                assertEquals(1, results.size)
                for (sample in results) {
                    assertEquals(s, sample.stringField)
                }
            }

        realm.query(Sample::class)
            .query("stringField == $0", s)
            .find { results ->
                assertEquals(1, results.size)
                for (sample in results) {
                    assertEquals(s, sample.stringField)
                }
            }
    }

    @Test
    fun query_parseErrorThrows() {
        val objects: RealmResults<Sample> = realm.query(Sample::class).find()
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

        realm.query(Sample::class)
            .find { results ->
                assertEquals(2, results.size)
            }

        realm.writeBlocking {
            delete(query(Sample::class).find())
        }

        assertEquals(0, realm.query(Sample::class).find().size)
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
}
