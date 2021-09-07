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

package io.realm.mongodb

import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import io.realm.mongodb.internal.KtorNetworkTransport
import kotlinx.coroutines.CoroutineDispatcher

/**
 * TODO
 */
interface AppConfiguration {
    val appId: String
    val dispatcher: CoroutineDispatcher
    val nativePointer: NativePointer
}

/**
 * TODO
 */
internal class AppConfigurationImpl(
    override val appId: String,
    override val dispatcher: CoroutineDispatcher,
) : AppConfiguration {

    override val nativePointer: NativePointer =
        RealmInterop.realm_app_config_new(appId) {
            KtorNetworkTransport(
                timeoutMs = 5000,
                dispatcher = dispatcher
            )
        }
}
