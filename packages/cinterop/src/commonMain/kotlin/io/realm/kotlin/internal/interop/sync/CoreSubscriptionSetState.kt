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

/**
 * Wrapper around C-API `realm_flx_sync_subscription_set_state`
 * See https://github.com/realm/realm-core/blob/master/src/realm.h#L3356
 */
expect enum class CoreSubscriptionSetState {
    RLM_SYNC_SUBSCRIPTION_UNCOMMITTED,
    RLM_SYNC_SUBSCRIPTION_PENDING,
    RLM_SYNC_SUBSCRIPTION_BOOTSTRAPPING,
    RLM_SYNC_SUBSCRIPTION_COMPLETE,
    RLM_SYNC_SUBSCRIPTION_ERROR,
    RLM_SYNC_SUBSCRIPTION_SUPERSEDED;
}
