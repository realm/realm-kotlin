package io.realm.internal.interop.sync

import realm_wrapper.realm_flx_sync_subscription_set_state

actual enum class CoreSubscriptionSetState(val nativeValue: realm_flx_sync_subscription_set_state) {
    RLM_SYNC_SUBSCRIPTION_UNCOMMITTED(realm_wrapper.RLM_SYNC_SUBSCRIPTION_UNCOMMITTED),
    RLM_SYNC_SUBSCRIPTION_PENDING(realm_wrapper.RLM_SYNC_SUBSCRIPTION_PENDING),
    RLM_SYNC_BOOTSTRAPPING(realm_wrapper.RLM_SYNC_BOOTSTRAPPING),
    RLM_SYNC_SUBSCRIPTION_COMPLETE(realm_wrapper.RLM_SYNC_SUBSCRIPTION_COMPLETE),
    RLM_SYNC_SUBSCRIPTION_ERROR(realm_wrapper.RLM_SYNC_SUBSCRIPTION_ERROR),
    RLM_SYNC_SUBSCRIPTION_SUPERSEDED(realm_wrapper.RLM_SYNC_SUBSCRIPTION_SUPERSEDED);

    companion object {
        fun of(state: realm_flx_sync_subscription_set_state): CoreSubscriptionSetState {
            for (value in values()) {
                if (value.nativeValue == state) {
                    return value
                }
            }
            error("Unknown subscription set state: $state")
        }
    }
}
