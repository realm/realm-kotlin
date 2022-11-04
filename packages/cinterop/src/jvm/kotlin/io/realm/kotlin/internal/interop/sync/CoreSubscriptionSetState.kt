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
import io.realm.kotlin.internal.interop.realm_flx_sync_subscription_set_state_e

actual enum class CoreSubscriptionSetState(override val nativeValue: Int) : NativeEnumerated {
    RLM_SYNC_SUBSCRIPTION_UNCOMMITTED(realm_flx_sync_subscription_set_state_e.RLM_SYNC_SUBSCRIPTION_UNCOMMITTED),
    RLM_SYNC_SUBSCRIPTION_PENDING(realm_flx_sync_subscription_set_state_e.RLM_SYNC_SUBSCRIPTION_PENDING),
    RLM_SYNC_SUBSCRIPTION_BOOTSTRAPPING(realm_flx_sync_subscription_set_state_e.RLM_SYNC_SUBSCRIPTION_BOOTSTRAPPING),
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
