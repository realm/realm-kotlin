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
 * A **direction** indicates whether a given [Progress]-flow created with
 * [SyncSession.progress] is reporting changes when either uploading or downloading data.
 */
public enum class Direction {
    /**
     * Used to pass to [SyncSession.progress] to create a flow that reports [Progress]
     * when downloading data from the server.
     */
    DOWNLOAD,
    /**
     * Used to pass to [SyncSession.progress] to create a flow that reports [Progress]
     * when uploading data from the device to the server.
     */
    UPLOAD,
}
