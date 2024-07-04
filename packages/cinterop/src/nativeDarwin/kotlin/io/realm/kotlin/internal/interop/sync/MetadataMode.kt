/*
 * Copyright 2021 Realm Inc.
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

import realm_wrapper.realm_sync_client_metadata_mode_e

actual enum class MetadataMode(val metadataValue: realm_sync_client_metadata_mode_e) {
    RLM_SYNC_CLIENT_METADATA_MODE_DISABLED(realm_sync_client_metadata_mode_e.RLM_SYNC_CLIENT_METADATA_MODE_DISABLED),
    RLM_SYNC_CLIENT_METADATA_MODE_PLAINTEXT(realm_sync_client_metadata_mode_e.RLM_SYNC_CLIENT_METADATA_MODE_PLAINTEXT),
    RLM_SYNC_CLIENT_METADATA_MODE_ENCRYPTED(realm_sync_client_metadata_mode_e.RLM_SYNC_CLIENT_METADATA_MODE_ENCRYPTED)
}
