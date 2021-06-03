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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlin.native.concurrent.ThreadLocal

// Expose platform runBlocking through common interface
public actual fun <T> runBlocking(context: CoroutineContext, block: suspend CoroutineScope.() -> T): T {
    return kotlinx.coroutines.runBlocking(context, block)
}

/**
 * The default dispatcher for Darwin platforms is backed by a run loop on the calling thread.
 */
actual fun defaultWriteDispatcher(id: String): CoroutineDispatcher {
    // TODO Propagate id to the underlying thread ... if it makes sense when we use the default
    //  runloop on the current thread!?
    // This triggers setting up a run loop, which is a requirement for the default dispatcher below
    runBlocking {}
    return Dispatchers.Default
}

@ThreadLocal
actual var transactionMap: MutableMap<SuspendableWriter, Boolean> = HashMap()
