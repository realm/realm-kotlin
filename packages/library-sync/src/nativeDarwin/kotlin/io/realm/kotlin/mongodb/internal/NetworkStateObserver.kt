package io.realm.kotlin.mongodb.internal

internal actual fun registerSystemNetworkObserver() {
    // This is handled automatically by Realm Core which will also call `Sync.reconnect()`
    // automatically. So on iOS/macOS we do not do anything.
    // See https://github.com/realm/realm-core/blob/a678c36a85cf299f745f68f8b5ceff364d714181/src/realm/object-store/sync/impl/sync_client.hpp#L82C3-L82C3
    // for further details.
}
