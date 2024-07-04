/*
 * Copyright 2022 Realm Inc.
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
import io.realm.kotlin.internal.interop.realm_sync_session_state_e

actual enum class CoreSyncSessionState(override val nativeValue: Int) : NativeEnumerated {
    RLM_SYNC_SESSION_STATE_DYING(realm_sync_session_state_e.RLM_SYNC_SESSION_STATE_DYING),
    RLM_SYNC_SESSION_STATE_ACTIVE(realm_sync_session_state_e.RLM_SYNC_SESSION_STATE_ACTIVE),
    RLM_SYNC_SESSION_STATE_INACTIVE(realm_sync_session_state_e.RLM_SYNC_SESSION_STATE_INACTIVE),
    RLM_SYNC_SESSION_STATE_WAITING_FOR_ACCESS_TOKEN(realm_sync_session_state_e.RLM_SYNC_SESSION_STATE_WAITING_FOR_ACCESS_TOKEN),
    RLM_SYNC_SESSION_STATE_PAUSED(realm_sync_session_state_e.RLM_SYNC_SESSION_STATE_PAUSED);

    companion object {
        fun of(state: Int): CoreSyncSessionState =
            entries.find { it.nativeValue == state }
                ?: error("Unknown sync session state: $state")
    }
}
