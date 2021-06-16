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

package io.realm

import io.realm.internal.singleThreadDispatcher
import io.realm.util.NsQueueDispatcher
import io.realm.util.PlatformUtils
import io.realm.util.Utils.printlntid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFRunLoopGetCurrent
import platform.CoreFoundation.CFRunLoopRun
import platform.CoreFoundation.CFRunLoopStop
import platform.Foundation.NSNumber
import platform.darwin.DISPATCH_QUEUE_PRIORITY_BACKGROUND
import platform.darwin.dispatch_get_global_queue
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Various coroutine tests to track if basic dispatching, etc. works.
 *
 * FIXME Remove when we have an overview of the constraints.
 */
class CoroutineTests {

    // Fails on non native-mt as dispatching from background thread to main does change actual
    // thread, thus failing to assert main thread id
    @Test
    fun dispatchBetweenThreads() {
        val tid = PlatformUtils.threadId()

        printlntid("main")
        val worker = Worker.start()
        worker.execute(TransferMode.SAFE, { tid }) { tid ->
            printlntid("worker")
            runBlocking {
                printlntid("runblocking")
                val currentTid = PlatformUtils.threadId()
                val async: Deferred<Unit> = CoroutineScope(Dispatchers.Unconfined).async {
                    assertEquals(currentTid, PlatformUtils.threadId())
                    printlntid("async")
                }
                withContext(Dispatchers.Main) {
                    // This just runs on the worker thread for non native-mt
                    printlntid("main from background")
                    assertEquals(tid, PlatformUtils.threadId())
                    printlntid("exiting")
                    CFRunLoopStop(CFRunLoopGetCurrent())
                }
            }
        }
        CFRunLoopRun()
        printlntid("main exit")
    }

    // Both with and without native-mt:
    // - NSQueueDispatcher tries to access non-shared block and scheduled lambda
    // - Freezing block and lambda yields InvalidMutabilityException as block is tranformed into
    //   continuation that is supposed to be modified on both threads
    @Test
    @Ignore
    fun dispatchQueueScheduler() {
        val queue = dispatch_get_global_queue(NSNumber(DISPATCH_QUEUE_PRIORITY_BACKGROUND).integerValue, 0)
        val dispatcher = NsQueueDispatcher(queue)
        CoroutineScope(dispatcher).async {
            printlntid("async")
        }
        CFRunLoopRun()
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun currentDispatcher() {
        val dispatcher = singleThreadDispatcher("background")

        val tid = runBlocking(dispatcher) { PlatformUtils.threadId() }

        val currentDispatcher = runBlocking(dispatcher) {
            coroutineContext[CoroutineDispatcher.Key]
        }
        runBlocking(currentDispatcher!!) {
            assertEquals(tid, PlatformUtils.threadId())
        }
    }
}
