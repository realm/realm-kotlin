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

package io.realm.kotlin.test

import android.os.Process
import android.text.format.Formatter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.platform.PlatformUtils.triggerGC
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.ext.query
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val oneMB = 1048576L
// Building a 1MB String
private val oneMBstring = StringBuilder("").apply {
    for (i in 1..4096) {
        // 128 length (256 bytes)
        append("v7TPOZtm50q8kMBoKiKRaD2JhXgjM6OUNzHojXuFXvxdtwtN9fCVIW4njdwVdZ9aChvXCtW4nzUYeYWbI6wuSspbyjvACtMtjQTtOoe12ZEPZPII6PAFTfbrQQxc3ymJ")
    }
}.toString()

class MemoryTest : RealmObject {
    var stringField: String = "Realm"
}
@RunWith(AndroidJUnit4::class)
class MemoryTests {

    lateinit var tmpDir: String

    @Before
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
    }

    @After
    fun tearDown() {
        PlatformUtils.deleteTempDir(tmpDir)
    }

    @Test
    fun garbageCollectorShouldFreeNativeResources() {
        val command = arrayListOf("/system/bin/sh", "-c", "cat /proc/${Process.myPid()}/maps | grep default.realm | awk '{print \$1}'")

        var realm: Realm? = openRealmFromTmpDir()

        var mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize < oneMB, "Opening a Realm should not cost more than 12KB")

        // inserting ~ 100MB of data and keep a strong reference to all allocated objects
        val referenceHolder = mutableListOf<MemoryTest>()
        realm!!.writeBlocking {
            for (i in 1..100) {
                copyToRealm(MemoryTest()).apply {
                    stringField = oneMBstring
                }.also { referenceHolder.add(it) }
            }
        }

        mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize >= 99 * oneMB && mappedMemorySize < 102 * oneMB, "Committing the 100 objects should result in memory mapping ~ 99 MB. Current amount is ${bytesToHumanReadable(mappedMemorySize)}")

        // Change callback are not automatically unregistered if the tokens are release, so for now
        // just do an internal explicit unregistration
        @Suppress("invisible_reference", "invisible_member")
        (realm as io.realm.kotlin.internal.RealmImpl).unregisterCallbacks()
        realm = null
        triggerGC()

        mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize >= 99 * oneMB && mappedMemorySize < 102 * oneMB, "Realm and its memory should still be allocated since we didn't release all the inserted objects yet. Current amount is ${bytesToHumanReadable(mappedMemorySize)}")

        referenceHolder.clear()
        triggerGC()

        mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize < oneMB, "Releasing references should close the Realm and free all the 99 MB allocated previously. Current amount is ${bytesToHumanReadable(mappedMemorySize)}")
    }

    // make sure that calling realm.close() will force close the Realm and release native memory
    @Test
    fun closeShouldFreeMemory() {
        val command = arrayListOf("/system/bin/sh", "-c", "cat /proc/${Process.myPid()}/maps | grep default.realm | awk '{print \$1}'")

        val realm = openRealmFromTmpDir()

        var mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize < oneMB, "Opening a Realm should not cost more than 12KB. Current amount is ${bytesToHumanReadable(mappedMemorySize)}")

        // inserting ~ 100MB of data and keep a strong reference to all allocated objects
        val referenceHolder = mutableListOf<MemoryTest>()
        realm.writeBlocking {
            for (i in 1..100) {
                copyToRealm(MemoryTest()).apply {
                    stringField = oneMBstring
                }.also { referenceHolder.add(it) }
            }
        }

        mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize >= 99 * oneMB && mappedMemorySize < 102 * oneMB, "Committing the 100 objects should result in memory mapping of ~ 99 MB. Current amount is ${bytesToHumanReadable(mappedMemorySize)}")

        realm.close() // force close
        triggerGC()

        mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize < oneMB, "Closing the Realm should free all the 99 MB allocated previously. Current amount is ${bytesToHumanReadable(mappedMemorySize)}")
    }

    // This test tries to trigger reclaiming of intermediate versions by holding on to an initial
    // version, performing various object creations and deletions and verify that the resulting
    // realm is not holding on to all these intermediate version when references to the objects has
    // been garbage collected.
    // NOTE There is no guarantee that all versions are freed up, so there is a small chance that
    // we cannot assert that the final size is not smaller that if all versions are stilled alive,
    // but this is the best we can do and is better than nothing until proven flaky.
    @Test
    fun releaseIntermediateVersions() {
        val command = arrayListOf("/system/bin/sh", "-c", "cat /proc/${Process.myPid()}/maps | grep default.realm | awk '{print \$1}'")

        var realm: Realm = openRealmFromTmpDir()

        // Reference to a frozen object from the initial version
        val initialVersion = realm.writeBlocking {
            copyToRealm(MemoryTest().apply { stringField = "INITIAL" })
        }

        var mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize < oneMB, "Opening a Realm should not cost more than 12KB")

        // Perform various writes and deletes and garbage collect the references to allow core to
        // release the underlying versions
        for (i in 1..10) {
            val referenceHolder = mutableListOf<MemoryTest>()
            realm.writeBlocking {
                for (i in 1..10) {
                    copyToRealm(MemoryTest()).apply {
                        stringField = oneMBstring
                    }.also { referenceHolder.add(it) }
                }
            }
            realm.writeBlocking {
                delete(query<MemoryTest>("stringField != 'INITIAL'"))
            }
            assertEquals(1, realm.query<MemoryTest>().find().size)
            referenceHolder.clear()
            triggerGC()
        }

        // Verify that the realm is smaller than the full size of all intermediate versions.
        mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize < 99 * oneMB, "Intermediate versions doesn't seem to be reclaimed. Reclaiming is not guaranteed by core, but should most likely happen, so take errors with a grain of salt. Current allocation is ${bytesToHumanReadable(mappedMemorySize)}")
    }

    private fun numberOfMemoryMappedBytes(cmd: ArrayList<String>): Long {
        return runCommand(cmd).run { memorySizeFromMemorySegments(this) }
    }

    private fun runCommand(command: ArrayList<String>): ArrayList<String> {
        val result = arrayListOf<String>()

        val process = Runtime.getRuntime().exec(command.toTypedArray())

        val input = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while (input.readLine().also { line = it } != null) result.add(line!!)

        process.waitFor()
        input.close()

        return result
    }

    // we process list of memory segments like 7f3c62e00000-7f3c66e00000 to calculate how many bytes are used for each segment
    private fun memorySizeFromMemorySegments(segments: ArrayList<String>): Long {
        var numberOfBytes = 0L
        for (segment: String in segments) {
            segment.split('-').also { numberOfBytes += (it[1].toLong(16) - it[0].toLong(16)) }
        }
        return numberOfBytes
    }

    private fun bytesToHumanReadable(mappedMemorySize: Long): String {
        return Formatter.formatFileSize(InstrumentationRegistry.getInstrumentation().targetContext, mappedMemorySize)
    }

    private fun openRealmFromTmpDir(): Realm {
        val configuration =
            RealmConfiguration.Builder(schema = setOf(MemoryTest::class))
                .directory(tmpDir)
                .build()
        return Realm.open(configuration)
    }
}
