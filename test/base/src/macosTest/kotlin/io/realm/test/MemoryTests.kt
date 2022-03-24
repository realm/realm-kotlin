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

package io.realm.test

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.entities.Sample
import io.realm.query
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
import kotlin.math.roundToInt
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

// New memory model doesn't seem to fix our memory leak, or maybe we have to reevaluate how we do
// the tests.
class MemoryTests {

    lateinit var tmpDir: String

    private val amountOfMemoryMappedInProcessCMD =
        "vmmap  -summary ${platform.posix.getpid()}  2>/dev/null | awk '/mapped/ {print \$3}'"

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
    @Ignore // Investigate https://github.com/realm/realm-kotlin/issues/327
    fun garbageCollectorShouldFreeNativeResources() {
        @OptIn(ExperimentalStdlibApi::class)
        println("NEW_MEMORY_MODEL: " + isExperimentalMM())

        val referenceHolder = mutableListOf<Sample>();
        {
            val realm = openRealmFromTmpDir()
            // TODO use Realm.delete once this is implemented
            realm.writeBlocking {
                delete(query<Sample>())
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

        // We should find a way to just meassure the increase over these tests. Referencing
        //   NSProcessInfo.Companion.processInfo().operatingSystemVersionString
        // as done in Darwin SystemUtils.kt can also cause allocations. Thus, just lazy evaluating
        // those system constants for now to avoid affecting the tests.
        assertEquals(
            "",
            runSystemCommand(amountOfMemoryMappedInProcessCMD),
            "Freeing the references should close the Realm so no memory mapped allocation should be present"
        )
    }

    // TODO Only run on macOS, filter using https://developer.apple.com/documentation/foundation/nsprocessinfo/3608556-iosapponmac when upgrading to XCode 12
    @Test
    fun closeShouldFreeMemory() {
        @OptIn(ExperimentalStdlibApi::class)
        println("NEW_MEMORY_MODEL: " + isExperimentalMM())

        val initialAllocation = parseSizeString(runSystemCommand(amountOfMemoryMappedInProcessCMD))

        val referenceHolder = mutableListOf<Sample>();
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

        // Referencing things like
        //   NSProcessInfo.Companion.processInfo().operatingSystemVersionString
        //   platform.Foundation.NSFileManager.defaultManager
        // as done in Darwin SystemUtils.kt cause allocations so we just assert the increase over
        // the test
        val allocation = parseSizeString(runSystemCommand(amountOfMemoryMappedInProcessCMD))
        assertEquals(initialAllocation, allocation, "mmap allocation exceeds expectations: initial=$initialAllocation current=$allocation")
    }

    @Test
    fun test_parseSizeString() {
        assertEquals(0, parseSizeString(""))
        assertEquals(1024, parseSizeString("1K"))
        assertEquals(5632, parseSizeString("5.5K"))
        assertEquals(1024 * 1024, parseSizeString("1M"))
        assertEquals(5767168, parseSizeString("5.5M"))
    }

    private fun parseSizeString(usage: String): Int {
        if (usage.isBlank()) {
            return 0
        }
        try {
            val i = usage.length - 1
            val size = usage.substring(0..i - 1).toFloat()
            val unit = usage.substring(i..i)
            val multiplier = when (unit) {
                "K" -> 1024
                "M" -> 1024 * 1024
                else -> error("Unknown memory unit: '$unit'")
            }
            return (size * multiplier).roundToInt()
        } catch (e: Exception) {
            error("Failed to parse size string: '$usage")
        }
    }

    private fun openRealmFromTmpDir(): Realm {
        val configuration = RealmConfiguration.Builder(schema = setOf(Sample::class))
            .directory(tmpDir)
            .build()
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
