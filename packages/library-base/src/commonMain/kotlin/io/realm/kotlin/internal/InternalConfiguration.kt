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

    // Temporary work-around for https://github.com/realm/realm-kotlin/issues/724
    public val isFlexibleSyncConfiguration: Boolean

    /**
     * Creates a new native Config object based on all the settings in this configuration.
     * Each pointer should only be used to open _one_ realm. If you want to open multiple realms
     * with the same [Configuration], this method should be called for each one of them.
     */
    public fun createNativeConfiguration(): RealmConfigurationPointer

    /**
     * This function is a way `RealmImpl` can control functionality that might differ depending
     * on whether a SyncConfiguration or RealmConfiguration was used. This allows us to run logic
     * that is associated with initial bootstrapping like running `initialSubscriptions`,
     * `initialData` or `waitForInitialRemoteData`.
     *
     * In Java we uses reflection to accomplish this,  but this isn't available on Kotlin Native.
     * So as a work-around we use the `InternalConfiguration` interface as that is being implemented
     * by both `RealmConfigurationImpl` and `SyncConfigurationImpl`.
     *
     * @param realm instance of the Realm that was just opened.
     * @param fileCreated `true` if the Realm file was created as part of opening the Realm.
     */
    public suspend fun realmOpened(realm: RealmImpl, fileCreated: Boolean)

    public fun debug(): String {
        return "path=$path\n" +
            " name=$name\n" +
            " maxNumberOfActiveVersions=$maxNumberOfActiveVersions\n" +
            " schemaVersion=$schemaVersion\n" +
            " schemaMode=$schemaMode\n" +
            " schema=$schema"
    }
}
