package io.realm.mongodb.sync

import io.realm.internal.platform.singleThreadDispatcher
import kotlinx.coroutines.CoroutineDispatcher

actual fun getDispatcher(): CoroutineDispatcher = singleThreadDispatcher("Sync Dispatcher")
