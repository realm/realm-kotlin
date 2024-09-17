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
import io.realm.kotlin.internal.util.CoroutineDispatcherFactory
import io.realm.kotlin.types.BaseRealmObject
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
    public val notificationDispatcherFactory: CoroutineDispatcherFactory
    public val writeDispatcherFactory: CoroutineDispatcherFactory
    public val schemaMode: SchemaMode
    public val logger: ContextLogger

    /**
     * Creates a new native Config object based on all the settings in this configuration.
     * Each pointer should only be used to open _one_ realm. If you want to open multiple realms
     * with the same [Configuration], this method should be called for each one of them.
     */
    public fun createNativeConfiguration(): RealmConfigurationPointer

    /**
     * This function is a way `RealmImpl` can defer how the Realm is opened to either a local
     * or sync code path. Synced and Local Realms will differ depending on
     * whether `SyncConfiguration.waitForInitialRemoteData` is set not.
     *
     * In Java we uses reflection to accomplish this,  but this isn't available on Kotlin Native.
     * So as a work-around we use the `InternalConfiguration` interface as that is being implemented
     * by both `RealmConfigurationImpl` and `SyncConfigurationImpl`.
     *
     * @param realm instance of the Realm that is being created.
     * @returns a pair of (LiveRealmPointer, FileCreated)
     */
    public suspend fun openRealm(realm: RealmImpl): Pair<FrozenRealmReference, Boolean>

    /**
     * This function is a way `RealmImpl` can defer how the Realm is initialized once opened.
     * Synced and Local Realms will differ depending on whether `Configuration.initialData` or
     * `SyncConfiguration.initialSubscriptions` are set.
     *
     * @param realm instance of the Realm that is being created.
     * @param realmFileCreated `true` if the Realm file was just created, `false` if it already existed.
     */
    public suspend fun initializeRealmData(realm: RealmImpl, realmFileCreated: Boolean)

    public fun debug(): String {
        return "path=$path\n" +
            " name=$name\n" +
            " maxNumberOfActiveVersions=$maxNumberOfActiveVersions\n" +
            " schemaVersion=$schemaVersion\n" +
            " schemaMode=$schemaMode\n" +
            " schema=$schema"
    }
}
