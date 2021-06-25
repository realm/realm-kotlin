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
package io.realm.internal

import io.realm.log.RealmLogger
import kotlin.native.concurrent.ensureNeverFrozen
import kotlin.native.concurrent.freeze
import kotlin.native.concurrent.isFrozen

actual object PlatformHelper {
    @Suppress("FunctionOnlyReturningConstant")
    actual fun appFilesDirectory(): String {
        return "."
    }

    actual fun createDefaultSystemLogger(tag: String): RealmLogger = NSLogLogger(tag)
}

actual fun <T> T.freeze(): T = this.freeze()

actual val <T> T.isFrozen: Boolean
    get() = this.isFrozen

actual fun Any.ensureNeverFrozen() = this.ensureNeverFrozen()
