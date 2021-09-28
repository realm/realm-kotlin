package io.realm.mongodb.internal

import io.realm.RealmConfiguration
import io.realm.internal.InternalRealmConfiguration
import io.realm.internal.RealmConfigurationImpl
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.sync.PartitionValue
import io.realm.mongodb.SyncConfiguration

internal class SyncConfigurationImpl(
    localConfiguration: RealmConfigurationImpl,
    override val partitionValue: PartitionValue,
    override val user: UserImpl,
) : RealmConfiguration by localConfiguration,
    InternalRealmConfiguration by localConfiguration,
    SyncConfiguration {

    private val nativeSyncConfig: NativePointer =
        RealmInterop.realm_sync_config_new(user.nativePointer, partitionValue.asSyncPartition())

    init {
        RealmInterop.realm_config_set_sync_config(localConfiguration.nativeConfig, nativeSyncConfig)
    }
}
