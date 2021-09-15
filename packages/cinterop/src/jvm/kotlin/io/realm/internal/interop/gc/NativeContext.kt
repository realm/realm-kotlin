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

package io.realm.internal.interop.gc

import io.realm.internal.interop.LongPointerWrapper
import java.lang.ref.ReferenceQueue

object NativeContext {
    private val referenceQueue: ReferenceQueue<LongPointerWrapper> = ReferenceQueue<LongPointerWrapper>()
    private val finalizingThread = Thread(FinalizerRunnable(referenceQueue))

    init {
        finalizingThread.name = "RealmFinalizingDaemon"
        finalizingThread.start()
    }

    fun addReference(referent: LongPointerWrapper) {
        NativeObjectReference(this, referent, referenceQueue)
    }
}
