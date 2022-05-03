package io.realm.internal.interop.sync

import io.realm.internal.interop.NativeEnumerated
import io.realm.internal.interop.realm_flx_sync_subscription_set_state_e

actual enum class CoreSubscriptionSetState(override val nativeValue: Int): NativeEnumerated {
    RLM_SYNC_SUBSCRIPTION_UNCOMMITTED(realm_flx_sync_subscription_set_state_e.RLM_SYNC_SUBSCRIPTION_UNCOMMITTED),
    RLM_SYNC_SUBSCRIPTION_PENDING(realm_flx_sync_subscription_set_state_e.RLM_SYNC_SUBSCRIPTION_PENDING),
    RLM_SYNC_BOOTSTRAPPING(realm_flx_sync_subscription_set_state_e.RLM_SYNC_BOOTSTRAPPING),
    RLM_SYNC_SUBSCRIPTION_COMPLETE(realm_flx_sync_subscription_set_state_e.RLM_SYNC_SUBSCRIPTION_COMPLETE),
    RLM_SYNC_SUBSCRIPTION_ERROR(realm_flx_sync_subscription_set_state_e.RLM_SYNC_SUBSCRIPTION_ERROR),
    RLM_SYNC_SUBSCRIPTION_SUPERSEDED(realm_flx_sync_subscription_set_state_e.RLM_SYNC_SUBSCRIPTION_SUPERSEDED);

    companion object {
        fun of(state: Int): CoreSubscriptionSetState {
            for (value in values()) {
                if (value.nativeValue == state) {
                    return value
                }
            }
            error("Unknown subscription set state: $state")
        }
    }
}