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

import io.realm.kotlin.CompactOnLaunchCallback
import io.realm.kotlin.InitialDataCallback
import io.realm.kotlin.InitialRealmFileConfiguration
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.internal.interop.SchemaMode
import io.realm.kotlin.internal.util.CoroutineDispatcherFactory
import io.realm.kotlin.migration.RealmMigration
import io.realm.kotlin.types.BaseRealmObject
import kotlin.reflect.KClass

public const val REALM_FILE_EXTENSION: String = ".realm"

@Suppress("LongParameterList")
internal class RealmConfigurationImpl(
    directory: String,
    name: String,
    schema: Set<KClass<out BaseRealmObject>>,
    maxNumberOfActiveVersions: Long,
    notificationDispatcherFactory: CoroutineDispatcherFactory,
    writeDispatcherFactory: CoroutineDispatcherFactory,
    schemaVersion: Long,
    encryptionKey: ByteArray?,
    override val deleteRealmIfMigrationNeeded: Boolean,
    compactOnLaunchCallback: CompactOnLaunchCallback?,
    migration: RealmMigration?,
    automaticBacklinkHandling: Boolean,
    initialDataCallback: InitialDataCallback?,
    inMemory: Boolean,
    initialRealmFileConfiguration: InitialRealmFileConfiguration?,
    logger: ContextLogger
) : ConfigurationImpl(
    directory,
    name,
    schema,
    maxNumberOfActiveVersions,
    notificationDispatcherFactory,
    writeDispatcherFactory,
    schemaVersion,
    when (deleteRealmIfMigrationNeeded) {
        true -> SchemaMode.RLM_SCHEMA_MODE_HARD_RESET_FILE
        false -> SchemaMode.RLM_SCHEMA_MODE_AUTOMATIC
    },
    encryptionKey,
    compactOnLaunchCallback,
    migration,
    automaticBacklinkHandling,
    initialDataCallback,
    inMemory,
    initialRealmFileConfiguration,
    logger
),
    RealmConfiguration
