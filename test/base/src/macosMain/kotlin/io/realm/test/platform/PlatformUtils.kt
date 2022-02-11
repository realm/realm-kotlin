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

import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValue
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.posix.nanosleep
import platform.posix.pthread_threadid_np
import platform.posix.timespec
import kotlin.native.internal.GC
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

actual object PlatformUtils {
    actual fun createTempDir(prefix: String): String {
        // X is a special char which will be replace by mkdtemp template
        val mask = prefix.replace('X', 'Z', ignoreCase = true)
        val path = "${platform.Foundation.NSTemporaryDirectory()}$mask"
        platform.posix.mkdtemp(path.cstr)
        return path
    }

    actual fun deleteTempDir(path: String) {
        platform.Foundation.NSFileManager.defaultManager.removeItemAtURL(platform.Foundation.NSURL(fileURLWithPath = path), null)
    }

    @OptIn(ExperimentalTime::class)
    actual fun sleep(duration: Duration) {
        val nanoseconds = duration.toLongNanoseconds()
        val time = cValue<timespec> {
            tv_sec = nanoseconds / 1000000000
            tv_nsec = nanoseconds % 1000000000
        }
        nanosleep(time, null)
    }
    actual fun threadId(): ULong {
        memScoped {
            val tidVar = alloc<ULongVar>()
            pthread_threadid_np(null, tidVar.ptr)
            return tidVar.value
        }
    }

    actual fun triggerGC() {
        GC.collect()
    }
}
