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

import io.realm.kotlin.internal.interop.realm_sync_connection_state_e

actual enum class CoreConnectionState(val value: Int) {
    RLM_SYNC_CONNECTION_STATE_DISCONNECTED(realm_sync_connection_state_e.RLM_SYNC_CONNECTION_STATE_DISCONNECTED),
    RLM_SYNC_CONNECTION_STATE_CONNECTING(realm_sync_connection_state_e.RLM_SYNC_CONNECTION_STATE_CONNECTING),
    RLM_SYNC_CONNECTION_STATE_CONNECTED(realm_sync_connection_state_e.RLM_SYNC_CONNECTION_STATE_CONNECTED);

    companion object {
        fun of(value: Int): CoreConnectionState =
            entries.find { it.value == value }
                ?: error("Unknown connection state: $value")
    }
}
