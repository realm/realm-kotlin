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

package io.realm.mongodb.internal

import io.realm.internal.InternalRealmConfiguration
import io.realm.internal.RealmConfigurationImpl
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.sync.PartitionValue
import io.realm.mongodb.AppException
import io.realm.mongodb.SyncConfiguration
import io.realm.mongodb.SyncSession

internal class SyncConfigurationImpl(
    localConfiguration: RealmConfigurationImpl,
    override val partitionValue: PartitionValue,
    override val user: UserImpl,
    override val errorHandler: (SyncSession, AppException) -> Unit
) : InternalRealmConfiguration by localConfiguration, SyncConfiguration {

    private val nativeSyncConfig: NativePointer =
        RealmInterop.realm_sync_config_new(user.nativePointer, partitionValue.asSyncPartition())

    init {
        RealmInterop.realm_sync_set_error_handler(nativeSyncConfig) { syncSessionPtr, error ->
            errorHandler(SyncSessionImpl(syncSessionPtr), error)
        }
        RealmInterop.realm_config_set_sync_config(localConfiguration.nativeConfig, nativeSyncConfig)
    }
}
