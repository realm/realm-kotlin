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

@file:OptIn(ExperimentalStdlibApi::class)
package io.realm

// FIXME API-CLEANUP Do we actually want to expose this. Test should probably just be reeavluated
//  or moved.
import io.realm.Utils.createTempDir
import io.realm.Utils.deleteTempDir
import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmModelInternal
import io.realm.runtimeapi.RealmModule
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import kotlinx.cinterop.toLong
import platform.posix.FILE
import platform.posix.NULL
import platform.posix.fgets
import platform.posix.pclose
import platform.posix.popen
import test.Sample
import kotlin.native.internal.GC
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InstrumentedTests {

    @RealmModule(Sample::class)
    class MySchema

    lateinit var tmpDir: String

    @BeforeTest
    fun setup() {
        tmpDir = createTempDir()
    }

    @AfterTest
    fun tearDown() {
        deleteTempDir(tmpDir)
    }

    // FIXME API-CLEANUP Do we actually want to expose this. Test should probably just be reeavluated
    //  or moved. Local implementation of pointer wrapper to support test. Using the internal one would
    //  require the native wrapper to be api dependency from cinterop/library. Don't know if the
    //  test is needed at all at this level
    class CPointerWrapper(val ptr: CPointer<out CPointed>?, managed: Boolean = true) : NativePointer
    @Test
    fun testRealmModelInternalPropertiesGenerated() {
        val p = Sample()

        @Suppress("CAST_NEVER_SUCCEEDS")
        val realmModel: RealmModelInternal = p as? RealmModelInternal
            ?: error("Supertype RealmModelInternal was not added to Sample class")

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

    // TODO try to understand why the cleaner is invoked while we still have reference to the object
    // just after the object is created it becomes elligble for GC which closes it, hence trying to set a string will throw
    // VMMAP can not be invoked from iOS so run this test only macos
    // TODO filter using https://developer.apple.com/documentation/foundation/nsprocessinfo/3608556-iosapponmac when upgrading to XCode 12
    @Test
    fun garbageCollectorShouldFreeNativeResources() {
        val referenceHolder = mutableListOf<Sample>()
        val amountOfMemoryMappedInProcessCMD = "vmmap  -summary ${platform.posix.getpid()}  2>/dev/null | awk '/mapped/ {print \$3}'";
        {
            val realm = openRealmFromTmpDir()
            // TODO use Realm.delete once this is implemented
            realm.beginTransaction()
            realm.objects(Sample::class).delete()
            realm.commitTransaction()

            // allocating a 1 MB string
            val oneMBstring = StringBuilder("").apply {
                for (i in 1..4096) {
                    // 128 length (256 bytes)
                    append("v7TPOZtm50q8kMBoKiKRaD2JhXgjM6OUNzHojXuFXvxdtwtN9fCVIW4njdwVdZ9aChvXCtW4nzUYeYWbI6wuSspbyjvACtMtjQTtOoe12ZEPZPII6PAFTfbrQQxc3ymJ")
                }
            }.toString()

            realm.beginTransaction()
            // inserting ~ 100MB of data
            for (i in 1..100) {
                realm.create(Sample::class).apply {
                    stringField = oneMBstring
                }.also { referenceHolder.add(it) }
            }
            realm.commitTransaction()
        }()
        assertEquals("99.0M", runSystemCommand(amountOfMemoryMappedInProcessCMD), "We should have at least 99 MB allocated as mmap")
        // After releasing all the 'realm_object_create' reference the Realm should be closed and the
        // no memory mapped file is allocated in the process
        referenceHolder.clear()
        GC.collect()
        platform.posix.sleep(1 * 5) // give chance to the Collector Thread to process references
        assertEquals("", runSystemCommand(amountOfMemoryMappedInProcessCMD), "Freeing the references should close the Realm so no memory mapped allocation should be present")
    }

    @Test
    fun closeShouldFreeMemory() {
        val referenceHolder = mutableListOf<Sample>()
        val amountOfMemoryMappedInProcessCMD = "vmmap  -summary ${platform.posix.getpid()}  2>/dev/null | awk '/mapped/ {print \$3}'";
        {
            val realm = openRealmFromTmpDir()

            // allocating a 1 MB string
            val oneMBstring = StringBuilder("").apply {
                for (i in 1..4096) {
                    // 128 length (256 bytes)
                    append("v7TPOZtm50q8kMBoKiKRaD2JhXgjM6OUNzHojXuFXvxdtwtN9fCVIW4njdwVdZ9aChvXCtW4nzUYeYWbI6wuSspbyjvACtMtjQTtOoe12ZEPZPII6PAFTfbrQQxc3ymJ")
                }
            }.toString()

            realm.beginTransaction()
            // inserting ~ 100MB of data
            for (i in 1..100) {
                realm.create(Sample::class).apply {
                    stringField = oneMBstring
                }.also { referenceHolder.add(it) }
            }
            realm.commitTransaction()
            realm.close() // force closing will free the native memory even though we still have reference to realm_object open.
        }()

        GC.collect()
        platform.posix.sleep(1 * 5) // give chance to the Collector Thread to process out of scope references
        assertEquals("", runSystemCommand(amountOfMemoryMappedInProcessCMD), "we should not have any mmap allocations")
    }

    private fun openRealmFromTmpDir(): Realm {
        val configuration = RealmConfiguration.Builder(schema = MySchema(), path = "$tmpDir/default.realm").build()
        return Realm.open(configuration)
    }

    private fun runSystemCommand(cmd: String): String {
        val pipe: CPointer<FILE> = requireNotNull(popen(cmd, "r"))
        val result = StringBuilder("")
        try {
            val buffer = ByteArray(4096)
            while (true) {
                val scan = fgets(buffer.refTo(0), buffer.size, pipe)
                if (scan != null && scan != NULL) {
                    result.append(scan.toKString())
                } else {
                    break
                }
            }
        } finally {
            pclose(pipe)
        }
        return result.toString().trim()
    }
}
