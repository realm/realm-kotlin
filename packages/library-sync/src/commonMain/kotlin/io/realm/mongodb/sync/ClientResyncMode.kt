/*
 * Copyright 2020 Realm Inc.
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
package io.realm.mongodb.sync

/**
 * Enum describing what should happen in case of a Client Resync.
 *
 *
 * A Client Resync is triggered if the device and server cannot agree on a common shared history
 * for the Realm file, thus making it impossible for the device to upload or receive any changes.
 * This can happen if the server is rolled back or restored from backup.
 *
 *
 * **IMPORTANT:** Just having the device offline will not trigger a Client Resync.
 */
enum class ClientResyncMode(nativeValue: Int) {
    /**
     * The local Realm will be discarded and replaced with the server side Realm.
     * All local changes will be lost.
     */
    DISCARD_LOCAL_REALM(0 /*OsRealmConfig.CLIENT_RESYNC_MODE_DISCARD*/),

    /**
     * A manual Client Resync is also known as a Client Reset.
     *
     *
     * A [ClientResetRequiredError] will be sent to
     * [SyncSession.ErrorHandler.onError], triggering
     * a Client Reset. Doing this provides a handle to both the old and new Realm file, enabling
     * full control of which changes to move, if any.
     *
     * @see SyncSession.ErrorHandler.onError
     */
    MANUAL(1 /*OsRealmConfig.CLIENT_RESYNC_MODE_MANUAL*/);
}