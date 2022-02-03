package io.realm.internal.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.newSingleThreadContext
import kotlin.coroutines.CoroutineContext

// Expose platform runBlocking through common interface
public actual fun <T> runBlocking(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T
): T {
    return kotlinx.coroutines.runBlocking(context, block)
}

/**
 * The default dispatcher for Darwin platforms spawns a new thread with a run loop.
 */
actual fun singleThreadDispatcher(id: String): CoroutineDispatcher {
    // TODO Find a way to report incompatible coroutine library and memory model here:
    //  Supported scenarios are:
    //  - Coroutine 1.6.0-native-mt:
    //    - base/sync works with both (old/new) memory models
    //  - Coroutine 1.6.0 and new memory model
    //    - base works without `kotlin.native.binary.freezing=disabled` from this PR
    //    - sync work but only with `kotlin.native.binary.freezing=disabled` as this is required by
    //      ktor
    //  Not supported:
    //  - Coroutine 1.6.0 and old memory model
    //
    //  Could maybe reuse ktor check from with our custom freezeOnOldMM :thinking:
    //  https://github.com/ktorio/ktor/blob/1.6.5/ktor-client/ktor-client-core/posix/src/io/ktor/client/utils/CoroutineUtilsPosix.kt
    //  or just catch and rethrow the below 1.6.0 exception with an appropriate error message:
    //  io.realm.test.shared.notifications.SystemNotificationTests.multipleSchedulersOnSameThread FAILED
    //    kotlin.IllegalStateException: This API is only supported for experimental K/N memory model
    return newSingleThreadContext(id)
}

actual fun multiThreadDispatcher(size: Int): CoroutineDispatcher {
    // TODO https://github.com/realm/realm-kotlin/issues/501
    return singleThreadDispatcher("singleThreadDispatcher")
}
