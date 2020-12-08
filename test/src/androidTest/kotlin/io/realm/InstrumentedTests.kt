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
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import test.Sample
import java.lang.IllegalStateException
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class InstrumentedTests {

    @RealmModule(Sample::class)
    class MySchema

    // Smoke test of compiling with library
    @Test
    fun contextIsNotNull() {
        assertNotNull(RealmInitializer.filesDir)
    }

    // This could be a common test, but included here for convenience as there is no other easy
    // way to trigger individual common test on Android
    // https://youtrack.jetbrains.com/issue/KT-34535
    @Test
    fun realmConfig() {
        val configuration = RealmConfiguration.Builder(schema = MySchema()).build()
        val realm = Realm.open(configuration)

        realm.beginTransaction()
        val sample = realm.create(Sample::class)
        kotlin.test.assertEquals("", sample.name)
        sample.name = "Hello, World!"
        kotlin.test.assertEquals("Hello, World!", sample.name)
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
