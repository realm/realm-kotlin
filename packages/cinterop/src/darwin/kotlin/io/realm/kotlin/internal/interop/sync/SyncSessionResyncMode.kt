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
import realm_wrapper.realm_sync_session_resync_mode

actual enum class SyncSessionResyncMode(override val nativeValue: UInt) : NativeEnumerated {
    RLM_SYNC_SESSION_RESYNC_MODE_MANUAL(realm_sync_session_resync_mode.RLM_SYNC_SESSION_RESYNC_MODE_MANUAL.value),
    RLM_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL(realm_sync_session_resync_mode.RLM_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL.value),
    RLM_SYNC_SESSION_RESYNC_MODE_RECOVER(realm_sync_session_resync_mode.RLM_SYNC_SESSION_RESYNC_MODE_RECOVER.value),
    RLM_SYNC_SESSION_RESYNC_MODE_RECOVER_OR_DISCARD(realm_sync_session_resync_mode.RLM_SYNC_SESSION_RESYNC_MODE_RECOVER_OR_DISCARD.value);

    actual companion object {
        actual fun fromInt(nativeValue: Int): SyncSessionResyncMode {
            for (value in SyncSessionResyncMode.values()) {
                if (value.nativeValue.toInt() == nativeValue) {
                    return value
                }
            }
            error("Unknown session resync mode: $nativeValue")
        }
    }
}
