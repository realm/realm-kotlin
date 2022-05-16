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

package io.realm.internal

import io.realm.BaseRealmObject
import io.realm.CompactOnLaunchCallback
import io.realm.LogConfiguration
import io.realm.RealmConfiguration
import io.realm.internal.interop.SchemaMode
import io.realm.migration.RealmMigration
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.reflect.KClass

public const val REALM_FILE_EXTENSION: String = ".realm"

@Suppress("LongParameterList")
internal class RealmConfigurationImpl constructor(
    directory: String,
    name: String,
    schema: Set<KClass<out BaseRealmObject>>,
    logConfig: LogConfiguration,
    maxNumberOfActiveVersions: Long,
    notificationDispatcher: CoroutineDispatcher,
    writeDispatcher: CoroutineDispatcher,
    schemaVersion: Long,
    encryptionKey: ByteArray?,
    override val deleteRealmIfMigrationNeeded: Boolean,
    compactOnLaunchCallback: CompactOnLaunchCallback?,
    migration: RealmMigration?
) : ConfigurationImpl(
    directory,
    name,
    schema,
    logConfig,
    maxNumberOfActiveVersions,
    notificationDispatcher,
    writeDispatcher,
    schemaVersion,
    when (deleteRealmIfMigrationNeeded) {
        true -> SchemaMode.RLM_SCHEMA_MODE_HARD_RESET_FILE
        false -> SchemaMode.RLM_SCHEMA_MODE_AUTOMATIC
    },
    encryptionKey,
    compactOnLaunchCallback,
    migration
),
    RealmConfiguration
