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

import io.realm.RealmObject
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop

/**
 * TODO align with RealmConfiguration once interfaces are merged
 */
interface SyncConfiguration {

    val user: User
    val partition: String
    val nativePointer: NativePointer

    companion object {
        fun <T : RealmObject> defaultConfig(
            user: User,
            partition: String,
            schema: Set<T>
        ): SyncConfiguration {
            return SyncConfigurationImpl(user, partition)
        }
    }
}

/**
 * TODO
 */
internal class SyncConfigurationImpl(
    override val user: User,
    override val partition: String
) : SyncConfiguration {

    override val nativePointer: NativePointer =
        RealmInterop.realm_sync_config_new(user.nativePointer, partition)
}
