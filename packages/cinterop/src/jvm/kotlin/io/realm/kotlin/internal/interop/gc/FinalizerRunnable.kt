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

package io.realm.kotlin.internal.interop.gc

import io.realm.kotlin.internal.interop.LongPointerWrapper
import java.lang.ref.ReferenceQueue

// Running in the FinalizingDaemon thread to free native objects.
internal class FinalizerRunnable(private val referenceQueue: ReferenceQueue<LongPointerWrapper<*>>) :
    Runnable {
    override fun run() {
        try {
            while (true) {
                val reference: NativeObjectReference =
                    referenceQueue.remove() as NativeObjectReference
                reference.cleanup()
            }
        } catch (e: InterruptedException) {
            // Restores the interrupted status.
            Thread.currentThread().interrupt()
            // FIXME implement platform Logger and log the below with fatal level
            println(
                "The FinalizerRunnable thread has been interrupted." +
                    " Native resources cannot be freed anymore"
            )
        }
    }
}
