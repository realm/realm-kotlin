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

import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.posix.pthread_threadid_np

actual object RuntimeUtils {
    actual fun threadId(): ULong {
        memScoped {
            val tidVar = alloc<ULongVar>()
            pthread_threadid_np(null, tidVar.ptr) //.ensureUnixCallResult("pthread_threadid_np")
            return tidVar.value
        }
    }

    actual fun printlntid(s: String)
    {
        println("<" + RuntimeUtils.threadId() + "> $s")
    }
}
