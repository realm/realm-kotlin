package io.realm.kotlin.internal.interop.sync

import io.realm.kotlin.internal.interop.NativeEnumerated
import io.realm.kotlin.internal.interop.realm_sync_progress_direction_e

actual enum class ProgressDirection(override val nativeValue: Int) : NativeEnumerated {
    RLM_SYNC_PROGRESS_DIRECTION_UPLOAD(realm_sync_progress_direction_e.RLM_SYNC_PROGRESS_DIRECTION_UPLOAD),
    RLM_SYNC_PROGRESS_DIRECTION_DOWNLOAD(realm_sync_progress_direction_e.RLM_SYNC_PROGRESS_DIRECTION_DOWNLOAD),
}
