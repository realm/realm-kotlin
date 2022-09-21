/*
 * Copyright 2022 Realm Inc.
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

package io.realm.test.multiplatform.util.platform

import kotlinx.cinterop.cstr

actual object PlatformUtils {
    actual fun createTempDir(prefix: String): String {
        // X is a special char which will be replace by mkdtemp template
        val mask = prefix.replace('X', 'Z', ignoreCase = true)
        val path = "${platform.Foundation.NSTemporaryDirectory()}$mask-native_tests"
        platform.posix.mkdtemp(path.cstr)
        return path
    }

    actual fun deleteTempDir(path: String) {
        platform.Foundation.NSFileManager.defaultManager.removeItemAtURL(
            platform.Foundation.NSURL(fileURLWithPath = path),
            null
        )
    }
}
