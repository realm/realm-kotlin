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

import io.realm.Cancellable
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop

class NotificationToken<T>(t: T, private val token: NativePointer) : Cancellable {

    private var t: T? = t

    override fun cancel() {
        if (t != null) {
            RealmInterop.realm_release(token)
        }
        t = null
    }

    // FIXME API We currently favor to do explicit registration.
    //  Only works on JVM. KN Cleaner is not available before v1.4.30-M1-eap-48
    //  https://github.com/realm/realm-kotlin/issues/23
    //  fun finalize() {
    //      cancel()
    //  }
}
