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

import io.realm.internal.RealmLog
import io.realm.internal.interop.CoreLogLevel
import io.realm.internal.interop.RealmAppConfigurationPointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmSyncClientConfigurationPointer
import io.realm.internal.interop.SyncLogCallback
import io.realm.internal.interop.sync.MetadataMode
import io.realm.internal.interop.sync.NetworkTransport
import io.realm.internal.platform.OS_NAME
import io.realm.internal.platform.OS_VERSION
import io.realm.internal.platform.RUNTIME
import io.realm.internal.platform.createDefaultSystemLogger
import io.realm.internal.platform.freeze
import io.realm.log.LogLevel
import io.realm.mongodb.AppConfiguration
import io.realm.mongodb.AppConfiguration.Companion.DEFAULT_BASE_URL
import io.realm.mongodb.sync.DiscardUnsyncedChangesStrategy
import io.realm.mongodb.sync.ManuallyRecoverUnsyncedChangesStrategy

// TODO Public due to being a transitive dependency to AppImpl
@Suppress("LongParameterList")
public class AppConfigurationImpl constructor(
    override val appId: String,
    override val baseUrl: String = DEFAULT_BASE_URL,
    override val networkTransport: NetworkTransport,
    override val metadataMode: MetadataMode = MetadataMode.RLM_SYNC_CLIENT_METADATA_MODE_PLAINTEXT,
    override val syncRootDirectory: String,
    override val defaultPartitionSyncClientResetStrategy: DiscardUnsyncedChangesStrategy,
    override val defaultFlexibleSyncClientResetStrategy: ManuallyRecoverUnsyncedChangesStrategy,
    public val log: RealmLog
) : AppConfiguration {

    public val nativePointer: RealmAppConfigurationPointer = initializeRealmAppConfig()
    public val synClientConfig: RealmSyncClientConfigurationPointer = initializeSyncClientConfig()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AppConfigurationImpl

        if (appId != (other.appId)) return false
        if (baseUrl != (other.baseUrl)) return false
        if (metadataMode != (other.metadataMode)) return false
        return log == other.log
    }

    override fun hashCode(): Int {
        var result = appId.hashCode()
        result = 31 * result + baseUrl.hashCode()
        result = 31 * result + metadataMode.hashCode()
        result = 31 * result + log.hashCode()
        return result
    }

    // Only freeze anything after all properties are setup as this triggers freezing the actual
    // AppConfigurationImpl instance itself
    private fun initializeRealmAppConfig(): RealmAppConfigurationPointer =
        RealmInterop.realm_app_config_new(
            appId = appId,
            baseUrl = baseUrl,
            networkTransport = RealmInterop.realm_network_transport_new(networkTransport),
            platform = "$OS_NAME/$RUNTIME",
            platformVersion = OS_VERSION,
            sdkVersion = io.realm.internal.SDK_VERSION
        ).freeze()

    private fun initializeSyncClientConfig(): RealmSyncClientConfigurationPointer =
        RealmInterop.realm_sync_client_config_new()
            .also { syncClientConfig ->
                // TODO use separate logger for sync or piggyback on config's?
                val syncLogger = createDefaultSystemLogger("SYNC", log.logLevel)

                // Initialize client configuration first
                RealmInterop.realm_sync_client_config_set_log_callback(
                    syncClientConfig,
                    object : SyncLogCallback {
                        override fun log(logLevel: Short, message: String?) {
                            val coreLogLevel = CoreLogLevel.valueFromPriority(logLevel)
                            syncLogger.log(LogLevel.fromCoreLogLevel(coreLogLevel), message ?: "")
                        }
                    }
                )
                RealmInterop.realm_sync_client_config_set_log_level(
                    syncClientConfig,
                    CoreLogLevel.valueFromPriority(log.logLevel.priority.toShort())
                )
                RealmInterop.realm_sync_client_config_set_metadata_mode(
                    syncClientConfig,
                    metadataMode
                )
                RealmInterop.realm_sync_client_config_set_base_file_path(
                    syncClientConfig,
                    syncRootDirectory
                )
            }
}
