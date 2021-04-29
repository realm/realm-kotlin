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

package io.realm.util

import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.async
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.CoroutineContext

actual class RunLoopThread : CoroutineScope {

    private val exit = CountDownLatch(1)

    private val thread = HandlerThread("test-thread")
    private val handler: Handler by lazy { thread.start(); Handler(thread.looper) }

    override val coroutineContext: CoroutineContext by lazy { handler.asCoroutineDispatcher() + exceptionHandler }

    private var error: Throwable? = null

    val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        error = exception
        terminate()
    }

    actual fun run(block: RunLoopThread.() -> Unit) {
        this.async { block(this@RunLoopThread) }
        exit.await()
        error?.let { throw it }
    }

    actual fun terminate() {
        exit.countDown()
    }
}
