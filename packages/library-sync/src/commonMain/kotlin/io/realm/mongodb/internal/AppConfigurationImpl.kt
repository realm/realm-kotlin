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
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.sync.MetadataMode
import io.realm.internal.interop.sync.NetworkTransport
import io.realm.internal.platform.OS_NAME
import io.realm.internal.platform.OS_VERSION
import io.realm.internal.platform.RUNTIME
import io.realm.internal.platform.freeze
import io.realm.mongodb.AppConfiguration
import io.realm.mongodb.AppConfiguration.Companion.DEFAULT_BASE_URL

internal class AppConfigurationImpl(
    override val appId: String,
    override val baseUrl: String = DEFAULT_BASE_URL,
    override val networkTransport: NetworkTransport,
    override val metadataMode: MetadataMode = MetadataMode.RLM_SYNC_CLIENT_METADATA_MODE_PLAINTEXT,
    val log: RealmLog
) : AppConfiguration {

    // Only freeze anything after all properties are setup as this triggers freezing the actual
    // AppConfigurationImpl instance itself
    val nativePointer: NativePointer = RealmInterop.realm_app_config_new(
        appId = appId,
        baseUrl = baseUrl,
        networkTransport = RealmInterop.realm_network_transport_new(networkTransport),
        platform = "$OS_NAME/$RUNTIME",
        platformVersion = OS_VERSION,
        sdkVersion = io.realm.internal.SDK_VERSION
    )//.freeze() // MM: Is this needed at all?

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
}
