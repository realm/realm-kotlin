/*
 * Copyright 2023 Realm Inc.
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

import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.selects.whileSelect

/**
 * Flow wrapper that will terminate the flow once the [completionFlow] emits an element that
 * satisfies the [completionPredicate].
 */
internal fun <T, S> Flow<T>.terminateWhen(
    completionFlow: Flow<S>,
    completionPredicate: (S) -> Boolean
): Flow<T> =
    channelFlow<T> {
        val completionChannel = completionFlow.produceIn(this@channelFlow)
        val elementChannel = produceIn(this@channelFlow)
        whileSelect {
            completionChannel.onReceiveCatching {
                if (it.isClosed || completionPredicate(it.getOrThrow())) {
                    this@channelFlow.close()
                    it.exceptionOrNull()
                        ?.let { this@channelFlow.cancel("Couldn't retrieve element", it) }
                    false
                } else {
                    true
                }
            }
            elementChannel.onReceiveCatching {
                if (it.isClosed) {
                    this@channelFlow.close()
                    it.exceptionOrNull()
                        ?.let { this@channelFlow.cancel("Couldn't retrieve element", it) }
                    false
                } else {
                    this@channelFlow.trySendWithBufferOverflowCheck(it.getOrThrow())?.let {
                        cancel(it)
                    }
                    true
                }
            }
        }
    }
