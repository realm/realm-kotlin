package io.realm.kotlin.mongodb.exceptions

import io.realm.kotlin.exceptions.RealmException
import io.realm.kotlin.mongodb.sync.SyncConfiguration

/**
 * Thrown when opening a Realm and it didn't finish download server data in the allocated timeframe.
 *
 * This can only happen if [SyncConfiguration.Builder.waitForInitialRemoteData] is set.
 */
public class DownloadingRealmTimeOutException : RealmException {
    internal constructor(syncConfig: SyncConfiguration) : super(
        "Realm did not managed to download all initial data in time: ${syncConfig.path}, " +
            "timeout: ${syncConfig.initialRemoteData?.timeout ?: "<missing>"}."
    )
}
