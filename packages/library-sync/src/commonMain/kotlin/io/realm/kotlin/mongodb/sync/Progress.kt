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

package io.realm.kotlin.mongodb.sync

/**
 * A **progress indicator** emitted by flows created from [SyncSession.progressAsFlow].
 */
public data class Progress(
    /**
     * Transfer progress estimation ranged from 0.0 to 1.0.
     */
    val estimate: Double,
) {
    /**
     * Property indicating if all pending bytes have been transferred.
     *
     * If the [Progress]-flow was created with [ProgressMode.CURRENT_CHANGES] then
     * the flow will complete when this returns `true`.
     *
     * If the [Progress]-flow was created with [ProgressMode.INDEFINITELY] then the
     * flow can continue to emit events with `isTransferComplete = false` for subsequent events
     * after returning a progress indicator with `isTransferComplete = true`.
     */
    public val isTransferComplete: Boolean = estimate >= 1.0
}
