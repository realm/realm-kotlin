package io.realm.mongodb.exceptions

import io.realm.exceptions.RealmException
import io.realm.mongodb.sync.SyncConfiguration

/**
 * Exception class used when a Realm was interrupted while downloading the initial data set.
 * This can only happen if [SyncConfiguration.Builder.waitForInitialRemoteData] is set.
 */
public class DownloadingRealmTimeOutException : RealmException {
    internal constructor(syncConfig: SyncConfiguration) : super(
        "Realm did not managed to download all initial data in time: ${syncConfig.path}, " +
            "timeout: ${syncConfig.initialRemoteDataTimeout}."
    )
}
