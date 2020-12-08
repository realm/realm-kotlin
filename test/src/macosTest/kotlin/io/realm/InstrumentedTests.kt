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

// FIXME Do we actually want to expose this
import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmModelInternal
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toLong
import test.Sample
import kotlin.test.Test
import kotlin.test.assertEquals

class InstrumentedTests {

    // FIXME Local implementation of pointer wrapper to support test. Using the internal one would
    //  require the native wrapper to be api dependency from cinterop/library. Don't know if the
    //  test is needed at all at this level
    class CPointerWrapper(val ptr: CPointer<out CPointed>?) : NativePointer
    @Test
    fun testRealmModelInternalPropertiesGenerated() {
        val p = Sample()
        @Suppress("CAST_NEVER_SUCCEEDS")
        val realmModel: RealmModelInternal = p as? RealmModelInternal ?: error("Supertype RealmModelInternal was not added to Sample class")

        memScoped {
            val ptr1: COpaquePointerVar = alloc()
            val ptr2: COpaquePointerVar = alloc()

            // Accessing getters/setters
            realmModel.`$realm$IsManaged` = true
            realmModel.`$realm$ObjectPointer` = CPointerWrapper(ptr1.ptr)
            realmModel.`$realm$Pointer` = CPointerWrapper(ptr2.ptr)
            realmModel.`$realm$TableName` = "Sample"

            assertEquals(true, realmModel.`$realm$IsManaged`)
            assertEquals(ptr1.rawPtr.toLong(), (realmModel.`$realm$ObjectPointer` as CPointerWrapper).ptr.toLong())
            assertEquals(ptr2.rawPtr.toLong(), (realmModel.`$realm$Pointer` as CPointerWrapper).ptr.toLong())
            assertEquals("Sample", realmModel.`$realm$TableName`)
        }
    }
}
