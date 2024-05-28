/*
 * Copyright 2021 Realm Inc.
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

package io.realm.kotlin.internal.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.ProducerScope

// Conventional try-with-resource wrapper for channels
@Suppress("NOTHING_TO_INLINE")
public inline fun <T : Channel<*>, R> T.use(block: (channel: T) -> R): R {
    try {
        return block(this)
    } finally {
        this.close()
    }
}

// Public to be accessible from sync progress listeners
@Suppress("NOTHING_TO_INLINE")
public inline fun <T> ProducerScope<T>.trySendWithBufferOverflowCheck(value: T) {
    trySend(value).checkForBufferOverFlow()?.let { cancel(it) }
}

public inline fun <T> ChannelResult<T>.checkForBufferOverFlow(): CancellationException? =
    if (!isClosed && isFailure) {
        CancellationException(
            "Cannot deliver object notifications. Increase dispatcher processing resources or " +
                "buffer the flow with buffer(...)"
        )
    } else {
        null
    }
