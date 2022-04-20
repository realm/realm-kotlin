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

package io.realm.internal.interop.sync

import io.realm.internal.interop.NativeEnumerated
import io.realm.internal.interop.realm_user_state_e

actual enum class CoreUserState(
    override val nativeValue: Int
) : NativeEnumerated {

    RLM_USER_STATE_LOGGED_OUT(realm_user_state_e.RLM_USER_STATE_LOGGED_OUT),
    RLM_USER_STATE_LOGGED_IN(realm_user_state_e.RLM_USER_STATE_LOGGED_IN),
    RLM_USER_STATE_REMOVED(realm_user_state_e.RLM_USER_STATE_REMOVED);

    companion object {
        // TODO Optimize
        fun of(state: Int): CoreUserState {
            for (value in values()) {
                if (value.nativeValue == state) {
                    return value
                }
            }
            error("Unknown user state: $state")
        }
    }
}
