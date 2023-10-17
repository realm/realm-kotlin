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

import io.realm.kotlin.test.util.Utils
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.cValue
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.posix.S_IRGRP
import platform.posix.S_IROTH
import platform.posix.S_IRUSR
import platform.posix.nanosleep
import platform.posix.pthread_threadid_np
import platform.posix.timespec
import kotlin.native.internal.GC
import kotlin.time.Duration

actual object PlatformUtils {
    actual fun createTempDir(prefix: String, readOnly: Boolean): String {
        // Currently we cannot template using platform.posix.mkdtemp
        // the return value is not of use.
        val suffix = "-${Utils.createRandomString(6)}"
        val path = "${platform.Foundation.NSTemporaryDirectory()}$prefix$suffix"
        platform.posix.mkdir(path, 448.toUShort())

        if (readOnly) {
            platform.posix.chmod(path, (S_IRUSR or S_IRGRP or S_IROTH).toUShort())
        }
        return path
    }

    actual fun deleteTempDir(path: String) {
        platform.Foundation.NSFileManager.defaultManager.removeItemAtURL(platform.Foundation.NSURL(fileURLWithPath = path), null)
    }

    actual fun sleep(duration: Duration) {
        val nanoseconds = duration.inWholeNanoseconds
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
