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

import io.realm.kotlin.entities.Location
import io.realm.kotlin.entities.sync.BinaryObject
import io.realm.kotlin.entities.sync.ChildPk
import io.realm.kotlin.entities.sync.ObjectIdPk
import io.realm.kotlin.entities.sync.ParentPk
import io.realm.kotlin.entities.sync.SyncObjectWithAllTypes
import io.realm.kotlin.entities.sync.SyncPerson
import io.realm.kotlin.entities.sync.SyncRestaurant
import io.realm.kotlin.entities.sync.flx.FlexChildObject
import io.realm.kotlin.entities.sync.flx.FlexEmbeddedObject
import io.realm.kotlin.entities.sync.flx.FlexParentObject

private val ASYMMETRIC_SCHEMAS = setOf(
    AsymmetricA::class,
    EmbeddedB::class,
    StandardC::class,
    Measurement::class,
)
private val DEFAULT_SCHEMAS = setOf(
    BackupDevice::class,
    BinaryObject::class,
    ChildPk::class,
    Device::class,
    DeviceParent::class,
    FlexChildObject::class,
    FlexEmbeddedObject::class,
    FlexParentObject::class,
    ObjectIdPk::class,
    ParentPk::class,
    SyncObjectWithAllTypes::class,
    SyncPerson::class,
    SyncRestaurant::class,
    Location::class,
)

val PARTITION_BASED_SCHEMA = DEFAULT_SCHEMAS
// Amount of schema classes that should be created on the server. EmbeddedRealmObjects are not
// included in this count
val FLEXIBLE_SYNC_SCHEMA_COUNT = 11
val FLEXIBLE_SYNC_SCHEMA = DEFAULT_SCHEMAS + ASYMMETRIC_SCHEMAS
