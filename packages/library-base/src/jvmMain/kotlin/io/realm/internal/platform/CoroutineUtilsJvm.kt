package io.realm.internal.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

public actual fun singleThreadDispatcher(id: String): CoroutineDispatcher {
    // TODO Propagate id to the underlying thread
    return Executors.newSingleThreadExecutor().asCoroutineDispatcher()
}
