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

import okio.Path.Companion.toPath
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.io.path.absolutePathString
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

actual object PlatformUtils {
    actual fun createTempDir(prefix: String): String {
        return Files.createTempDirectory("$prefix-jvm_tests").absolutePathString()
    }

    actual fun deleteTempDir(path: String) {
        val rootPath: Path = Paths.get(path)
        val pathsToDelete: List<Path> =
            Files.walk(rootPath).sorted(Comparator.reverseOrder()).collect(Collectors.toList())
        for (p in pathsToDelete) {
            print("£££££££££££££££££££££££££ Trying to delete: $p ")
            println(" succeeded?  ${Files.deleteIfExists(p)}")
        }

//        if (!File(path).verboseDeleteRecursively()) {
//            throw IllegalStateException("Failed to delete: $path")
//        }
    }


    private fun File.verboseDeleteRecursively(): Boolean = walkBottomUp().fold(true) { res, it ->
        var deleted = it.delete()
        if (!deleted) {
            println("£££££££££££££££££££££££££ COULD NOT DELETE: ${it.path} exists: ${it.exists()}")
            // wait a bit then retry on Windows
            for (i in 1..60) {
                println("£££££££££££££££££££££££££ COULD NOT DELETE: ${it.path} attempt $i")
                Thread.sleep(500)
                System.gc()
                deleted = Files.deleteIfExists(it.toPath())
                if (deleted)
                    break;
            }
        }
        (deleted || !it.exists()) && res
    }

    @OptIn(ExperimentalTime::class)
    actual fun sleep(duration: Duration) {
        Thread.sleep(duration.toLongMilliseconds())
    }

    actual fun threadId(): ULong = Thread.currentThread().id.toULong()

    @Suppress("ExplicitGarbageCollectionCall")
    actual fun triggerGC() {
        for (i in 1..30) {
            allocGarbage(0)
            Thread.sleep(100)
            System.gc()
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
