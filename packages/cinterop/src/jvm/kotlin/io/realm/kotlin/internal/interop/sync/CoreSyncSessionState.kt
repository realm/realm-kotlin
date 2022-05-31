package io.realm.kotlin.internal.interop.sync

import io.realm.kotlin.internal.interop.NativeEnumerated
import io.realm.kotlin.internal.interop.realm_sync_session_state_e

actual enum class CoreSyncSessionState(override val nativeValue: Int) : NativeEnumerated {
    RLM_SYNC_SESSION_STATE_DYING(realm_sync_session_state_e.RLM_SYNC_SESSION_STATE_DYING),
    RLM_SYNC_SESSION_STATE_ACTIVE(realm_sync_session_state_e.RLM_SYNC_SESSION_STATE_ACTIVE),
    RLM_SYNC_SESSION_STATE_INACTIVE(realm_sync_session_state_e.RLM_SYNC_SESSION_STATE_INACTIVE),
    RLM_SYNC_SESSION_STATE_WAITING_FOR_ACCESS_TOKEN(realm_sync_session_state_e.RLM_SYNC_SESSION_STATE_WAITING_FOR_ACCESS_TOKEN);

    companion object {
        fun of(state: Int): CoreSyncSessionState {
            for (value in values()) {
                if (value.nativeValue == state) {
                    return value
                }
            }
            error("Unknown sync session state: $state")
        }
    }
}
