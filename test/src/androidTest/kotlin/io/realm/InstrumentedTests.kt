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

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.realm.internal.RealmInitializer
import io.realm.runtimeapi.RealmModelInternal
import io.realm.runtimeapi.RealmModule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import test.Sample
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import java.lang.IllegalStateException
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class InstrumentedTests {

    @RealmModule(Sample::class)
    class MySchema

    lateinit var realm : Realm

    @Before
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

        realm.beginTransaction()
        val sample = realm.create(Sample::class)
        kotlin.test.assertEquals("", sample.name)
        sample.name = s
        kotlin.test.assertEquals(s, sample.name)
        realm.commitTransaction()

    }

    @Test
    fun query() {
        val s = "Hello, World!"

        realm.beginTransaction()
        realm.create(Sample::class).run { name = s }
        realm.create(Sample::class).run { name = "Hello, Realm!" }
        realm.commitTransaction()

        val objects1: RealmResults<Sample> = realm.objects(Sample::class)
        assertEquals(2, objects1.size)

        val objects2: RealmResults<Sample> =
            realm.objects(Sample::class).query("name == $0", s)
        assertEquals(1, objects2.size)
        for (sample in objects2) {
            assertEquals(s, sample.name)
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
        realm.create(Sample::class).run { name = "Hello, World!" }
        realm.create(Sample::class).run { name = "Hello, Realm!" }
        realm.commitTransaction()

        val objects1: RealmResults<Sample> = realm.objects(Sample::class)
        assertEquals(2, objects1.size)

        realm.beginTransaction()
        realm.objects(Sample::class).delete()
        realm.commitTransaction()

        assertEquals(0, realm.objects(Sample::class).size)
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

    // FIXME API-CLEANUP Local implementation of pointer wrapper to support test. Using the internal
    //  one would require jni-swig-stub to be api dependency from cinterop/library. Don't know if
    //  the test is needed at all at this level
    //  https://github.com/realm/realm-kotlin/issues/56
    class LongPointerWrapper(val ptr: Long) : io.realm.runtimeapi.NativePointer
    @Test
    fun testRealmModelInternalPropertiesGenerated() {
        val p = Sample()
        val realmModel: RealmModelInternal = p as? RealmModelInternal ?: error("Supertype RealmModelInternal was not added to Sample class")

        // Accessing getters/setters
        realmModel.`$realm$IsManaged` = true
        realmModel.`$realm$ObjectPointer` = LongPointerWrapper(0xCAFEBABE)
        realmModel.`$realm$Pointer` = LongPointerWrapper(0XCAFED00D)
        realmModel.`$realm$TableName` = "Sample"

        assertEquals(true, realmModel.`$realm$IsManaged`)
        assertEquals(0xCAFEBABE, (realmModel.`$realm$ObjectPointer` as LongPointerWrapper).ptr)
        assertEquals(0XCAFED00D, (realmModel.`$realm$Pointer` as LongPointerWrapper).ptr)
        assertEquals("Sample", realmModel.`$realm$TableName`)
    }
}
