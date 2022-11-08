package io.realm.kotlin.internal.interop.sync

import realm_wrapper.realm_sync_progress_direction_e

actual enum class ProgressDirection(val nativeValue: realm_sync_progress_direction_e) {
    RLM_SYNC_PROGRESS_DIRECTION_UPLOAD(realm_sync_progress_direction_e.RLM_SYNC_PROGRESS_DIRECTION_UPLOAD),
    RLM_SYNC_PROGRESS_DIRECTION_DOWNLOAD(realm_sync_progress_direction_e.RLM_SYNC_PROGRESS_DIRECTION_DOWNLOAD),
}
