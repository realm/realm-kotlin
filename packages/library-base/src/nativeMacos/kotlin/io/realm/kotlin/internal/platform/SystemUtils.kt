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
package io.realm.kotlin.internal.platform

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSProcessInfo
import platform.posix.uname
import platform.posix.utsname

public actual val OS_NAME: String = "MacOS"
// These causes memory mapping rendering MemoryTests to fail, so only initialize them if actually needed
public actual val OS_VERSION: String by lazy { NSProcessInfo.Companion.processInfo().operatingSystemVersionString }
public actual val DEVICE_MANUFACTURER: String = ""
public actual val DEVICE_MODEL: String = ""
public actual val CPU_ARCH: String by lazy {
    try {
        memScoped {
            val systemInfo = alloc<utsname>()
            uname(systemInfo.ptr)
            systemInfo.machine.toKString()
        }
    } catch (e: Exception) {
        "Unknown"
    }
}

@Suppress("FunctionOnlyReturningConstant")
public actual fun appFilesDirectory(): String {
    return platform.Foundation.NSFileManager.defaultManager.currentDirectoryPath
}
