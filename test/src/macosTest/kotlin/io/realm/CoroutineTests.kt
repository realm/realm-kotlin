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

import io.realm.util.PlatformUtils
import io.realm.util.Utils.printlntid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFRunLoopGetCurrent
import platform.CoreFoundation.CFRunLoopRun
import platform.CoreFoundation.CFRunLoopStop
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals

class CoroutineTests {

    @Test
    fun dispatchBetweenThreads() {
        val tid = PlatformUtils.threadId()

        printlntid("main")
        val worker = Worker.start()
        worker.execute(TransferMode.SAFE, { tid }) { tid ->
            printlntid("worker")
            runBlocking {
                printlntid("runblocking")
                val async: Deferred<Unit> = CoroutineScope(Dispatchers.Unconfined).async {
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
}
