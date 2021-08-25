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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm

import io.realm.test.platform.PlatformUtils.createTempDir
import io.realm.test.platform.PlatformUtils.deleteTempDir
import io.realm.test.platform.PlatformUtils.triggerGC
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.FILE
import platform.posix.NULL
import platform.posix.fgets
import platform.posix.pclose
import platform.posix.popen
import test.Sample
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class MemoryTests {

    lateinit var tmpDir: String

    @BeforeTest
    fun setup() {
        tmpDir = createTempDir()
    }

    @AfterTest
    fun tearDown() {
        deleteTempDir(tmpDir)
    }

    // TODO Only run on macOS, filter using https://developer.apple.com/documentation/foundation/nsprocessinfo/3608556-iosapponmac when upgrading to XCode 12
    @Test
    @Ignore // We currently do not clean up intermediate versions if the realm itself is garbage collected
    fun garbageCollectorShouldFreeNativeResources() {
        val referenceHolder = mutableListOf<Sample>()
        val amountOfMemoryMappedInProcessCMD =
            "vmmap  -summary ${platform.posix.getpid()}  2>/dev/null | awk '/mapped/ {print \$3}'";
        {
            val realm = openRealmFromTmpDir()
            // TODO use Realm.delete once this is implemented
            realm.writeBlocking {
                objects(Sample::class).delete()
            }

            // allocating a 1 MB string
            val oneMBstring = StringBuilder("").apply {
                for (i in 1..4096) {
                    // 128 length (256 bytes)
                    append("v7TPOZtm50q8kMBoKiKRaD2JhXgjM6OUNzHojXuFXvxdtwtN9fCVIW4njdwVdZ9aChvXCtW4nzUYeYWbI6wuSspbyjvACtMtjQTtOoe12ZEPZPII6PAFTfbrQQxc3ymJ")
                }
            }.toString()

            // inserting ~ 100MB of data
            val elements: List<Sample> =
                realm.writeBlocking {
                    IntRange(1, 100).map {
                        copyToRealm(Sample()).apply {
                            stringField = oneMBstring
                        }
                    }
                }
            referenceHolder.addAll(elements)
        }()
        assertEquals(
            "99.0M",
            runSystemCommand(amountOfMemoryMappedInProcessCMD),
            "We should have at least 99 MB allocated as mmap"
        )
        // After releasing all the 'realm_object_create' reference the Realm should be closed and the
        // no memory mapped file is allocated in the process
        referenceHolder.clear()
        triggerGC()

        platform.posix.sleep(1 * 5) // give chance to the Collector Thread to process references
        assertEquals(
            "",
            runSystemCommand(amountOfMemoryMappedInProcessCMD),
            "Freeing the references should close the Realm so no memory mapped allocation should be present"
        )
    }

    // TODO Only run on macOS, filter using https://developer.apple.com/documentation/foundation/nsprocessinfo/3608556-iosapponmac when upgrading to XCode 12
    @Test
    fun closeShouldFreeMemory() {
        val referenceHolder = mutableListOf<Sample>()
        val amountOfMemoryMappedInProcessCMD =
            "vmmap  -summary ${platform.posix.getpid()}  2>/dev/null | awk '/mapped/ {print \$3}'";
        {
            val realm = openRealmFromTmpDir()

            // allocating a 1 MB string
            val oneMBstring = StringBuilder("").apply {
                for (i in 1..4096) {
                    // 128 length (256 bytes)
                    append("v7TPOZtm50q8kMBoKiKRaD2JhXgjM6OUNzHojXuFXvxdtwtN9fCVIW4njdwVdZ9aChvXCtW4nzUYeYWbI6wuSspbyjvACtMtjQTtOoe12ZEPZPII6PAFTfbrQQxc3ymJ")
                }
            }.toString()

            // inserting ~ 100MB of data
            val elements: List<Sample> =
                realm.writeBlocking {
                    IntRange(1, 100).map {
                        copyToRealm(Sample()).apply {
                            stringField = oneMBstring
                        }
                    }
                }
            referenceHolder.addAll(elements)
            realm.close() // force closing will free the native memory even though we still have reference to realm_object open.
        }()

        triggerGC()
        platform.posix.sleep(1 * 5) // give chance to the Collector Thread to process out of scope references
        assertEquals(
            "",
            runSystemCommand(amountOfMemoryMappedInProcessCMD),
            "we should not have any mmap allocations"
        )
    }

    private fun openRealmFromTmpDir(): Realm {
        val configuration = RealmConfiguration.Builder(path = "$tmpDir/default.realm", schema = setOf(Sample::class)).build()
        return Realm.openBlocking(configuration)
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
