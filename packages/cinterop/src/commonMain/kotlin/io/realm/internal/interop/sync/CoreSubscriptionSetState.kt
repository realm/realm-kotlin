package io.realm.internal.interop.sync

/**
 * Wrapper around C-API `realm_flx_sync_subscription_set_state`
 */
expect enum class CoreSubscriptionSetState {
    RLM_SYNC_SUBSCRIPTION_UNCOMMITTED,
    RLM_SYNC_SUBSCRIPTION_PENDING,
    RLM_SYNC_BOOTSTRAPPING,
    RLM_SYNC_SUBSCRIPTION_COMPLETE,
    RLM_SYNC_SUBSCRIPTION_ERROR,
    RLM_SYNC_SUBSCRIPTION_SUPERSEDED;
}