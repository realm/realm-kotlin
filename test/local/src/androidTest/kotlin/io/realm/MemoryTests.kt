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

import android.os.Process
import android.text.format.Formatter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.realm.test.platform.PlatformUtils
import io.realm.test.platform.PlatformUtils.triggerGC
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import test.Sample
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.io.path.ExperimentalPathApi
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MemoryTests {

    lateinit var tmpDir: String

    @ExperimentalPathApi
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

        // Building a 1MB String
        val oneMBstring = StringBuilder("").apply {
            for (i in 1..4096) {
                // 128 length (256 bytes)
                append("v7TPOZtm50q8kMBoKiKRaD2JhXgjM6OUNzHojXuFXvxdtwtN9fCVIW4njdwVdZ9aChvXCtW4nzUYeYWbI6wuSspbyjvACtMtjQTtOoe12ZEPZPII6PAFTfbrQQxc3ymJ")
            }
        }.toString()
        val oneMB = 1048576L

        var mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize < oneMB, "Opening a Realm should not cost more than 12KB")

        // inserting ~ 100MB of data and keep a strong reference to all allocated objects
        val referenceHolder = mutableListOf<Sample>()
        realm!!.writeBlocking {
            for (i in 1..100) {
                copyToRealm(Sample()).apply {
                    stringField = oneMBstring
                }.also { referenceHolder.add(it) }
            }
        }

        mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize >= 99 * oneMB && mappedMemorySize < 102 * oneMB, "Committing the 100 objects should result in memory mapping ~ 99 MB. Current amount is ${bytesToHumanReadable(mappedMemorySize)}")

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

        // Building a 1MB String
        val oneMBstring = StringBuilder("").apply {
            for (i in 1..4096) {
                // 128 length (256 bytes)
                append("v7TPOZtm50q8kMBoKiKRaD2JhXgjM6OUNzHojXuFXvxdtwtN9fCVIW4njdwVdZ9aChvXCtW4nzUYeYWbI6wuSspbyjvACtMtjQTtOoe12ZEPZPII6PAFTfbrQQxc3ymJ")
            }
        }.toString()
        val oneMB = 1048576L

        var mappedMemorySize = numberOfMemoryMappedBytes(command)
        assertTrue(mappedMemorySize < oneMB, "Opening a Realm should not cost more than 12KB. Current amount is ${bytesToHumanReadable(mappedMemorySize)}")

        // inserting ~ 100MB of data and keep a strong reference to all allocated objects
        val referenceHolder = mutableListOf<Sample>()
        realm.writeBlocking {
            for (i in 1..100) {
                copyToRealm(Sample()).apply {
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
        val configuration = RealmConfiguration(path = "$tmpDir/default.realm", schema = setOf(Sample::class))
        return Realm(configuration)
    }
}
