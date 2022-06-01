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

package io.realm.kotlin.internal

import io.realm.kotlin.Configuration
import io.realm.kotlin.internal.interop.RealmConfigurationPointer
import io.realm.kotlin.internal.interop.SchemaMode
import io.realm.kotlin.types.BaseRealmObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.reflect.KClass

/**
 * An **internal Realm configuration** that holds internal properties from a
 * [io.realm.Configuration]. This is needed to make "agnostic" configurations from a base-sync
 * point of view.
 */
// TODO Public due to being transitive dependency to `ConfigurationImpl` and `SyncConfigurationImpl`.
public interface InternalConfiguration : Configuration {
    public val mapOfKClassWithCompanion: Map<KClass<out BaseRealmObject>, RealmObjectCompanion>
    public val mediator: Mediator
    public val notificationDispatcher: CoroutineDispatcher
    public val writeDispatcher: CoroutineDispatcher
    public val schemaMode: SchemaMode

    /**
     * Creates a new native Config object based on all the settings in this configuration.
     * Each pointer should only be used to open _one_ realm. If you want to open multiple realms
     * with the same [Configuration], this method should be called for each one of them.
     */
    public open fun createNativeConfiguration(): RealmConfigurationPointer

    public fun debug(): String {
        return "path=$path\n" +
            " name=$name\n" +
            " maxNumberOfActiveVersions=$maxNumberOfActiveVersions\n" +
            " schemaVersion=$schemaVersion\n" +
            " schemaMode=$schemaMode\n" +
            " schema=$schema"
    }
}
