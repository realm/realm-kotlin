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

package io.realm.kotlin.test

import io.realm.kotlin.internal.platform.singleThreadDispatcher
import io.realm.kotlin.test.platform.NsQueueDispatcher
import io.realm.kotlin.test.platform.PlatformUtils
import io.realm.kotlin.test.util.Utils.printlntid
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import platform.CoreFoundation.CFRunLoopRun
import platform.Foundation.NSNumber
import platform.darwin.DISPATCH_QUEUE_PRIORITY_BACKGROUND
import platform.darwin.dispatch_get_global_queue
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Various coroutine tests to track if basic dispatching, etc. works.
 *
 * FIXME Remove when we have an overview of the constraints.
 */
class CoroutineTests {

    @Test
    fun dispatchBetweenThreads() = runTest {
        val dispatcher1 = newSingleThreadContext("test-dispatcher-1")
        val dispatcher2 = newSingleThreadContext("test-disptacher-2")
        val tid2 = runBlocking(dispatcher2) {
            PlatformUtils.threadId()
        }
        runBlocking(dispatcher1) {
            val currentTid = PlatformUtils.threadId()
            CoroutineScope(Dispatchers.Unconfined).async {
                assertEquals(currentTid, PlatformUtils.threadId())
            }.await()
            runBlocking(dispatcher2) {
                assertEquals(tid2, PlatformUtils.threadId())
            }
        }
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
