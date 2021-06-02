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

package io.realm.util

import kotlinx.cinterop.cValue
import kotlinx.cinterop.cstr
import kotlinx.cinterop.toKString
import platform.posix.nanosleep
import platform.posix.timespec
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

actual object PlatformUtils {
    actual fun createTempDir(): String {
        val mask = "${platform.Foundation.NSTemporaryDirectory()}realm_test_.XXXXXX"
        return platform.posix.mkdtemp(mask.cstr)!!.toKString()
    }

    actual fun deleteTempDir(path: String) {
        platform.Foundation.NSFileManager.defaultManager.removeItemAtURL(platform.Foundation.NSURL(fileURLWithPath = path), null)
    }

    @OptIn(ExperimentalTime::class)
    actual fun sleep(duration: Duration) {
        val nanoseconds = duration.toLongNanoseconds()
        val time = cValue<timespec> {
            tv_sec = nanoseconds/1000000000
            tv_nsec = nanoseconds%1000000000
        }
        nanosleep(time, null)
    }
}
