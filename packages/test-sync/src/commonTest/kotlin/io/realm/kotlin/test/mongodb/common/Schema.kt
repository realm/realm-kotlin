package io.realm.kotlin.test.mongodb.common

import io.realm.kotlin.entities.sync.ChildPk
import io.realm.kotlin.entities.sync.ParentPk
import io.realm.kotlin.entities.sync.SyncObjectWithAllTypes
import io.realm.kotlin.entities.sync.SyncPerson
import io.realm.kotlin.entities.sync.flx.FlexChildObject
import io.realm.kotlin.entities.sync.flx.FlexEmbeddedObject
import io.realm.kotlin.entities.sync.flx.FlexParentObject

val SYNC_SCHEMA = setOf(
    AsymmetricSyncTests.AsymmetricA::class,
    AsymmetricSyncTests.EmbeddedB::class,
    AsymmetricSyncTests.StandardC::class,
    BackupDevice::class,
    ChildPk::class,
    Device::class,
    DeviceParent::class,
    FlexChildObject::class,
    FlexEmbeddedObject::class,
    FlexParentObject::class,
    Measurement::class,
    ParentPk::class,
    SyncObjectWithAllTypes::class,
    SyncPerson::class
)
