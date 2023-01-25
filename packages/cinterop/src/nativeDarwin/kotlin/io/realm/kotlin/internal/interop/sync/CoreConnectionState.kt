/*
 * Copyright 2023 Realm Inc.
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

package io.realm.kotlin.internal.interop.sync

import io.realm.kotlin.internal.interop.NativeEnumerated
import realm_wrapper.realm_sync_connection_state

actual enum class CoreConnectionState(override val nativeValue: UInt) : NativeEnumerated {
    RLM_SYNC_CONNECTION_STATE_DISCONNECTED(realm_sync_connection_state.RLM_SYNC_CONNECTION_STATE_DISCONNECTED.value),
    RLM_SYNC_CONNECTION_STATE_CONNECTING(realm_sync_connection_state.RLM_SYNC_CONNECTION_STATE_CONNECTING.value),
    RLM_SYNC_CONNECTION_STATE_CONNECTED(realm_sync_connection_state.RLM_SYNC_CONNECTION_STATE_CONNECTED.value),
    ;

    actual companion object {
        actual fun of(nativeValue: Int): CoreConnectionState =
            values().find { it.nativeValue == nativeValue.toUInt() }
                ?: error("Unknown property type: $nativeValue")
    }
}
