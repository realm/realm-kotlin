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

package io.realm.kotlin.test.platform

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.util.stream.Collectors
import kotlin.io.path.absolutePathString
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

actual object PlatformUtils {
    actual fun createTempDir(prefix: String, readOnly: Boolean): String {
        val dir: Path = Files.createTempDirectory("$prefix-jvm_tests")
        if (readOnly) {
            Files.setPosixFilePermissions(
                dir,
                setOf(
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OWNER_READ
                )
            )
        }
        return dir.absolutePathString()
    }

    actual fun deleteTempDir(path: String) {
        val rootPath: Path = Paths.get(path)
        val pathsToDelete: List<Path> =
            Files.walk(rootPath).sorted(Comparator.reverseOrder()).collect(Collectors.toList())
        for (p in pathsToDelete) {
            // Sometimes (on Windows) we need the give a GC a chance to run and close all native pointers
            // before we can delete the Realm, otherwise delete will fail with " The process cannot access the
            // file because it is being used by another process".
            //
            // We try to trigger the GC once then retry the delete.
            var counter = 5
            var deleted = false
            var error: java.nio.file.FileSystemException? = null
            while (!deleted && counter > 0) {
                try {
                    Files.deleteIfExists(p)
                    deleted = true
                } catch (e: java.nio.file.FileSystemException) {
                    error = e
                    triggerGC()
                    sleep(1.seconds)
                    counter -= 1
                }
            }
            if (!deleted) {
                throw error!!
            }
        }
    }

    actual fun sleep(duration: Duration) {
        Thread.sleep(duration.inWholeMilliseconds)
    }

    actual fun threadId(): ULong = Thread.currentThread().id.toULong()

    @Suppress("ExplicitGarbageCollectionCall")
    actual fun triggerGC() {
        for (i in 1..30) {
            allocGarbage(0)
            Thread.sleep(100)
            System.gc()
            @Suppress("deprecation")
            System.runFinalization()
        }
        Thread.sleep(5000) // 5 seconds to give the GC some time to process
    }

    // Allocs as much garbage as we can. Pass maxSize = 0 to use all available memory in the process.
    private fun allocGarbage(garbageSize: Int): ByteArray {
        var garbageSize = garbageSize
        if (garbageSize == 0) {
            val maxMemory = Runtime.getRuntime().maxMemory()
            val totalMemory = Runtime.getRuntime().totalMemory()
            garbageSize = (maxMemory - totalMemory).toInt() / 10 * 9
        }
        var garbage = ByteArray(0)
        try {
            if (garbageSize > 0) {
                garbage = ByteArray(garbageSize)
                garbage[0] = 1
                garbage[garbage.size - 1] = 1
            }
        } catch (oom: OutOfMemoryError) {
            return allocGarbage(garbageSize / 10 * 9)
        }
        return garbage
    }
}
