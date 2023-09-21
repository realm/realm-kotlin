/*
 * Copyright 2023 Realm Inc.
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
package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.entities.sync.ChildPk
import io.realm.kotlin.entities.sync.ObjectIdPk
import io.realm.kotlin.entities.sync.ParentPk
import io.realm.kotlin.entities.sync.SyncObjectWithAllTypes
import io.realm.kotlin.entities.sync.SyncPerson
import io.realm.kotlin.entities.sync.flx.FlexChildObject
import io.realm.kotlin.entities.sync.flx.FlexEmbeddedObject
import io.realm.kotlin.entities.sync.flx.FlexParentObject

private val ASYMMETRIC_CLASSES = setOf(
    AsymmetricSyncTests.AsymmetricA::class,
    AsymmetricSyncTests.EmbeddedB::class,
    AsymmetricSyncTests.StandardC::class,
    Measurement::class,
)

private val DEFAULT_CLASSES = setOf(
    BackupDevice::class,
    ChildPk::class,
    Device::class,
    DeviceParent::class,
    FlexChildObject::class,
    FlexEmbeddedObject::class,
    FlexParentObject::class,
    ObjectIdPk::class,
    ParentPk::class,
    SyncObjectWithAllTypes::class,
    SyncPerson::class
)

val FLX_SYNC_SCHEMA = DEFAULT_CLASSES + ASYMMETRIC_CLASSES
val PARTITION_SYNC_SCHEMA = DEFAULT_CLASSES
