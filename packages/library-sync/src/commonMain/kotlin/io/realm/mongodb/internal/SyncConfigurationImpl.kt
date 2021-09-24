package io.realm.mongodb.internal

import io.realm.InternalRealmConfiguration
import io.realm.RealmConfiguration
import io.realm.internal.RealmConfigurationImpl
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.sync.PartitionValue
import io.realm.mongodb.SyncConfiguration
import io.realm.mongodb.User

internal class SyncConfigurationImpl(
    localConfiguration: RealmConfigurationImpl,
    override val partitionValue: PartitionValue,
    userImpl: UserImpl,
) : RealmConfiguration by localConfiguration,
    InternalRealmConfiguration by localConfiguration,
    SyncConfiguration {

    override val user: User

    private val nativeSyncConfig: NativePointer =
        RealmInterop.realm_sync_config_new(userImpl.nativePointer, partitionValue.asSyncPartition())

    init {
        user = userImpl
        RealmInterop.realm_config_set_sync_config(localConfiguration.nativeConfig, nativeSyncConfig)
    }
}
