/*
 * Copyright 2020 Realm Inc.
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

package io.realm

import platform.Foundation.NSFileManager
import platform.Foundation.temporaryDirectory

actual object PlatformHelper {

    actual fun appFilesDirectory(): String {
        // FIXME What is the standard default location for realms on Darwin...and does this have to
        //  be differentiated for macos vs. iOS
        //  https://github.com/realm/realm-kotlin/issues/75
        val currentDirectoryPath: String? = NSFileManager.defaultManager.temporaryDirectory().path
        return currentDirectoryPath!!
    }
}
