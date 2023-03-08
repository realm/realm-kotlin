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

package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.internal.RealmLog
import io.realm.kotlin.internal.SDK_VERSION
import io.realm.kotlin.internal.interop.CoreLogLevel
import io.realm.kotlin.internal.interop.RealmAppConfigurationPointer
import io.realm.kotlin.internal.interop.RealmAppPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmSyncClientConfigurationPointer
import io.realm.kotlin.internal.interop.SyncConnectionParams
import io.realm.kotlin.internal.interop.SyncLogCallback
import io.realm.kotlin.internal.interop.sync.MetadataMode
import io.realm.kotlin.internal.interop.sync.NetworkTransport
import io.realm.kotlin.internal.platform.CPU_ARCH
import io.realm.kotlin.internal.platform.DEVICE_MANUFACTURER
import io.realm.kotlin.internal.platform.DEVICE_MODEL
import io.realm.kotlin.internal.platform.OS_NAME
import io.realm.kotlin.internal.platform.OS_VERSION
import io.realm.kotlin.internal.platform.RUNTIME
import io.realm.kotlin.internal.platform.RUNTIME_VERSION
import io.realm.kotlin.internal.platform.appFilesDirectory
import io.realm.kotlin.internal.platform.freeze
import io.realm.kotlin.mongodb.AppConfiguration
import io.realm.kotlin.mongodb.AppConfiguration.Companion.DEFAULT_BASE_URL

// TODO Public due to being a transitive dependency to AppImpl
@Suppress("LongParameterList")
internal class AppConfigurationImpl constructor(
    override val appId: String,
    override val baseUrl: String = DEFAULT_BASE_URL,
    override val encryptionKey: ByteArray?,
    internal val networkTransportFactory: () -> NetworkTransport,
    override val metadataMode: MetadataMode,
    override val syncRootDirectory: String,
    public val log: RealmLog,
    override val appName: String?,
    override val appVersion: String?
) : AppConfiguration {

    /**
     * Since the app configuration holds a reference to a network transport we want to delay
     * construction of it to as late as possible.
     *
     * Thus this method should only be called from [AppImpl] and will create both a native
     * AppConfiguration and App at the same time.
     */
    public fun createNativeApp(): Pair<NetworkTransport, RealmAppPointer> {
        // Create a new network transport for each App instance. This which allow the App to control
        // the lifecycle of any threadpools created by the network transport. Also, there should
        // be no reason for people to have multiple app instances for the same app, so the net
        // effect should be the same
        val networkTransport = networkTransportFactory()
        val appConfigPointer: RealmAppConfigurationPointer =
            initializeRealmAppConfig(appName, appVersion, networkTransport)
        var applicationInfo: String? = null
        // Define user agent strings sent when making the WebSocket connection to Device Sync
        if (appName != null || appVersion == null) {
            val info = StringBuilder()
            appName?.let { info.append(appName) } ?: info.append("Unknown")
            info.append("/")
            appVersion?.let { info.append(appVersion) } ?: info.append("Unknown")
            applicationInfo = info.toString()
        }
        val sdkInfo = "RealmKotlin/$SDK_VERSION"
        val synClientConfig: RealmSyncClientConfigurationPointer = initializeSyncClientConfig(
            sdkInfo,
            applicationInfo.toString()
        )
        return Pair(
            networkTransport,
            RealmInterop.realm_app_get(
                appConfigPointer,
                synClientConfig,
                appFilesDirectory()
            )
        )
    }

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
    private fun initializeRealmAppConfig(
        localAppName: String?,
        localAppVersion: String?,
        networkTransport: NetworkTransport
    ): RealmAppConfigurationPointer {
        return RealmInterop.realm_app_config_new(
            appId = appId,
            baseUrl = baseUrl,
            networkTransport = RealmInterop.realm_network_transport_new(networkTransport),
            connectionParams = SyncConnectionParams(
                sdkVersion = SDK_VERSION,
                localAppName = localAppName,
                localAppVersion = localAppVersion,
                platform = OS_NAME,
                platformVersion = OS_VERSION,
                cpuArch = CPU_ARCH,
                device = DEVICE_MANUFACTURER,
                deviceVersion = DEVICE_MODEL,
                framework = RUNTIME,
                frameworkVersion = RUNTIME_VERSION
            )
        ).freeze()
    }

    private fun initializeSyncClientConfig(sdkInfo: String?, applicationInfo: String?): RealmSyncClientConfigurationPointer =
        RealmInterop.realm_sync_client_config_new()
            .also { syncClientConfig ->
                // Initialize client configuration first
                RealmInterop.realm_sync_client_config_set_default_binding_thread_observer(syncClientConfig, appId)
                RealmInterop.realm_sync_client_config_set_log_level(
                    syncClientConfig,
                    CoreLogLevel.valueFromPriority(log.logLevel.priority.toShort())
                )

                // TODO Redirect all Sync logs to the App configured logger. This makes it
                //  impossible to configure a different TAG for these logs. Previously
                //  these logs had a `SYNC` tag in contrast to the `REALM` tag used by the database.
                //  A redesign of this should also take into account that unified error logging
                //  is also coming in Core.
                RealmInterop.realm_sync_client_config_set_log_callback(
                    syncClientConfig,
                    object : SyncLogCallback {
                        override fun log(logLevel: Short, message: String?) {
                            when (val coreLogLevel = CoreLogLevel.valueFromPriority(logLevel)) {
                                CoreLogLevel.RLM_LOG_LEVEL_TRACE -> log.trace(message ?: "")
                                CoreLogLevel.RLM_LOG_LEVEL_DEBUG -> log.debug(message ?: "")
                                CoreLogLevel.RLM_LOG_LEVEL_DETAIL -> log.debug(message ?: "")
                                CoreLogLevel.RLM_LOG_LEVEL_INFO -> log.info(message ?: "")
                                CoreLogLevel.RLM_LOG_LEVEL_WARNING -> log.warn(message ?: "")
                                CoreLogLevel.RLM_LOG_LEVEL_ERROR -> log.error(message ?: "")
                                CoreLogLevel.RLM_LOG_LEVEL_FATAL -> log.error(message ?: "")
                                else -> throw IllegalStateException("Unsupported level: $coreLogLevel")
                            }
                        }
                    }
                )
                RealmInterop.realm_sync_client_config_set_metadata_mode(
                    syncClientConfig,
                    metadataMode
                )
                RealmInterop.realm_sync_client_config_set_base_file_path(
                    syncClientConfig,
                    syncRootDirectory
                )

                encryptionKey?.let {
                    RealmInterop.realm_sync_client_config_set_metadata_encryption_key(
                        syncClientConfig,
                        it
                    )
                }

                sdkInfo?.let {
                    RealmInterop.realm_sync_client_config_set_user_agent_binding_info(
                        syncClientConfig,
                        it
                    )
                }

                applicationInfo?.let {
                    RealmInterop.realm_sync_client_config_set_user_agent_application_info(
                        syncClientConfig,
                        it
                    )
                }
            }
}
