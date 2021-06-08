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

import android.os.Handler
import android.os.HandlerThread
import io.realm.log.RealmLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlin.coroutines.CoroutineContext

actual object PlatformHelper {

    // Returns the root directory of the platform's App data
    actual fun appFilesDirectory(): String = RealmInitializer.filesDir.absolutePath

    // Returns the default logger for the platform
    actual fun createDefaultSystemLogger(tag: String): RealmLogger = LogCatLogger(tag)
}

actual fun singleThreadDispatcher(id: String): CoroutineDispatcher {
    val thread = HandlerThread("RealmWriter[$id]")
    thread.start()
    return Handler(thread.looper).asCoroutineDispatcher()
}

// FIXME All of the below is common with Android. Should be align in separate source set but
//  that is already tracked by https://github.com/realm/realm-kotlin/issues/175

// Expose platform runBlocking through common interface
public actual fun <T> runBlocking(context: CoroutineContext, block: suspend CoroutineScope.() -> T): T {
    return kotlinx.coroutines.runBlocking(context, block)
}

private class JVMThreadLocal<T> constructor(val initializer: () -> T) : java.lang.ThreadLocal<T>() {
    override fun initialValue(): T? {
        return initializer()
    }
}

private val jvmTransactionMap =
    io.realm.internal.JVMThreadLocal<MutableMap<SuspendableWriter, Boolean>>({ mutableMapOf() })
actual var transactionMap: MutableMap<SuspendableWriter, Boolean>
    get() = jvmTransactionMap.get()!!
    set(value) {
        jvmTransactionMap.set(value)
    }
