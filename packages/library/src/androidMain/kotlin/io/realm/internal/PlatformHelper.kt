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
import java.lang.ThreadLocal

actual object PlatformHelper {

    // Returns the root directory of the platform's App data
    actual fun appFilesDirectory(): String = RealmInitializer.filesDir.absolutePath

    // Returns the default logger for the platform
    actual fun createDefaultSystemLogger(tag: String): RealmLogger = LogCatLogger(tag)
}

class ThreadLocal<T> constructor(initialValue: T) {
    // FIXME withInitial only available on API 26
    private var _value: ThreadLocal<T> = ThreadLocal.withInitial { initialValue }
    var value: T
        get() { return _value.get() as T }
        set(value) { _value.set(value) }
}

actual val transactionMap: MutableMap<SuspendableWriter, Boolean> =
    ThreadLocal<MutableMap<SuspendableWriter, Boolean>>(mutableMapOf()).value
