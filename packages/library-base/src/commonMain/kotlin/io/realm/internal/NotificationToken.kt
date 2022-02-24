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

package io.realm.internal

import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.notifications.Cancellable
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

internal class NotificationToken<T>(callback: T, private val token: NativePointer) : Cancellable {

    private val lock = reentrantLock()
    private val observer: AtomicRef<T?> = atomic(callback)

    override fun cancel() {
        lock.withLock {
            if (observer.value != null) {
                RealmInterop.realm_release(token)
            }
            observer.value = null
        }
    }

    // FIXME API We currently favor to do explicit registration.
    //  Only works on JVM. KN Cleaner is not available before v1.4.30-M1-eap-48
    //  https://github.com/realm/realm-kotlin/issues/23
    //  fun finalize() {
    //      cancel()
    //  }
}
