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

import java.io.File
import java.nio.file.Files
import kotlin.io.path.absolutePathString

actual object PlatformUtils {
    actual fun createTempDir(prefix: String): String {
        return Files.createTempDirectory("$prefix-jvm_tests").absolutePathString()
    }

    actual fun deleteTempDir(path: String) {
        File(path).deleteRecursively()
    }
}
