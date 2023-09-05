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

import io.realm.kotlin.LogConfiguration
import io.realm.kotlin.internal.SDK_VERSION
import io.realm.kotlin.internal.interop.RealmAppConfigurationPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmSyncClientConfigurationPointer
import io.realm.kotlin.internal.interop.SyncConnectionParams
import io.realm.kotlin.internal.interop.sync.MetadataMode
import io.realm.kotlin.internal.interop.sync.NetworkTransport
import io.realm.kotlin.internal.platform.DEVICE_MANUFACTURER
import io.realm.kotlin.internal.platform.DEVICE_MODEL
import io.realm.kotlin.internal.platform.OS_VERSION
import io.realm.kotlin.internal.platform.RUNTIME
import io.realm.kotlin.internal.platform.RUNTIME_VERSION
import io.realm.kotlin.internal.platform.appFilesDirectory
import io.realm.kotlin.internal.util.CoroutineDispatcherFactory
import io.realm.kotlin.internal.util.DispatcherHolder
import io.realm.kotlin.mongodb.AppConfiguration
import io.realm.kotlin.mongodb.AppConfiguration.Companion.DEFAULT_BASE_URL
import io.realm.kotlin.mongodb.HttpLogObfuscator
import org.mongodb.kbson.ExperimentalKBsonSerializerApi
import org.mongodb.kbson.serialization.EJson

// TODO Public due to being a transitive dependency to AppImpl
@Suppress("LongParameterList")
public class AppConfigurationImpl @OptIn(ExperimentalKBsonSerializerApi::class) constructor(
    override val appId: String,
    override val baseUrl: String = DEFAULT_BASE_URL,
    override val encryptionKey: ByteArray?,
    private val appNetworkDispatcherFactory: CoroutineDispatcherFactory,
    internal val networkTransportFactory: (dispatcher: DispatcherHolder) -> NetworkTransport,
    override val metadataMode: MetadataMode,
    override val syncRootDirectory: String,
    public val logger: LogConfiguration?,
    override val appName: String?,
    override val appVersion: String?,
    internal val bundleId: String,
    override val ejson: EJson,
    override val httpLogObfuscator: HttpLogObfuscator?,
    override val customRequestHeaders: Map<String, String>,
    override val authorizationHeaderName: String,
) : AppConfiguration {

    /**
     * Since the app configuration holds a reference to a network transport we want to delay
     * construction of it to as late as possible.
     *
     * Thus this method should only be called from [AppImpl] and will create both a native
     * AppConfiguration and App at the same time.
     */
    public fun createNativeApp(): AppResources {
        // Create a new network transport for each App instance. This which allow the App to control
        // the lifecycle of any threadpools created by the network transport. Also, there should
        // be no reason for people to have multiple app instances for the same app, so the net
        // effect should be the same
        val appDispatcher = appNetworkDispatcherFactory.create()
        val networkTransport = networkTransportFactory(appDispatcher)
        val appConfigPointer: RealmAppConfigurationPointer =
            initializeRealmAppConfig(bundleId, networkTransport)
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
        return Triple(
            appDispatcher,
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
        return true
    }

    override fun hashCode(): Int {
        var result = appId.hashCode()
        result = 31 * result + baseUrl.hashCode()
        result = 31 * result + metadataMode.hashCode()
        return result
    }

    // Only freeze anything after all properties are setup as this triggers freezing the actual
    // AppConfigurationImpl instance itself
    private fun initializeRealmAppConfig(
        bundleId: String,
        networkTransport: NetworkTransport
    ): RealmAppConfigurationPointer {
        return RealmInterop.realm_app_config_new(
            appId = appId,
            baseUrl = baseUrl,
            networkTransport = RealmInterop.realm_network_transport_new(networkTransport),
            connectionParams = SyncConnectionParams(
                sdkVersion = SDK_VERSION,
                bundleId = bundleId,
                platformVersion = OS_VERSION,
                device = DEVICE_MANUFACTURER,
                deviceVersion = DEVICE_MODEL,
                framework = RUNTIME,
                frameworkVersion = RUNTIME_VERSION
            )
        )
    }

    private fun initializeSyncClientConfig(sdkInfo: String?, applicationInfo: String?): RealmSyncClientConfigurationPointer =
        RealmInterop.realm_sync_client_config_new()
            .also { syncClientConfig ->
                // Initialize client configuration first
                RealmInterop.realm_sync_client_config_set_default_binding_thread_observer(syncClientConfig, appId)
                RealmInterop.realm_sync_client_config_set_metadata_mode(
                    syncClientConfig,
                    metadataMode
                )
                RealmInterop.realm_sync_client_config_set_base_file_path(
                    syncClientConfig,
                    syncRootDirectory
                )

                // Disable multiplexing. See https://github.com/realm/realm-core/issues/6656
                RealmInterop.realm_sync_client_config_set_multiplex_sessions(syncClientConfig, false)

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

    internal companion object {
        internal fun create(appId: String, bundleId: String): AppConfiguration =
            AppConfiguration.Builder(appId).build(bundleId)
    }
}
