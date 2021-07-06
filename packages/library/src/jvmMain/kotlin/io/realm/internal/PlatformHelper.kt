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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

actual object PlatformHelper {
    @Suppress("FunctionOnlyReturningConstant")
    actual fun appFilesDirectory(): String {
        // FIXME What is the standard default location for non-Android JVM builds.
        //  https://github.com/realm/realm-kotlin/issues/75
        return "."
    }

    actual fun createDefaultSystemLogger(tag: String): RealmLogger = StdOutLogger(tag)
}

public actual fun singleThreadDispatcher(id: String): CoroutineDispatcher {
    // TODO Propagate id to the underlying thread
    return Executors.newSingleThreadExecutor().asCoroutineDispatcher()
}

// FIXME All of the below is common with Android. Should be align in separate source set but
//  that is already tracked by https://github.com/realm/realm-kotlin/issues/175

// Expose platform runBlocking through common interface
public actual fun <T> runBlocking(context: CoroutineContext, block: suspend CoroutineScope.() -> T): T {
    return kotlinx.coroutines.runBlocking(context, block)
}

actual fun threadId(): ULong {
    return Thread.currentThread().id.toULong()
}

actual fun <T> T.freeze(): T = this

actual val <T> T.isFrozen: Boolean
    get() = false

actual fun Any.ensureNeverFrozen() {}

actual typealias WeakReference<T> = java.lang.ref.WeakReference<T>
