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

package io.realm.test.platform

import android.annotation.SuppressLint
import android.os.SystemClock
import java.io.File
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

actual object PlatformUtils {
    @SuppressLint("NewApi")
    @ExperimentalPathApi
    actual fun createTempDir(): String {
        return Files.createTempDirectory("android_tests").absolutePathString()
    }

    actual fun deleteTempDir(path: String) {
        File(path).deleteRecursively()
    }

    @OptIn(ExperimentalTime::class)
    actual fun sleep(duration: Duration) {
        Thread.sleep(duration.toLongMilliseconds())
    }

    actual fun threadId(): ULong = Thread.currentThread().id.toULong()

    // Empiric approach to trigger GC
    @Suppress("ExplicitGarbageCollectionCall")
    actual fun triggerGC() {
        for (i in 1..30) {
            allocGarbage(0)
            SystemClock.sleep(100)
            System.gc()
            System.runFinalization()
        }
        SystemClock.sleep(5000) // 5 seconds to give the GC some time to process
    }
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
