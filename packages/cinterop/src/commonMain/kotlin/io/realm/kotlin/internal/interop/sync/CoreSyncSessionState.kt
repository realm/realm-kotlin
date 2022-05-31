package io.realm.kotlin.internal.interop.sync

/**
 * Wrapper around C-API `realm_sync_session_state`
 */
expect enum class CoreSyncSessionState {
    RLM_SYNC_SESSION_STATE_DYING,
    RLM_SYNC_SESSION_STATE_ACTIVE,
    RLM_SYNC_SESSION_STATE_INACTIVE,
    RLM_SYNC_SESSION_STATE_WAITING_FOR_ACCESS_TOKEN;
}
